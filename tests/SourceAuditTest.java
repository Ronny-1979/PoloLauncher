package de.ronny.pololauncher;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;

/**
 * Static checks over the production sources. This is NOT an Android build (use compile-check.sh for
 * that), and since v0.30.4 it deliberately avoids asserting on cosmetic details:
 *  1. syntax of every production file,
 *  2. lock discipline for the shared vehicle state (AST based),
 *  3. a cross-reference audit: every "Foo.bar(...)" / "new Foo(...)" that names a project class must
 *     match a real method / constructor arity - this catches renamed or removed members in UI classes
 *     that the plain-Java tests never compile,
 *  4. structural guards for defects that were fixed once (whitespace-insensitive, so reformatting
 *     does not break them).
 */
public final class SourceAuditTest {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static final Path ROOT = Path.of("app/src/main/java/de/ronny/pololauncher");

    static String source(String name) throws Exception { return Files.readString(ROOT.resolve(name + ".java")); }
    /** Collapses all whitespace so that line breaks and indentation never matter. */
    static String norm(String text) { return text.replaceAll("\\s+", " "); }
    static boolean has(String source, String fragment) { return norm(source).contains(norm(fragment)); }
    static void requires(String source, String fragment, String message) { check(has(source, fragment), message + "  [missing: " + fragment + "]"); }
    static void forbids(String source, String fragment, String message) { check(!has(source, fragment), message + "  [found: " + fragment + "]"); }

