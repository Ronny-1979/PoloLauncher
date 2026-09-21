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
        for (String category : new String[]{"Karte & GPS", "Tank & Verbrauch", "OBD-Verbindung", "Darstellung & Medien", "System & Diagnose"})
            requires(settings, "addCategory(root, \"" + category + "\"", "Settings category " + category);
        for (String target : new String[]{"MapSettingsActivity", "OfflineMapsActivity", "RangeSettingsActivity",
                "Obd2SettingsActivity", "SkinSettingsActivity", "DiagnosticsActivity"})
            requires(settings, target + ".class", "Settings destination stays reachable: " + target);
        requires(settings, "onBackPressed() { navigateBack(); }", "Settings back navigation");
        requires(settings, "state.putString(\"settings_page\", page)", "Settings page restored after recreation");
        requires(settings, ".getBoolean(\"dab_start_and_return\", false)", "DAB startup is opt-in");
        // DAB autostart
        requires(main, "dabStartupGate.claim(enabled, internetReady)", "DAB launch gate");
        requires(main, "handler.postDelayed(this::returnAfterDabStartup, 3000L)", "DAB delayed return");
        requires(main, "handler.removeCallbacks(startDabAutomatically)", "Pending DAB start is cancelled with the lifecycle");
        String internetCheck = main.substring(main.indexOf("private boolean dabInternetAvailable()"), main.indexOf("private void returnAfterDabStartup()"));
        requires(internetCheck, "NET_CAPABILITY_VALIDATED", "DAB waits for validated Internet");
        forbids(internetCheck, "TRANSPORT_CELLULAR", "A SIM connection is not working Internet");
        // range / consumption plumbing
        requires(main, "Obd2Runtime.reliableDisplayAverage(this)", "Dashboard average comes from the persisted, reset-aware source");
        forbids(main, "lastKnownAvgConsumption", "No stale RAM average on the dashboard");
        forbids(source("RangeStore"), "displayAverage", "Range uses the explicit cumulative baseline");
        requires(source("RangeStore"), "Obd2Runtime.rangeAverage(context)", "Combined range restores the OBD baseline");
        forbids(source("RangeStore"), "RangeDisplayStore", "Cached display values never enter learning");
        forbids(main, "new RangeStore", "Dashboard does not write range state");
        requires(main, "RangeTrackingService.ensureStarted(this)", "Dashboard starts the service");
        requires(main, "RangeTrackingService.remainingKm(MainActivity.this)", "Dashboard reads the service result");
        requires(source("RangeTrackingSession"), "RangeDisplayStore.record(context, state.fuelLiters, remaining)", "Display cache fed by the session");
        requires(source("RangeStore"), "learner.update(state.odometerKm, state.fuelLiters", "Learner uses raw CAN litres, not modelled contents");
        requires(source("RangeStore"), "calibrateObdModel(context)", "Finished learning windows calibrate the OBD model");
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
        requires(source("RangeStore"), "rate, reserve(context), now", "Adaptive rate feeds the range estimator");
        String rangeMenu = source("RangeSettingsActivity");
        requires(rangeMenu, "setAutomaticLearning(this, enabled)", "Learning toggle wired to settings");
        requires(rangeMenu, "resetLearning(this)", "Learning reset wired to settings");
        requires(rangeMenu, "setIncludeObd2InRange(this, enabled)", "CAN/OBD range selection wired");
        requires(source("DiagnosticsActivity"), "Obd2Runtime.diagnostics(this)", "Detailed OBD diagnostics wired");
        requires(source("VendorRuntime"), "Obd2Runtime.release(app)", "OBD runtime lifetime is balanced");
        requires(main, "updateConsumption(s)", "Consumption tile wired");
        check(!Files.exists(ROOT.resolve("VwPq25Decoder.java")) && !Files.exists(ROOT.resolve("SimpleSoftDecoder.java")),
                "Unused/unconfirmed decoders stay removed");
        check(!Files.exists(ROOT.resolve("GpsLeadPredictor.java")) && !Files.exists(ROOT.resolve("GpsLeadPolicy.java")),
                "Variable GPS forward projection stays removed");
        String mapSettings = source("MapSettingsActivity");
        check(mapSettings.substring(mapSettings.indexOf("protected void onResume")).contains("roadSnap.setEnabled(available)"),
                "Road correction availability refreshed on resume");
        // maps / GPS
        requires(main, "offlineMap.restorePageGesture()", "Offline map wired to page swipe recovery");
        requires(main, "vectorMap.restorePageGesture()", "Online map wired to page swipe recovery");
        requires(main, "fallbackGps.setConsumer(offlineMap::acceptExternalLocation)", "Only one GPS/render path is active");
        requires(source("OfflineMapPanel"), "catch (SecurityException ignored)", "GPS teardown tolerates revoked permission");
        String offline = source("OfflineMapPanel");
        String external = offline.substring(offline.indexOf("void acceptExternalLocation"), offline.indexOf("private void deliverCameraPosition"));
        forbids(external, "acceptLocation(", "Online GPS is not filtered twice");
        requires(external, "roadMatcher.accept(cameraFix)", "Road matching stays active while online");
        String recenter = offline.substring(offline.indexOf("private void showLastPosition()"), offline.indexOf("private static boolean recent"));
        check(recenter.indexOf("deliverCameraPosition(lastCameraLocation)") >= 0
                && recenter.indexOf("deliverCameraPosition(lastCameraLocation)") < recenter.indexOf("acceptLocation(location)"),
                "GPS recenter restores the accepted position without duplicate-fix filtering");
        requires(offline, "private final SettlementZoomAnalyzer.Listener settlementListener", "Weak map callbacks stay strongly held by the panel");
        requires(offline, "private final RoadPositionMatcher.Listener roadPositionListener", "Weak map callbacks stay strongly held by the panel");
        requires(source("RoadPositionMatcher"), "worker.shutdownNow()", "Road matcher worker is cancellable");
        requires(source("SettlementZoomAnalyzer"), "worker.shutdownNow()", "Settlement worker is cancellable");
        requires(source("OnlineVectorMapPanel"), "timeoutGate.arm()", "Load timeout gate connected");
        requires(source("OnlineVectorMapPanel"), "timeoutGate.clear()", "Load timeout gate cleared with the lifecycle");
        requires(source("OnlineVectorMapPanel"), "tiles.openfreemap.org/styles/", "Online style loading");
        forbids(source("OfflineMapPanel"), "Manifest.permission", "Location permission checks go through LocationAccess");
        requires(source("LocationAccess"), "ACCESS_FINE_LOCATION", "GPS provider requires the precise permission");
        // map downloads
        String store = source("OfflineMapStore");
        requires(store, "finishIfCompleteAsync", "Download validation off the UI thread");
        forbids(main, "OfflineMapStore.finishIfComplete(this)", "Main page never validates downloads itself");
        requires(store, "COMPLETIONS.join(id, owner", "All live observers join the work");
        requires(main, "handler.postDelayed(checkMapDownload, 5_000L)", "Visible launcher rechecks downloads");
        requires(main, "handler.removeCallbacks(checkMapDownload)", "Download recheck cleaned up with the lifecycle");
        requires(store, "if (!isActive(c, id, key)) return null", "Cancelled downloads cannot be activated");
        requires(store, "MapFileActivation.activatedWithWarning(activation)", "A map that is in place but left a backup still counts as installed");
        requires(store, "catch (Throwable error)", "Completion worker cannot leave a pending entry behind");
        String downloadScreen = source("OfflineMapsActivity");
        for (String call : new String[]{"progress", "installedBytes", "availableBytes", "installed", "selectedFile", "start", "cancelActive", "deleteInstalled", "select"})
            forbids(downloadScreen, "OfflineMapStore." + call + "(this", "Download page does no blocking storage work on the UI thread: " + call);
        requires(downloadScreen, "OfflineMapStore.loadUiStateAsync(this", "Download UI uses asynchronous snapshots");
        requires(downloadScreen, "OfflineMapStore.runActionAsync(this", "Download UI uses asynchronous mutations");
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
        requires(receiver, "if (!heuristicSniffer)", "Key-name guessing is opt-in");
        requires(receiver, "if (depth >= 4) return", "Bundle depth is bounded");
        requires(source("Hex"), "MAX_BYTES = 512", "Hex dumps are bounded");
        // OBD
        String obd = source("Obd2Client");
        requires(obd, "MAX_REPLY_CHARS = 16_384", "ELM replies are bounded");
        requires(obd, "catch (Throwable unexpected)", "An Error cannot kill the poll thread silently");
        requires(obd, "drain(input);", "Stale bytes are discarded before every command");
        requires(obd, "MAX_RECONNECT_DELAY_MS", "Reconnect attempts back off");
        forbids(obd, "private InputStream in", "Stream fields are shared between threads");
        // time base: freshness never depends on the wall clock
        for (String name : new String[]{"VehicleRepository", "HctSyncDecoder", "SystemCanReceiver", "ConsumptionDisplay", "MainActivity"})
            forbids(source(name), "System.currentTimeMillis()", name + " uses VehicleRepository.now(), not wall time");
        // UI hygiene
        for (Path path : Files.list(ROOT).filter(p -> p.toString().endsWith(".java")).toList()) {
            String name = path.getFileName().toString();
            if (name.equals("CopperSkin.java")) continue;
            forbids(Files.readString(path), "AlertDialog.Builder(", name + " builds dialogs through CopperSkin.dialog (dark theme)");
        }
        forbids(main, "info.loadIcon(pm)); } catch", "App icons are not loaded on the UI thread");
        requires(main, "iconLoader.execute(", "App icons load on a background thread");
        forbids(source("SettingsActivity"), "startActivity(new Intent(\"android.settings", "Settings screens start through the safe launcher");
        requires(source("VehiclePreferences"), "door_fl_fault", "Front-left door contact is a user setting");
        // build / release hygiene
        String gradle = Files.readString(Path.of("app/build.gradle"));
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