    static final class ClassInfo {
        final Set<String> methods = new HashSet<>();
        final Set<Integer> constructorArities = new HashSet<>();
        boolean extendsSomething;
    }

    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<CompilationUnitTree> units = new ArrayList<>();
        int fileCount;
        try (var manager = compiler.getStandardFileManager(diagnostics, null, null); var paths = Files.walk(ROOT)) {
            List<Path> files = paths.filter(p -> p.toString().endsWith(".java")).sorted().toList();
            fileCount = files.size();
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("-proc:none"), null,
                    manager.getJavaFileObjectsFromPaths(files));
            for (CompilationUnitTree unit : task.parse()) units.add(unit);
        }
        check(diagnostics.getDiagnostics().stream().noneMatch(d -> d.getKind() == Diagnostic.Kind.ERROR),
                "Java syntax errors: " + diagnostics.getDiagnostics());
        System.out.println("SourceAuditTest: " + fileCount + " Java files parsed without syntax errors");

        // ---- 2. lock discipline: the shared state may only be touched under its lock
        for (CompilationUnitTree unit : units) {
            String file = unit.getSourceFile().getName();
            if (file.endsWith("VehicleRepository.java")) continue;
            new TreeScanner<Void, Void>() {
                int locks;
                @Override public Void visitSynchronized(SynchronizedTree tree, Void unused) {
                    boolean repository = tree.getExpression().toString().contains("VehicleRepository.class");
                    if (repository) locks++;
                    super.visitSynchronized(tree, unused);
                    if (repository) locks--;
                    return null;
                }
                @Override public Void visitMethodInvocation(MethodInvocationTree tree, Void unused) {
                    if (tree.getMethodSelect().toString().equals("VehicleRepository.mutable"))
                        check(locks > 0, "VehicleRepository.mutable() outside the repository lock in " + file);
                    return super.visitMethodInvocation(tree, unused);
                }
            }.scan(unit, null);
        }

        // ---- 3. cross-reference audit against the project's own classes
        Map<String, ClassInfo> classes = new HashMap<>();
        for (CompilationUnitTree unit : units) {
            for (Tree type : unit.getTypeDecls()) {
                if (!(type instanceof ClassTree clazz)) continue;
                ClassInfo info = new ClassInfo();
                info.extendsSomething = clazz.getExtendsClause() != null;
                boolean explicit = false;
                for (Tree member : clazz.getMembers()) {
                    if (!(member instanceof MethodTree method)) continue;
                    if (method.getName().contentEquals("<init>")) {
                        explicit = true;
                        info.constructorArities.add(method.getParameters().size());
                    } else info.methods.add(method.getName().toString());
                }
                if (!explicit) info.constructorArities.add(0);
                classes.put(clazz.getSimpleName().toString(), info);
            }
        }
        int[] audited = {0, 0};
        for (CompilationUnitTree unit : units) {
            String file = Path.of(unit.getSourceFile().getName()).getFileName().toString();
            new TreeScanner<Void, Void>() {
                @Override public Void visitMethodInvocation(MethodInvocationTree tree, Void unused) {
                    if (tree.getMethodSelect() instanceof MemberSelectTree select
                            && select.getExpression() instanceof IdentifierTree owner) {
                        ClassInfo info = classes.get(owner.getName().toString());
                        if (info != null && !info.extendsSomething) {
                            audited[0]++;
                            check(info.methods.contains(select.getIdentifier().toString()),
                                    file + " calls " + owner.getName() + "." + select.getIdentifier() + "(...), which does not exist");
                        }
                    }
                    return super.visitMethodInvocation(tree, unused);
                }
                @Override public Void visitNewClass(NewClassTree tree, Void unused) {
                    Tree type = tree.getIdentifier();
                    if (type instanceof ParameterizedTypeTree generic) type = generic.getType();
                    if (type instanceof IdentifierTree name) {
                        ClassInfo info = classes.get(name.getName().toString());
                        if (info != null) {
                            audited[1]++;
                            check(info.constructorArities.contains(tree.getArguments().size()),
                                    file + " calls new " + name.getName() + " with " + tree.getArguments().size()
                                            + " argument(s); constructors take " + info.constructorArities);
                        }
                    }
                    return super.visitNewClass(tree, unused);
                }
            }.scan(unit, null);
        }
        check(audited[0] > 100 && audited[1] > 15, "cross-reference audit looked at too little code");
        System.out.println("SourceAuditTest: cross-reference audit checked " + audited[0] + " static calls and " + audited[1] + " constructor calls");

        // ---- 4. structural guards
        String main = source("MainActivity");
        String settings = source("SettingsActivity");
        for (String category : new String[]{"Karte & GPS", "Tank, Verbrauch & OBD", "Darstellung & Medien", "System & Diagnose"})
            requires(settings, "addCategory(root, \"" + category + "\"", "Settings category " + category);
        for (String target : new String[]{"MapSettingsActivity", "RangeSettingsActivity",
                "Obd2SettingsActivity", "DiagnosticsActivity"})
            requires(settings, target + ".class", "Settings destination stays reachable: " + target);
        requires(source("MapSettingsActivity"), "OfflineMapsActivity.class",
                "Offline map management stays reachable from map settings");
        requires(settings, "navigateBack();", "Settings back navigation");
        requires(settings, "state.putString(\"settings_page\", page)", "Settings page restored after recreation");
        requires(settings, ".getBoolean(\"dab_start_and_return\", false)", "DAB startup is opt-in");
        // DAB autostart
        requires(main, "dabStartupGate.claim(enabled, internetReady)", "DAB launch gate");
        requires(main, "dabReturnGate.update(", "DAB return waits for foreground and playing station");
        requires(main, "handler.removeCallbacks(startDabAutomatically)", "Pending DAB start is cancelled with the lifecycle");
        String internetCheck = main.substring(main.indexOf("private boolean dabInternetAvailable()"), main.indexOf("private void returnAfterDabStartup()"));
        requires(internetCheck, "NET_CAPABILITY_VALIDATED", "DAB waits for validated Internet");
        forbids(internetCheck, "TRANSPORT_CELLULAR", "A SIM connection is not working Internet");
        // range / consumption plumbing
        requires(main, "Obd2Runtime.rangeAverage(this)", "Dashboard average is the gliding last-100-km window");
        forbids(main, "lastKnownAvgConsumption", "No stale RAM average on the dashboard");
        forbids(source("RangeStore"), "displayAverage", "Range uses the explicit reliable rolling baseline");
        requires(source("RangeStore"), "Obd2Runtime.rangeAverage(context)", "Combined range restores the OBD baseline");
        requires(source("Obd2ConsumptionAverager"), "WINDOW_KM = 100d", "OBD average has an explicit 100-km window");
        requires(source("Obd2ConsumptionAverager"), "trimToWindow()", "OBD average continuously trims its oldest distance");
        requires(source("Obd2Client"), "avg_rolling_100km_v1", "Rolling OBD window is persisted");
        requires(source("Obd2Client"), "averager.reliableValue()", "Live range obeys the one-kilometre maturity threshold");
        forbids(source("RangeStore"), "RangeDisplayStore", "Cached display values never enter calculation");
        forbids(main, "new RangeStore", "Dashboard does not write range state");
        requires(main, "RangeTrackingService.ensureStarted(this)", "Dashboard starts the service");
        requires(main, "RangeTrackingService.remainingKm(MainActivity.this)", "Dashboard reads the service result");
        requires(source("RangeTrackingSession"), "RangeDisplayStore.record(context, state.fuelLiters, remaining)", "Display cache fed by the session");
        check(!Files.exists(ROOT.resolve("ConsumptionLearner.java")), "Retired CAN consumption learner is removed");
        check(!Files.exists(ROOT.resolve("Obd2Calibration.java")), "Retired CAN-derived OBD calibration is removed");
        requires(source("VendorRuntime"), "Obd2Runtime.acquire(app)", "OBD shares the persistent runtime");
        // service
        String service = source("RangeTrackingService");
        requires(service, "startForeground(NOTIFICATION_ID, notification)", "Service is promoted to foreground");
        requires(service, "catch (RuntimeException notAllowed)", "startForeground refusal (Android 12+) is survivable");
        requires(service, "return START_STICKY", "Service restartable");
        requires(service, "session.update(VehicleRepository.snapshot())", "Service samples fresh CAN data");
        requires(service, "handler.removeCallbacks(sample)", "Timer cleaned up on repeated starts and shutdown");
        String manifest = Files.readString(Path.of("app/src/main/AndroidManifest.xml"));
        for (String needle : new String[]{"android.permission.FOREGROUND_SERVICE", "android.permission.POST_NOTIFICATIONS",
                ".RangeTrackingService", "android:stopWithTask=\"false\""})
            check(manifest.contains(needle), "Manifest declares " + needle);
        check(manifest.contains("android:name=\".PoloApplication\""),
                "Application installs persistent crash reporting");
        check(!manifest.contains(".SkinSettingsActivity"), "Retired skin activity is absent from manifest");
        String crashReporter = source("CrashReporter");
        requires(crashReporter, "MAX_STACK_CHARS", "Crash report size stays bounded");
        requires(crashReporter, "setDefaultUncaughtExceptionHandler", "Uncaught Java crashes are captured");
        String diagnosticsSource = source("DiagnosticsActivity");
        requires(diagnosticsSource, "Intent.ACTION_CREATE_DOCUMENT", "Diagnosis can be exported without storage permission");
        requires(diagnosticsSource, "CrashReporter.fullReport(app)", "Diagnosis export includes the last crash off the UI thread");
        requires(source("RangeStore"), "rate, reserve(context), now", "Adaptive rate feeds the range estimator");
        String rangeMenu = source("RangeSettingsActivity");
        forbids(rangeMenu, "CAN-Kontroll", "Retired CAN control model is absent from settings");
        forbids(rangeMenu, "CAN-Lern", "Retired CAN learner text is absent from settings");
        forbids(rangeMenu, "statt nur CAN", "Obsolete CAN-only selector is removed from settings");
        forbids(rangeMenu, "setIncludeObd2InRange", "Range menu cannot restore CAN-only mode");
        String rangeStore = source("RangeStore");
        forbids(rangeStore, "static boolean includeObd2InRange", "CAN-only mode API is removed");
        forbids(rangeStore, "static void setIncludeObd2InRange", "CAN-only mode setter is removed");
        requires(rangeStore, "prefs.edit().remove(\"include_obd2_in_range\")",
                "Legacy CAN-only preference is migrated away");
        requires(rangeStore, "double rate = combinedConsumption(context, state)",
                "Range estimator always receives combined CAN plus OBD model");
        requires(rangeMenu, "Berechnung: CAN + OBD2", "Settings report the fixed combined model");
        String compactSettings = source("SettingsActivity");
        requires(compactSettings, "Tank, Verbrauch & OBD", "Tank and OBD settings are grouped once");
        forbids(compactSettings, "addCategory(root, \"OBD-Verbindung\"", "Duplicate OBD category is removed");
        forbids(compactSettings, "new Intent(this, OfflineMapsActivity.class)",
                "Offline map management is linked only from map settings");
        String obdMenu = source("Obd2SettingsActivity");
        forbids(obdMenu, "Modellkorrektur", "Retired CAN-derived model correction is absent from OBD settings");
        forbids(obdMenu, "CAN-Lern", "Retired CAN learner is absent from OBD settings");
        requires(source("DiagnosticsActivity"), "Obd2Runtime.diagnostics(this)", "Detailed OBD diagnostics wired");
        requires(source("VendorRuntime"), "Obd2Runtime.release(app)", "OBD runtime lifetime is balanced");
        requires(main, "updateConsumption(s)", "Consumption tile wired");
        requires(main, "weatherLoader.execute", "Weather network access stays off the UI thread");
        requires(main, "WeatherStore.load(this)", "Weather cache restored on dashboard creation");
        String weatherClient = source("WeatherClient");
        requires(weatherClient, "https://api.open-meteo.com/v1/forecast", "Weather endpoint uses HTTPS");
        requires(weatherClient, "setConnectTimeout(8_000)", "Weather connection is bounded");
        requires(weatherClient, "setReadTimeout(8_000)", "Weather response wait is bounded");
        requires(weatherClient, "HttpText.readLimited", "Weather response size is bounded");
        requires(source("HttpText"), "MAX_RESPONSE_BYTES", "Shared response cap exists");
        requires(weatherClient, "BuildConfig.VERSION_NAME", "Weather User-Agent follows the installed app version");
        requires(main, "WEATHER_REFRESH_MS = 3L * 60L * 1_000L", "Weather refreshes every three minutes");
        requires(main, "LOCALITY_LOOKUP_DISTANCE_METERS", "Place lookup is distance gated");
        requires(main, "LOCALITY_LOOKUP_MIN_INTERVAL_MS", "Place lookup is rate limited");
        requires(main, "weatherRefreshForNewLocality = true", "A new place forces fresh weather");
        requires(main, "setScaleType(ImageView.ScaleType.FIT_CENTER)", "Weather icons preserve their complete outline");
        requires(main, "setCropToPadding(false)", "Weather icons cannot be clipped by ImageView padding");
        requires(main, "v.setImageTintList(null)", "Weather vector colours are not replaced by the selected skin");
        forbids(main, "weatherCurrentIcon.setImageTintList", "Current weather keeps its natural multi-colour icon");
        forbids(main, "weatherDayIcon[i].setImageTintList", "Forecast weather keeps its natural multi-colour icon");
        requires(Files.readString(Path.of("app/src/main/res/drawable/ic_weather_sun.xml")), "#FFFFC83D",
                "Sun icon has a fixed natural yellow");
        requires(Files.readString(Path.of("app/src/main/res/drawable/ic_weather_rain.xml")), "#FF4FA3F7",
                "Rain icon has fixed blue precipitation");
        requires(main, "weatherStatusNote = \"OFFLINE\"", "Cached weather explicitly marks offline mode");
        requires(main, "weatherStatusNote = \"FEHLER\"", "Cached weather explicitly marks refresh failures");
        requires(main, "private final Runnable refreshNetworkState", "Network work uses a slower dedicated timer");
        String dashboardTick = main.substring(main.indexOf("private final Runnable tick"), main.indexOf("private TextView dabButton"));
        forbids(dashboardTick, "updateMapConnection()", "500 ms dashboard timer performs no connectivity query");
        requires(main, "setMetricState(speed", "Stale vehicle values are visibly marked");
        requires(source("VehicleRepository"), "engineStateFresh && copy.engineStoppedByCan",
                "Only a fresh confirmed engine-off state keeps RPM at zero");
        requires(source("VehicleRepository"), "ENGINE_STATE_TTL_MS = 5_000L",
                "Engine state expires instead of pinning RPM forever");
        requires(source("HctSyncDecoder"), "oilWarning == chargeWarning",
                "Engine state is only inferred from the confirmed paired Mode3 warning state");
        forbids(source("SystemCanReceiver"), "\"doorrR\"",
                "Lower-cased broadcast heuristic contains no unreachable mixed-case rear-right key");
        requires(source("GlassBackgroundView"), "ImageSampling.forCenterCrop",
                "Full wallpaper decode is memory bounded");
        requires(source("GlassBlurCache"), "ImageSampling.forCenterCrop",
                "Blur-source decode is memory bounded");
        forbids(settings, "FLAG_GRANT_PERSISTABLE_URI_PERMISSION",
                "Background picker does not request a permission it no longer persists");
        requires(manifest, "android.permission.READ_BASIC_PHONE_STATE",
                "Android 13 network type has the basic phone-state permission");
        requires(manifest, "android.permission.PACKAGE_USAGE_STATS",
                "Optional CarPlay foreground detection declares Usage Access");
        requires(source("RangeTrackingService"), "checkCarPlayAutoReturn()",
                "Background service watches automatic CarPlay foregrounding");
        requires(source("RangeTrackingService"), "standbyWakeDetector.heartbeat",
                "Range service detects quick-start freezes even without screen broadcasts");
        requires(source("RangeTrackingService"), "ACTION_DAB_QUICKSTART_WAKE",
                "Detected quick-start wake notifies the visible launcher");
        requires(main, "ACTION_DAB_QUICKSTART_WAKE",
                "Main page rearms DAB after proprietary quick-start wake");
        requires(source("AppLauncher"), "CarPlayAutoReturn.markManualLaunch",
                "Intentional CarPlay launches suppress automatic return");
        requires(source("SettingsActivity"), "Nach automatischer CarPlay-Verbindung zum Launcher zurück",
                "CarPlay auto-return has an explicit user switch");
        requires(source("GlassBlurCache"), "if (cached != null && !cached.isRecycled()",
                "Glass draw path returns the cached blur before resolving the wallpaper file");
        String blurGet = source("GlassBlurCache").substring(
                source("GlassBlurCache").indexOf("static synchronized Bitmap get"),
                source("GlassBlurCache").indexOf("static synchronized void invalidate"));
        check(blurGet.indexOf("return cached") < blurGet.indexOf("BackgroundPreferences.customFile"),
                "Cached glass draw performs no wallpaper preference/filesystem lookup");
        String localityFetch = main.substring(main.indexOf("private void maybeRefreshLocality()"),
                main.indexOf("private void applyLocalityResult"));
        forbids(localityFetch, "LocalityStore.save",
                "Unconfirmed Nominatim result is not persisted before validation");
        requires(main.substring(main.indexOf("private void applyLocalityResult"),
                        main.indexOf("private Location currentWeatherLocation")),
                "LocalityStore.save(getApplicationContext(), loaded)",
                "Only accepted/confirmed locality results are persisted");
        String localityClient = source("LocalityClient");
        requires(localityClient, "https://nominatim.openstreetmap.org/reverse", "Locality endpoint uses HTTPS");
        requires(localityClient, "setConnectTimeout(8_000)", "Locality connection is bounded");
        requires(localityClient, "setReadTimeout(8_000)", "Locality response wait is bounded");
        requires(localityClient, "HttpText.readLimited", "Locality response size is bounded");
        requires(localityClient, "setRequestProperty(\"User-Agent\", USER_AGENT)", "Nominatim receives an identifying User-Agent");
        requires(localityClient, "BuildConfig.VERSION_NAME", "Locality User-Agent follows the installed app version");
        check(!Files.exists(ROOT.resolve("CopperSkin.java")), "Retired multi-skin engine is removed");
        check(!Files.exists(ROOT.resolve("SkinSettingsActivity.java")), "Retired skin chooser is removed");
        check(!Files.exists(ROOT.resolve("CarPlayIconView.java")), "Retired homemade CarPlay icon is removed");
        forbids(main, "cockpit_appearance", "Dashboard has no hidden legacy skin preference");
        forbids(settings, "CopperSkin", "Settings have no hidden legacy skin state");
        for (String dead : new String[]{"buildShortcutGlassCard", "metricHeading", "addMetricColumn",
                "addTankMetric", "addConsumptionMetric", "addDrivingMetric", "addEnvironmentMetric",
                "addTwoColumnMetric", "addGlassShortcut", "addDockButton", "columnPanel", "capitalize"})
            forbids(main, dead + "(", "Retired dashboard helper removed: " + dead);
        check(!Files.exists(ROOT.resolve("VwPq25Decoder.java")) && !Files.exists(ROOT.resolve("SimpleSoftDecoder.java")),
                "Unused/unconfirmed decoders stay removed");
        check(!Files.exists(ROOT.resolve("GpsLeadPredictor.java")) && !Files.exists(ROOT.resolve("GpsLeadPolicy.java")),
                "Old camera forward-projection experiment stays removed; settlement look-ahead is isolated separately");
        String mapSettings = source("MapSettingsActivity");
        forbids(mapSettings, "Punkt auf Straße einrasten", "Road snapping is permanent: no menu switch");
        forbids(mapSettings, "setRoadSnap", "Road snapping is permanent: no preference setter");
        forbids(source("MapPreferences"), "roadSnap", "Road snapping is permanent: no preference accessor left");
        requires(source("GermanyRoadPositionMatcher"), "GermanyRoadTiles.open(context)",
                "Road matcher reads every road from the offline map tiles");
        requires(source("GermanyRoadPositionMatcher"), "traffic.findRoadCandidates(",
                "Traffic database stays the fallback when no tile is available");
        forbids(source("GermanyRoadPositionMatcher"), "queryRenderedFeatures",
                "Road lines come from the tiles on disk, never from what the screen has rendered");
        requires(source("GermanyRoadPositionMatcher"), "worker.execute(this::closeResources)",
                "Matcher closes its databases on the worker, never blocking the UI thread");
        forbids(source("GermanyRoadPositionMatcher"), "worker.shutdownNow()",
                "Matcher must not interrupt a running database read");
        forbids(source("GermanyRoadTiles"), "synchronized String activeRegion",
                "UI thread reads the region name without waiting for tile I/O");
        requires(source("PoloApplication"), "UiStallWatchdog.start(this)",
                "UI stalls are recorded with the main thread's stack");
        forbids(source("GermanyOfflinePanel"), "queryRenderedFeatures", "No screen-dependent road query");
        forbids(mapSettings, "Direkt an Ortsgrenze umschalten",
                "Direct settlement-boundary zoom is permanent: no switch in the map settings any more");
        forbids(mapSettings, "setAutoZoom", "Location-dependent zoom is permanent: no switch any more");
        forbids(source("MapPreferences"), "autoZoom(", "Location-dependent zoom is permanent");
        requires(source("VehicleRepository"), "RPM_FROZEN_MS", "A frozen rpm value after engine-off is shown as 0");
        forbids(source("MapPreferences"), "direct_boundary_zoom",
                "Direct settlement-boundary zoom is permanent and no longer a stored setting");
        requires(source("SettlementZoomGate"), "EXIT_DISTANCE_M = 60d",
                "Exit from the settlement zoom needs meaningful travelled distance");
        requires(source("SettlementZoomGate"), "EXIT_CONFIRMATIONS = 2",
                "Exit from the settlement zoom needs repeated clear outside observations");
        requires(main, "String.format(Locale.GERMANY, \"%.0f L\", shownTank)",
                "Last known tank value remains readable without an ALT suffix");
        forbids(main, "%.0f L ALT", "Tank age is conveyed by orange colour, not extra text");
        // maps / GPS
        requires(main, "germanyMap.restorePageGesture()", "Germany offline map wired to page swipe recovery");
        requires(main, "GermanyOfflineStore.warmUpAsync(this", "Full Germany validation runs in the background, not at startup");
        requires(main, "GermanyOfflineStore.markedReady(this)", "Startup reads only the cheap READY flag");
        requires(main, "fallbackGps.setRawConsumer(germanyMap::acceptAnalysisLocation)",
                "Germany raw GPS remains authoritative for traffic and settlement analysis");
        requires(main, "fallbackGps.setConsumer(this::acceptGermanyCameraPosition)",
                "Filtered camera GPS uses the road-snap-safe visible-renderer route");
        requires(source("MainActivity"), "new SpeedLimitSignView(this)",
                "Map shows the speed-limit traffic sign overlay");
        requires(source("MapPreferences"), "getBoolean(SPEED_CAMERA_ALERTS, false)",
                "Speed-camera feature is disabled by default");
        requires(source("MapSettingsActivity"), "Feste Blitzer aus Offline-Karte anzeigen",
                "Speed-camera feature has an explicit settings switch");
        requires(source("SpeedCameraDecision"), "roadAxisDifference",
                "Camera road matching is independent of arbitrary OSM way node direction");
        requires(source("DiagnosticsActivity"), "SpeedLimitDiagnostics.summary()",
                "Diagnosis shows the current offline speed-limit lookup reason");
        requires(source("TrafficPbfScanner"), "new PbfReader(pbf, 1)",
                "Traffic data is built locally from official Geofabrik OSM PBF");
        requires(source("TrafficPbfScanner"), "Pass 1", "PBF conversion deliberately uses bounded two-pass node retention");
        requires(source("TrafficDataStore"), "CREATE VIRTUAL TABLE road_index USING rtree",
                "Compact traffic DB has a spatial road index");
        requires(source("TrafficDataStore"), "resolved == SpeedLimitValue.UNKNOWN",
                "Unresolved traffic-road candidates cannot hide a nearby usable speed limit");
        requires(source("GermanyOfflineService"), "startForeground",
                "Large PBF conversion survives leaving the map settings screen");
        requires(source("GermanyOfflineService"), "pbf.delete()",
                "Large Geofabrik raw file is removed after conversion");
        requires(source("GermanyOfflineUpdateChecker"), "setRequestMethod(\"HEAD\")",
                "Germany update checks use lightweight HTTP HEAD requests");
        requires(source("GermanyOfflineUpdateChecker"), "Karten: ",
                "Germany update check reports map-region update count");
        requires(source("GermanyOfflineUpdateChecker"), "Verkehr: ",
                "Germany update check reports traffic-region update count");
        requires(source("OfflineMapsActivity"), "OFFLINE-KARTE DEUTSCHLAND",
                "Offline map screen exposes one Germany map instead of a region list");
        requires(source("OfflineMapsActivity"), "Tempolimits · auch im Stand erkennbar",
                "Germany offline screen describes the integrated speed-limit data");
        requires(source("OfflineMapsActivity"), "GermanyOfflineStore.start(this)",
                "Germany offline screen starts the unified package builder");
        requires(source("GermanyOfflineStore"), "removeLegacyMaps",
                "Legacy regional maps are removed only by the Germany migration");
        requires(source("GermanyOfflineService"), "if (!GermanyOfflineStore.mapsReady(this))",
                "Germany migration validates every map before activation");
        requires(source("GermanyOfflineService"), "if (!GermanyOfflineStore.trafficReady(this))",
                "Germany migration validates all traffic data before activation");
        requires(source("GermanyOfflineStyle"), "mbtiles://",
                "Offline Germany renderer reads local MBTiles directly with MapLibre");
        requires(source("GermanyOfflineStyle"), "\"mbtiles://\" + file.getAbsolutePath()",
                "MBTiles source URL is mbtiles:// + absolute path (mbtiles:///data/...)");
        forbids(source("GermanyOfflineStyle"), "\"mbtiles://\" + Uri",
                "MBTiles source URL must not embed a file:// URI (mbtiles://file:///... cannot be opened)");
        requires(source("GermanyOfflineAnalyzer"), "worker.execute(toClose::close)",
                "Traffic DB is closed off the UI thread");
        requires(source("GermanyOfflinePanel"), "private void advance(String reason)",
                "Offline panel falls back to simpler style levels instead of hanging on a black map");
        requires(source("GermanyOfflinePanel"), "OfflineMapSelfTest.start(",
                "Offline panel writes a self-test of map files, tiles, sprite and glyphs into the diagnostics log");
        requires(source("GermanyOfflinePanel"), "if (visible && started) activeMs += 2_000L;",
                "Style attempts only count time while the map is visible and started");
        requires(source("GermanyOfflinePanel"), "new Style.Builder().fromJson(json)",
                "Offline style JSON must be applied through Style.Builder.fromJson (a plain String is read as a URL)");
        forbids(source("GermanyOfflinePanel"), "map.setStyle(json,",
                "setStyle(String) treats its argument as a URL, never as style JSON");
        requires(source("GermanyOfflinePanel"), "ATT_HTTP",
                "Offline panel can fall back from direct mbtiles:// to the local tile server");
        requires(source("GermanyOfflinePanel"), "oldServer.close()",
                "Offline panel stops the local tile server on destroy");
        requires(Files.readString(java.nio.file.Paths.get("app/src/main/AndroidManifest.xml")), "network_security_config",
                "Cleartext HTTP is allowed for 127.0.0.1 only through a network security config");
        requires(Files.readString(java.nio.file.Paths.get("app/src/main/res/xml/network_security_config.xml")), "127.0.0.1",
                "Network security config names the loopback address");
        requires(source("GermanyOfflineStyle"), "offline_style/",
                "Offline style is composed from the generated VersaTiles Liberty-style layer templates");
        requires(source("GermanyOfflineStyle"), "GermanyRegionIndex.select(",
                "Offline style binds only the packages around the position, never all 14 at once");
        forbids(source("GermanyOfflineStyle"), "REGION_KEYS.length; i++",
                "Offline style no longer creates one source per package unconditionally");
        requires(source("GermanyOfflinePanel"), "GermanyRegionIndex.covered(",
                "Offline panel reloads the style when a new federal state comes into view");
        requires(source("GermanyOfflinePanel"), "setBackgroundColor(",
                "Offline map view has a paper-coloured background, never black");
        forbids(source("MainActivity"), "OnlineVectorMapPanel", "The online map has been removed");
        forbids(source("MapSettingsActivity"), "Nur Offline-Karte", "No online/offline switch any more");
        requires(source("MapSettingsActivity"), "3D-ANSICHT", "Map settings contain tilt and building height");
        requires(source("GermanyOfflinePanel"), "bearingSmoother.next(", "Camera heading is low-pass filtered");
        requires(source("GermanyOfflinePanel"), "styleNight = nightMode", "Night/day theme is chosen when the style is built");
        forbids(source("GermanyOfflinePanel"), "nightShade.setVisibility(View.VISIBLE)", "Night uses the dark theme, no translucent veil");
        requires(source("GermanyOfflinePanel"), "void applyViewSettings()", "Tilt/building height changes apply on return from the menu");
        requires(source("MainActivity"), "germanyMap.applyViewSettings()", "Main page applies changed 3D settings");
        requires(source("GermanyOfflineService"), "}, false);",
                "Germany traffic import keeps only speed-tagged roads plus cameras");
        requires(source("GermanyOfflineService"), "\"speed-camera\"",
                "Germany traffic DB is explicitly tagged as compact speed/camera profile");
        String germanyTraffic = source("GermanyTrafficReader");
        requires(germanyTraffic, "PRELOAD_METRES = 20_000d",
                "Nearby neighbour traffic regions are preloaded 20 km before a border hand-over");
        requires(germanyTraffic, "KEEP_METRES = 25_000d",
                "Regional reader uses hysteresis before closing the previous traffic region");
        requires(germanyTraffic, "RESCAN_AFTER_METRES = 5_000d",
                "Regional activation is rescanned after meaningful movement, not every GPS tick");
        requires(germanyTraffic, "hasTrafficNear(lat, lon, PRELOAD_METRES)",
                "Only extracts with actual nearby speed/camera data stay open");
        requires(germanyTraffic, "closeSlot(slot)",
                "Traffic readers outside the current region window are closed");
        forbids(germanyTraffic, "result.readers.add",
                "Germany traffic reader no longer opens all regional SQLite databases up front");
        requires(source("TrafficDataStore"), "SELECT 1 FROM road_index",
                "Regional activation uses a cheap indexed road coverage probe");
        requires(source("GermanyOfflineStore"), "confirmRendered",
                "Legacy map deletion is gated by a real Germany render confirmation");
        requires(source("GermanyOfflinePanel"), "addOnDidFinishRenderingMapListener",
                "Germany migration waits for an actual fully rendered MapLibre map");
        requires(source("GermanyOfflinePanel"), "private boolean cameraReady()",
                "Germany live camera has a readiness gate independent of migration confirmation");
        requires(source("GermanyOfflinePanel"), "map != null && styleApplied && !failed && !destroyed",
                "Germany live follow starts as soon as map plus local style are usable");
        forbids(source("GermanyOfflinePanel"), "if (!following || !ready()) return;",
                "Germany GPS follow must never wait for fully-rendered migration confirmation");
        requires(source("MainActivity"), "germanyMap.failed()",
                "Asynchronous Germany renderer failure activates the fallback path");
        requires(source("MainActivity"), "new SpeedCameraAlertView(this)",
                "Map contains a dedicated camera warning overlay");
        requires(source("SettingsActivity"), "settings-background-io",
                "Wallpaper file transactions run on a dedicated worker");
        check(source("SettingsActivity").split("void onBackPressed\\(\\)", -1).length - 1 == 1,
                "SettingsActivity defines onBackPressed exactly once");
        forbids(source("SettingsActivity"), "BackgroundPreferences.importFromUri(this, uri)",
                "Wallpaper import never performs large copy/fsync on the UI thread");
        requires(source("DiagnosticsActivity"), "diagnosis-export",
                "SAF diagnosis export runs on a dedicated worker");
        requires(source("SystemCanReceiver"), "MAX_TOP_LEVEL_EXTRAS = 64",
                "Exported vendor receiver bounds hostile top-level extras");
        requires(source("SystemCanReceiver"), "MAX_GENERIC_LOG_CHARS = 4_096",
                "Exported receiver bounds diagnostic string construction");
        requires(source("HctCanbusBinderRuntime"), "MAX_CALLBACK_FRAME_BYTES = 4_096",
                "Vendor Binder callback rejects oversized raw frames");
        requires(source("SystemCanReceiver"), "MAX_VENDOR_BYTE_ARRAY = 4_096",
                "Exported CAN receiver rejects oversized byte-array payloads before decoding");
        forbids(source("SerialCanFramer"), "feed(Arrays.copyOfRange",
                "Serial frame resynchronization is iterative, not recursive");
        requires(source("HctCanbusBinderRuntime"), "if (accepted) handleCallbackFrame(raw)",
                "Binder callback decoding runs outside the global HCT monitor");
        requires(source("GlassBlurCache"), "Drop only our reference",
                "Background cache invalidation never recycles a bitmap still held by a drawable");
        requires(source("OfflineMapsActivity"), "Auf Updates prüfen",
                "Ready Germany package is updated only after an explicit user check");
        requires(source("OfflineMapsActivity"), "GermanyOfflineStore.startUpdate",
                "Germany update starts only after the confirmation flow");
        requires(source("GermanyOfflineStore"), "startUpdate",
                "Initial/resume and ready-package update paths are separated");
        requires(source("GermanyOfflineUpdatePolicy"), "remoteModified > localModified",
                "Germany updater compares remote freshness without legacy Mapsforge baselines");
        requires(source("RangeTrackingService"), "carplay-foreground-check",
                "UsageStats and CarPlay package probing run off the service main thread");
        requires(source("DabSessionWatcher"), "catch (RuntimeException sessionRace)",
                "MediaSession disappearing during bind cannot crash the main thread");
        requires(source("MediaInfo"), "MAX_TEXT_CHARS = 512",
                "DAB metadata is bounded before persistence and display");
        requires(source("DabNotificationListener"), "value instanceof CharSequence",
                "Notification metadata ignores arbitrary object toString payloads");
        forbids(source("RangeTrackingService"), "String foreground = ForegroundAppDetector.currentPackage(this)",
                "Service main loop never performs UsageStats query synchronously");
        requires(main, "SolarDaylight.isNight", "Automatic map mode follows the GPS solar position");
        requires(main, "handler.removeCallbacks(refreshMapTheme)", "Automatic theme timer is lifecycle safe");
        requires(mapSettings, "MapPreferences.COLOR_AUTO", "Map settings expose automatic day/night mode");
        requires(mapSettings, "MapPreferences.COLOR_DAY", "Map settings expose permanent day mode");
        requires(mapSettings, "MapPreferences.COLOR_NIGHT", "Map settings expose permanent night mode");
        requires(mapSettings, "option.setId(android.view.View.generateViewId())",
                "Radio options have unique IDs so old selections are unchecked immediately");
        requires(mapSettings, "option.setTag(key)", "Radio options retain their persisted preference key");
        requires(mapSettings, "syncRadioSelection(colorModes, mode)", "Map colour mode selection refreshes immediately");
        requires(source("LocationAccess"), "ACCESS_FINE_LOCATION", "GPS provider requires the precise permission");
        requires(source("LocationAccess"), "lastKnownNetwork",
                "Approximate location retains a network-provider fallback");
        requires(source("FallbackLocationFeed"), "LocationAccess.lastKnownGps",
                "Fallback feed isolates precise GPS access from coarse network access");
        // map downloads
        String downloadScreen = source("OfflineMapsActivity");
        requires(downloadScreen, "GermanyOfflineStore.status(this)", "Germany UI reads one package status");
        requires(downloadScreen, "GermanyOfflineStore.cancel(this)", "Long Germany build can be cancelled");
        requires(downloadScreen, "GermanyOfflineStore.deleteAll(this)", "One delete action removes the complete offline package");
        requires(source("GermanyOfflineService"), "worker = Executors.newSingleThreadExecutor",
                "Multi-gigabyte Germany build runs away from the UI thread");
        requires(source("GermanyOfflineService"), "pbf.delete()",
                "Temporary regional PBF files are deleted after traffic extraction");
        String germanyService = source("GermanyOfflineService");
        requires(germanyService, "START_REDELIVER_INTENT",
                "Germany foreground build is redelivered after process/service loss");
        requires(germanyService, "GermanyOfflineStore.markRunRequested(this, updateExisting)",
                "Germany build request is persisted before long background work");
        requires(germanyService, "scheduleAtFixedRate",
                "Germany builder emits a heartbeat during long PBF passes");
        requires(germanyService, "onTaskRemoved(Intent rootIntent)",
                "Leaving the launcher task does not cancel the Germany builder");
        requires(source("GermanyOfflineStore"), "static void autoResumePending(Context c)",
                "Pending Germany builds can be restored without a button press");
        requires(source("PoloApplication"), "GermanyOfflineStore.autoResumePending(this)",
                "Process recreation retries a pending Germany build");
        requires(main, "GermanyOfflineStore.autoResumePending(this)",
                "Visible launcher is a recovery fallback for restrictive head-unit firmware");
        requires(source("GermanyOfflineResumeReceiver"), "Intent.ACTION_BOOT_COMPLETED",
                "Real Android boot resumes a pending Germany build");
        requires(manifest, "android.permission.RECEIVE_BOOT_COMPLETED",
                "Manifest permits Germany build recovery after boot");
        requires(manifest, ".GermanyOfflineResumeReceiver",
                "Manifest registers the Germany build recovery receiver");
        // DAB / media
        String watcher = source("DabSessionWatcher");
        requires(watcher, "MediaInfo.updateArtwork(artwork(m))", "Missing artwork clears the logo");
        requires(watcher, "registerCallback(callback, handler)", "DAB state is event driven");
        forbids(main, "getActiveSessions", "The UI thread makes no MediaSession Binder query per tick");
        requires(main, "dabWatcher.poll()", "Dashboard tick only retries watcher registration");
        requires(source("MediaInfo"), "WeakReference<Bitmap> artworkSource", "Large source artwork is not retained beside the scaled copy");
        requires(source("DabNotificationListener"), "LABEL_CHECKS", "Package label checks are cached");
        // CAN / vendor input
        String hctBinder = source("HctCanbusBinderRuntime");
        requires(hctBinder, "executeBinderTask(\"Verbindung initialisieren\"", "Binder setup stays off the main thread");
        requires(hctBinder, "executeBinderTask(\"Callback abmelden\"", "Binder teardown stays off the main thread");
        requires(hctBinder, "new ArrayBlockingQueue<>(8)", "Binder work queue is bounded");
        String receiver = source("SystemCanReceiver");
        check(receiver.indexOf("isNoisySyncMode2(action, extras)") < receiver.indexOf("new StringBuilder(\"BROADCAST \""),
                "Noisy Mode2 broadcasts are rejected before diagnostic text allocation");
        requires(receiver, "VENDOR_INSPECTOR.execute(() -> inspectVendorObject", "Vendor reflection runs on the bounded worker");
        requires(receiver, "catch (Throwable failure)", "onReceive cannot crash the process");
        forbids(receiver, "heuristicSniffer", "Unknown broadcast key-name guessing is removed");
        forbids(receiver, "applyNumeric(", "Unknown numeric broadcasts cannot guess vehicle fields");
        forbids(receiver, "applyBoolean(", "Unknown boolean broadcasts cannot guess vehicle fields");
        requires(receiver, "if (depth >= 4) return", "Bundle depth is bounded");
        requires(source("Hex"), "MAX_BYTES = 512", "Hex dumps are bounded");
        // OBD
        String obd = source("Obd2Client");
        requires(obd, "MAX_REPLY_CHARS = 16_384", "ELM replies are bounded");
        requires(obd, "catch (Throwable unexpected)", "An Error cannot kill the poll thread silently");
        requires(obd, "drain(input);", "Stale bytes are discarded before every command");
        requires(obd, "MAX_RECONNECT_DELAY_MS", "Reconnect attempts back off");
        requires(obd, "if (running) return averager.reliableValue()",
                "Live range average does not serialize/parse the 100-km window every tick");
        requires(obd, "new Thread(() -> closeSilently(closing), \"obd2-socket-close\")",
                "Potentially blocking Bluetooth socket close runs off the UI thread");
        forbids(obd, "private InputStream in", "Stream fields are shared between threads");
        // time base: freshness never depends on the wall clock
        for (String name : new String[]{"VehicleRepository", "HctSyncDecoder", "SystemCanReceiver", "ConsumptionDisplay", "MainActivity"})
            forbids(source(name), "System.currentTimeMillis()", name + " uses VehicleRepository.now(), not wall time");
        // UI hygiene
        for (Path path : Files.list(ROOT).filter(p -> p.toString().endsWith(".java")).toList()) {
            String name = path.getFileName().toString();
            if (name.equals("GlassSkin.java")) continue;
            forbids(Files.readString(path), "AlertDialog.Builder(", name + " builds dialogs through GlassSkin.dialog (dark theme)");
        }
        requires(source("GlassSkin"), "static android.app.AlertDialog.Builder dialog(Context c)",
                "Glass UI owns the dark dialog factory");
        requires(source("FrostedGlassDrawable"), "outline.setRoundRect(bounds, radius)",
                "Glass drawable exposes rounded outline");
        forbids(main, "info.loadIcon(pm)); } catch", "App icons are not loaded on the UI thread");
        requires(main, "iconLoader.execute(", "App icons load on a background thread");
        forbids(source("SettingsActivity"), "startActivity(new Intent(\"android.settings", "Settings screens start through the safe launcher");
        requires(source("VehiclePreferences"), "door_fl_fault", "Front-left door contact is a user setting");
        String backgrounds = source("BackgroundPreferences");
        requires(backgrounds, "target.renameTo(backup)", "Wallpaper import keeps the previous valid file as backup");
        requires(backgrounds, "backup.renameTo(target)", "Failed wallpaper activation restores the previous file");
        requires(backgrounds, "backup.renameTo(fallback)", "Interrupted wallpaper import recovers on next launch");
        // build / release hygiene
        String gradle = Files.readString(Path.of("app/build.gradle"));
        if ((weatherClient.contains("BuildConfig.VERSION_NAME")
                || localityClient.contains("BuildConfig.VERSION_NAME"))
                && !(gradle.contains("buildConfig true")
                || gradle.contains("buildConfig = true"))) {
            throw new AssertionError(
                    "BuildConfig.VERSION_NAME is used but app/build.gradle does not enable buildFeatures.buildConfig");
        }
        java.util.regex.Matcher version = java.util.regex.Pattern.compile("versionName '([^']+)'").matcher(gradle);
        check(version.find(), "versionName present");
        check(Files.readString(Path.of("README.md")).lines().findFirst().orElse("").contains(version.group(1)),
                "README title names the current version " + version.group(1));
        check(gradle.contains("abiFilters"), "Native libraries limited to the radio's ABIs");
        String wrapper = Files.readString(Path.of("gradle/wrapper/gradle-wrapper.properties"));
        check(wrapper.contains("distributionSha256Sum="), "Gradle distribution is checksum-verified");
        check(Files.size(Path.of("gradle/wrapper/gradle-wrapper.jar")) > 20_000, "Official Gradle wrapper jar (not a stub)");
        check(has(Files.readString(Path.of("BUILD_APK_WINDOWS.bat")), "if errorlevel 1 ("), "Windows build script reports failures");
        System.out.println("SourceAuditTest: symbol cross-reference, locking and retained-defect guards passed (not an Android build)");
    }
}
