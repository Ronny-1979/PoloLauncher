#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_dir"
# Repository regression guard: these Mapsforge coordinates are JitPack artifacts.
if grep -q "com.github.mapsforge.mapsforge:" app/build.gradle; then
  if ! grep -q "https://jitpack.io" settings.gradle; then
    echo "RepositoryConfigTest: FEHLER - Mapsforge JitPack-Koordinaten vorhanden, aber JitPack-Repository fehlt" >&2
    exit 1
  fi
  echo "RepositoryConfigTest: Mapsforge JitPack repository present"
fi

if ! grep -q "org.openstreetmap.osmosis:osmosis-pbf2:0.49.2" app/build.gradle; then
  echo "RepositoryConfigTest: FEHLER - Osmosis PBF2 dependency fehlt" >&2
  exit 1
fi
echo "RepositoryConfigTest: Osmosis PBF2 dependency present"

if ! grep -Fq "pickFirsts += ['osmosis-plugins.conf']" app/build.gradle; then
  echo "PackagingConfigTest: FEHLER - osmosis-plugins.conf Duplicate-Fix fehlt" >&2
  exit 1
fi
echo "PackagingConfigTest: osmosis-plugins.conf pickFirst configured"

if ! grep -Fq "raw.githubusercontent.com/maplibre/demotiles" app/src/main/java/de/ronny/pololauncher/GermanyOfflineStore.java; then
  echo "GlyphFallbackConfigTest: FEHLER - unabhängiger Glyph-Fallback fehlt" >&2
  exit 1
fi
echo "GlyphFallbackConfigTest: primary + GitHub glyph sources configured"

if ! grep -Fq "OfflineGlyphValidator.isUsable(part, start)" app/src/main/java/de/ronny/pololauncher/GermanyOfflineService.java; then
  echo "GlyphResumeConfigTest: FEHLER - vorhandener gültiger .part-Download wird nicht wiederverwendet" >&2
  exit 1
fi
if grep -Eq "Glyph-Datei leer|part\.length\(\) *<=? *100|target\.length\(\) *<=? *100" app/src/main/java/de/ronny/pololauncher/GermanyOfflineService.java; then
  echo "GlyphResumeConfigTest: FEHLER - alte 100-Byte-Fehlheuristik noch im Produktionscode" >&2
  exit 1
fi
echo "GlyphResumeConfigTest: structural validation + existing .part reuse configured"

traffic_store=app/src/main/java/de/ronny/pololauncher/TrafficDataStore.java
traffic_service=app/src/main/java/de/ronny/pololauncher/GermanyOfflineService.java
if grep -Fq 'execSQL("PRAGMA journal_mode=OFF")' "$traffic_store"; then
  echo "TrafficSqlitePragmaTest: FEHLER - journal_mode wird noch per execSQL ausgeführt" >&2
  exit 1
fi
if ! grep -Fq 'applyPragma("PRAGMA journal_mode=OFF")' "$traffic_store" || \
   ! grep -Fq 'db.rawQuery(sql, null)' "$traffic_store"; then
  echo "TrafficSqlitePragmaTest: FEHLER - query-sicherer PRAGMA-Pfad fehlt" >&2
  exit 1
fi
echo "TrafficSqlitePragmaTest: Android query-safe PRAGMA execution configured"

if ! grep -Fq 'reusableTrafficSource(pbf, remote)' "$traffic_service" || \
   ! grep -Fq 'OSM-Rohdaten für Fortsetzen behalten' "$traffic_service" || \
   ! grep -Fq 'if (activated)' "$traffic_service"; then
  echo "TrafficResumeConfigTest: FEHLER - vollständige PBF wird nach Buildfehler nicht fortsetzbar gehalten" >&2
  exit 1
fi
echo "TrafficResumeConfigTest: completed traffic PBF is reusable after build failure"

if ! grep -Fq 'putExtra(EXTRA_UPDATE_EXISTING, updateExisting)' app/src/main/java/de/ronny/pololauncher/GermanyOfflineStore.java || \
   ! grep -Fq 'localValid && !updateExisting' "$traffic_service" || \
   ! grep -Fq 'vorhanden – beim Fortsetzen übernommen' "$traffic_service"; then
  echo "GermanyResumeMapTest: FEHLER - gültige Karten werden beim Fortsetzen nicht sicher beibehalten" >&2
  exit 1
fi
if ! grep -Fq '!localValid && !updateExisting && part.isFile() && validMbtiles(part)' "$traffic_service"; then
  echo "GermanyResumeMapTest: FEHLER - vollständiger Karten-.part-Download wird beim Fortsetzen nicht übernommen" >&2
  exit 1
fi
if ! grep -Fq 'localTrafficValid && !updateExisting' "$traffic_service"; then
  echo "GermanyResumeTrafficTest: FEHLER - fertige Verkehrsdaten würden beim Fortsetzen erneut online geprüft" >&2
  exit 1
fi
echo "GermanyResumeMapTest: resume keeps valid maps/traffic; explicit ready-state update remains separate"

offline_ui=app/src/main/java/de/ronny/pololauncher/OfflineMapsActivity.java
update_checker=app/src/main/java/de/ronny/pololauncher/GermanyOfflineUpdateChecker.java
if ! grep -Fq 'Auf Updates prüfen' "$offline_ui" || \
   ! grep -Fq 'GermanyOfflineUpdateChecker.checkAsync' "$offline_ui" || \
   ! grep -Fq 'GermanyOfflineStore.startUpdate' "$offline_ui"; then
  echo "GermanyExplicitUpdateUiTest: FEHLER - explizite Deutschland-Updateprüfung/Bestätigung fehlt" >&2
  exit 1
fi
if ! grep -Fq 'setRequestMethod("HEAD")' "$update_checker" || \
   ! grep -Fq 'Karten: ' "$update_checker" || \
   ! grep -Fq 'Verkehr: ' "$update_checker"; then
  echo "GermanyExplicitUpdateUiTest: FEHLER - leichte HEAD-Prüfung oder getrennte Karten/Verkehr-Zählung fehlt" >&2
  exit 1
fi
echo "GermanyExplicitUpdateUiTest: legacy Sachsen updater removed; explicit Germany HEAD check + confirmation configured"

background_service=app/src/main/java/de/ronny/pololauncher/GermanyOfflineService.java
background_store=app/src/main/java/de/ronny/pololauncher/GermanyOfflineStore.java
if ! grep -Fq 'return START_REDELIVER_INTENT;' "$background_service" || \
   ! grep -Fq 'GermanyOfflineStore.markRunRequested(this, updateExisting)' "$background_service" || \
   ! grep -Fq 'scheduleAtFixedRate' "$background_service" || \
   ! grep -Fq 'GermanyOfflineStore.touch(GermanyOfflineService.this)' "$background_service"; then
  echo "GermanyBackgroundResumeTest: FEHLER - redeliver/persistenter Request/Heartbeat fehlt" >&2
  exit 1
fi
if ! grep -Fq 'static void autoResumePending(Context c)' "$background_store" || \
   ! grep -Fq 'GermanyOfflineStore.autoResumePending(this)' app/src/main/java/de/ronny/pololauncher/PoloApplication.java || \
   ! grep -Fq 'GermanyOfflineStore.autoResumePending(this)' app/src/main/java/de/ronny/pololauncher/MainActivity.java; then
  echo "GermanyBackgroundResumeTest: FEHLER - Prozess-/Launcher-Fallback zur Wiederaufnahme fehlt" >&2
  exit 1
fi
if ! grep -Fq 'android.permission.RECEIVE_BOOT_COMPLETED' app/src/main/AndroidManifest.xml || \
   ! grep -Fq '.GermanyOfflineResumeReceiver' app/src/main/AndroidManifest.xml || \
   ! grep -Fq 'Intent.ACTION_BOOT_COMPLETED' app/src/main/java/de/ronny/pololauncher/GermanyOfflineResumeReceiver.java; then
  echo "GermanyBackgroundResumeTest: FEHLER - Boot-Wiederaufnahme fehlt" >&2
  exit 1
fi
if ! grep -Fq 'android:stopWithTask="false"' app/src/main/AndroidManifest.xml || \
   ! grep -Fq 'onTaskRemoved(Intent rootIntent)' "$background_service"; then
  echo "GermanyBackgroundResumeTest: FEHLER - Hintergrundlauf ist an Launcher-Task gekoppelt" >&2
  exit 1
fi
echo "GermanyBackgroundResumeTest: foreground background-run + ACC/process/boot auto-resume configured"

germany_panel=app/src/main/java/de/ronny/pololauncher/GermanyOfflinePanel.java
if grep -Fq 'analyzer.setLocationConsumer(this::deliverLocation)' "$germany_panel"; then
  echo "GermanyLiveNavigationTest: FEHLER - Kartenposition hängt noch an der asynchronen Traffic-Analyse" >&2
  exit 1
fi
if ! grep -Fq 'deliverLocation(location);' "$germany_panel" || \
   ! grep -Fq 'void acceptAnalysisLocation(Location location)' "$germany_panel" || \
   ! grep -Fq 'analyzer.accept(location);' "$germany_panel" || \
   ! grep -Fq 'return location == null ? analyzer.currentLocation() : new Location(location);' "$germany_panel"; then
  echo "GermanyLiveNavigationTest: FEHLER - getrennter Kamera-/Analysepfad fehlt" >&2
  exit 1
fi
if grep -Fq 'if (!following || !ready()) return;' "$germany_panel" || \
   grep -Fq 'if (ready() && following && MapPreferences.autoZoom(context))' "$germany_panel"; then
  echo "GermanyLiveNavigationTest: FEHLER - GPS-Follow/Ortszoom warten noch auf MapLibre fully-rendered" >&2
  exit 1
fi
if ! grep -Fq 'private boolean cameraReady()' "$germany_panel" || \
   ! grep -Fq 'map != null && styleApplied && !failed && !destroyed' "$germany_panel"; then
  echo "GermanyLiveNavigationTest: FEHLER - eigener render-unabhängiger Kamera-Ready-Pfad fehlt" >&2
  exit 1
fi
echo "GermanyLiveNavigationTest: live GPS camera path decoupled from traffic and fully-rendered confirmation"

# Live navigation must also survive MapLibre states where a style is usable before
# the stricter loaded/fully-rendered confirmation arrives.
if ! grep -Fq 'if (destroyed || styleApplied || failed) return;' "$germany_panel" || \
   ! grep -Fq 'surface.removeCallbacks(timeout);' "$germany_panel"; then
  echo "GermanyLiveUsableMapTest: FEHLER - sichtbare Deutschlandkarte kann noch am fully-rendered Timeout scheitern" >&2
  exit 1
fi
echo "GermanyLiveUsableMapTest: offline camera follow uses style-ready state; render confirmation stays separate"

# v0.31.49 parity: raw GPS is for road/settlement analysis; only the filtered camera fix
# moves the map. Hidden map renderers must not consume camera work in the background.
fallback_feed="app/src/main/java/de/ronny/pololauncher/FallbackLocationFeed.java"
main_activity="app/src/main/java/de/ronny/pololauncher/MainActivity.java"
if ! grep -Fq 'Consumer<Location> raw = rawConsumer;' "$fallback_feed" || \
   ! grep -Fq 'raw.accept(new Location(location));' "$fallback_feed" || \
   ! grep -Fq 'Location cameraFix = filter.update(location);' "$fallback_feed"; then
  echo "SachsenFollowParityTest: FEHLER - Roh-GPS und Kamerafix sind nicht getrennt" >&2
  exit 1
fi
if ! grep -Fq 'fallbackGps.setRawConsumer(germanyMap::acceptAnalysisLocation);' "$main_activity" || \
   ! grep -Fq 'fallbackGps.setConsumer(this::acceptGermanyCameraPosition);' "$main_activity" || \
   ! grep -Fq 'germanyMap.acceptLocation(location);' "$main_activity"; then
  echo "SachsenFollowParityTest: FEHLER - Roh-/Kamera-GPS oder sichtbares Renderer-Routing fehlt" >&2
  exit 1
fi
if grep -Fq 'germanyMap.setLocationConsumer(vectorMap::acceptLocation);' "$main_activity"; then
  echo "AlwaysFollowDirectGpsTest: FEHLER - versteckte Deutschlandkarte ist noch Kamera-GPS-Relay" >&2
  exit 1
fi
if grep -Fq 'following = false;' "$germany_panel"; then
  echo "AlwaysFollowDirectGpsTest: FEHLER - MapLibre kann Follow noch dauerhaft abschalten" >&2
  exit 1
fi
echo "AlwaysFollowDirectGpsTest: permanent follow + direct visible-renderer GPS routing configured"
if ! grep -Fq 'if (!visible || !following || !cameraReady()) return;' "$germany_panel" || \
   ! grep -Fq 'CameraMotion.isJitter(distance[0]' "$germany_panel" || \
   ! grep -Fq 'distance[0] <= 250f' "$germany_panel" || \
   ! grep -Fq 'interval < 5_000L' "$germany_panel" || \
   ! grep -Fq '* 1.1d)))' "$germany_panel"; then
  echo "SachsenFollowParityTest: FEHLER - bewährte v0.31.49 Follow-Schwellen fehlen" >&2
  exit 1
fi
if ! grep -Fq 'changeZoom(wantedZoom());' "$germany_panel" || \
   ! grep -Fq 'surface.postDelayed(this, 500L);' "$germany_panel"; then
  echo "SachsenFollowParityTest: FEHLER - Ortszoom ist nicht mehr getrennt von GPS-Positionsbewegung" >&2
  exit 1
fi
echo "SachsenFollowParityTest: raw analysis + filtered camera + v0.31.49 follow thresholds configured"
germany_analyzer="app/src/main/java/de/ronny/pololauncher/GermanyOfflineAnalyzer.java"
if ! grep -Fq 'SettlementZoomGate areaGate' "$germany_analyzer" || \
   grep -Fq 'directBoundaryZoom' "$germany_analyzer" || \
   ! grep -Fq 'areaGate.update(' "$germany_analyzer"; then
  echo "SachsenFollowParityTest: FEHLER - bewährte Ortszoom-Hysterese fehlt oder der alte Direkt/Stabil-Schalter ist wieder da" >&2
  exit 1
fi
echo "SachsenFollowParityTest: Germany autozoom reuses v0.31.49 direct/stable hysteresis"

# Standby wake: head-unit quick-start leaves the MapLibre surfaces blank; the Activity must rebuild once.
if ! grep -Fq 'onStandbyWake("Bildschirm an");' "$main_activity" || \
   ! grep -Fq 'onStandbyWake("Quick-Start");' "$main_activity" || \
   ! grep -Fq 'private static long lastWakeMapRebuildMs;' "$main_activity" || \
   ! grep -Fq 'Karte nach Standby neu aufgebaut' "$main_activity" || \
   ! grep -Fq '|| !this.activityVisible) return;' "$main_activity"; then
  echo "StandbyMapRebuildTest: FEHLER - Karten-Neuaufbau nach Standby fehlt oder hat keinen Cooldown" >&2
  exit 1
fi
echo "StandbyMapRebuildTest: wake (screen-on + quick-start) rebuilds map once with static cooldown"

# Germany road snap must never block live follow and must not change the raw GPS used by autozoom.
germany_matcher="app/src/main/java/de/ronny/pololauncher/GermanyRoadPositionMatcher.java"
map_settings="app/src/main/java/de/ronny/pololauncher/MapSettingsActivity.java"
if ! grep -Fq 'private static final long FALLBACK_MS = 350L;' "$germany_matcher" || \
   ! grep -Fq 'resultGate.fallback()' "$germany_matcher" || \
   ! grep -Fq 'Math.max(18d, Math.min(35d, accuracy * 1.5d + 8d))' "$germany_matcher" || \
   grep -Fq 'road.speed == SpeedLimitValue.UNKNOWN' "$germany_matcher"; then
  echo "GermanyRoadSnapTest: FEHLER - Mapsforge-Paritaet/Fallback der Strassenkorrektur fehlt" >&2
  exit 1
fi
if ! grep -Fq 'fallbackGps.setConsumer(this::acceptGermanyCameraPosition);' "$main_activity" || \
   ! grep -Fq 'if (matcher != null && matcher.available()) {' "$main_activity" || \
   ! grep -Fq 'fallbackGps.setRawConsumer(germanyMap::acceptAnalysisLocation);' "$main_activity"; then
  echo "GermanyRoadSnapTest: FEHLER - Kamera-Snap und Roh-GPS-Analyse sind nicht sauber getrennt" >&2
  exit 1
fi
if grep -Fq 'Punkt auf Straße einrasten' "$map_settings" || grep -Fq 'setRoadSnap' "$map_settings" || \
   grep -Fq 'roadSnap' app/src/main/java/de/ronny/pololauncher/MapPreferences.java; then
  echo "GermanyRoadSnapTest: FEHLER - Straßeneinrasten ist nicht fest verbaut (Menüpunkt/Schalter noch vorhanden)" >&2
  exit 1
fi
echo "GermanyRoadSnapTest: v0.31.49 snap envelope + 350 ms raw-GPS fallback configured"

# Explicit regression guard for adaptive zoom after the direct-GPS/follow changes.
if ! grep -Fq 'MapArea observation = classifyArea(speed);' "$germany_analyzer" || \
   ! grep -Fq 'Consumer<MapArea> c = areaConsumer;' "$germany_analyzer" || \
   ! grep -Fq 'changeZoom(wantedZoom());' "$germany_panel"; then
  echo "GermanyAutoZoomRoutingTest: FEHLER - Innerorts/Außerorts-Zoom ist nicht bis zur sichtbaren Karte verdrahtet" >&2
  exit 1
fi
echo "GermanyAutoZoomRoutingTest: raw GPS -> area gate -> visible offline zoom path intact"

# The dashboard's 3-second UI polling loop must never execute the full Germany package
# validation. ready() opens/checks all regional traffic SQLite databases and is only for
# explicit/startup validation; driving-time polling may use the persisted READY flag only.
runtime_refresh=$(sed -n '/private final Runnable refreshNetworkState = new Runnable()/,/^    };/p' "$main_activity")
if printf '%s\n' "$runtime_refresh" | grep -Fq 'GermanyOfflineStore.ready(MainActivity.this)'; then
  echo "GermanyRuntimeUiThreadTest: FEHLER - vollständige Deutschland-Validierung läuft noch im 3-Sekunden-UI-Loop" >&2
  exit 1
fi
main_ready_calls=$(grep -Fc 'GermanyOfflineStore.ready(this)' "$main_activity" || true)
if [ "$main_ready_calls" -gt 1 ]; then
  echo "GermanyRuntimeUiThreadTest: FEHLER - MainActivity validiert das 8,3-GB-Paket mehrfach synchron" >&2
  exit 1
fi
if ! grep -Fq 'static boolean markedReady(Context c)' app/src/main/java/de/ronny/pololauncher/GermanyOfflineStore.java; then
  echo "GermanyRuntimeUiThreadTest: FEHLER - günstiger READY-Flag-Pfad fehlt" >&2
  exit 1
fi
echo "GermanyRuntimeUiThreadTest: no full 14-region SQLite validation in 3-second driving UI loop"

# Offline-first startup: the Germany MapLibre renderer must start hidden internally and then be
# promoted explicitly by MainActivity before the online renderer is considered. This guards the
# real radio symptom "black map until LTE/online map appears".
if ! grep -Fq 'private boolean visible;' "$germany_panel" ||    ! grep -Fq 'view.setVisibility(View.GONE);' "$germany_panel" ||    ! grep -Fq 'gps.setVisibility(View.GONE);' "$germany_panel" ||    ! grep -Fq 'germanyMap.setVisible(true);' "$main_activity" ||    ! grep -Fq 'requestRenderFrame();' "$germany_panel" ||    ! grep -Fq 'map.triggerRepaint();' "$germany_panel" ||    ! grep -Fq 'view.postInvalidate();' "$germany_panel"; then
  echo "GermanyOfflineFirstStartupTest: FEHLER - Offline-first/Render-Reaktivierung fehlt" >&2
  exit 1
fi
if grep -Fq 'private boolean visible = true;' "$germany_panel"; then
  echo "GermanyOfflineFirstStartupTest: FEHLER - initiales visible=true würde setVisible(true) wieder kurzschließen" >&2
  exit 1
fi
echo "GermanyOfflineFirstStartupTest: Germany map is explicitly promoted first and requests a fresh frame"

test_output=$(mktemp -d)
trap 'rm -rf "$test_output"' EXIT HUP INT TERM
source_dir=app/src/main/java/de/ronny/pololauncher
java -m jdk.compiler/com.sun.tools.javac.Main ${JAVAC_RELEASE:---release 17} -d "$test_output" \
  tests/stubs/android/location/*.java tests/stubs/android/os/*.java \
  tests/stubs/android/content/*.java tests/stubs/android/graphics/*.java \
  tests/stubs/android/util/*.java tests/stubs/android/view/*.java \
  tests/stubs/android/view/animation/*.java tests/stubs/android/animation/*.java \
  tests/stubs/android/bluetooth/*.java tests/stubs/de/ronny/pololauncher/*.java \
  tests/stubs/android/app/*.java tests/stubs/android/database/*.java tests/stubs/android/database/sqlite/*.java tests/stubs/android/net/*.java \
  tests/stubs/org/openstreetmap/osmosis/core/domain/v0_6/*.java tests/stubs/org/openstreetmap/osmosis/core/container/v0_6/*.java tests/stubs/org/openstreetmap/osmosis/core/task/v0_6/*.java tests/stubs/org/openstreetmap/osmosis/pbf2/v0_6/*.java \
  "$source_dir/AverageDisplayFilter.java" "$source_dir/RangeDisplayFilter.java" "$source_dir/RangeEstimator.java" "$source_dir/VehicleState.java" \
  "$source_dir/VehicleRepository.java" "$source_dir/GpsFollowFilter.java" \
  "$source_dir/TravelBearingTracker.java" \
  "$source_dir/PositionResultGate.java" "$source_dir/CameraMotion.java" "$source_dir/CameraAim.java" "$source_dir/BearingSmoother.java" \
  "$source_dir/OfflineGlyphValidator.java" "$source_dir/GermanyOfflineUpdatePolicy.java" "$source_dir/TrafficPbfScanner.java" "$source_dir/TrafficDataStore.java" "$source_dir/TrafficDataManager.java" "$source_dir/GermanyRegions.java" "$source_dir/MapArea.java" "$source_dir/GermanyTrafficReader.java" "$source_dir/GermanyRoadPositionMatcher.java" "$source_dir/MapFollowDiagnostics.java" \
  "$source_dir/RoadGeometry.java" "$source_dir/RoadSnapSelector.java" "$source_dir/RoadSnapCandidates.java" "$source_dir/RoadKinds.java" "$source_dir/MvtRoadDecoder.java" "$source_dir/GermanyRoadTiles.java" "$source_dir/GermanyRegionIndex.java" "$source_dir/LocalTileServer.java" "$source_dir/OfflineStyleComposer.java" \
  "$source_dir/SettlementZoomGate.java" \
  "$source_dir/SpeedLimitValue.java" "$source_dir/SpeedLimitGate.java" "$source_dir/SpeedLimitDiagnostics.java" \
  "$source_dir/SpeedCameraDecision.java" "$source_dir/SpeedCameraGate.java" \
  "$source_dir/SerialCanFramer.java" "$source_dir/CanFrameNormalizer.java" \
  "$source_dir/HctSyncDecoder.java" "$source_dir/Hex.java" "$source_dir/DiagLog.java" \
  "$source_dir/HctCanbusBinderRuntime.java" \
  "$source_dir/MediaInfo.java" "$source_dir/PagedLauncherLayout.java" \
  "$source_dir/SystemCanReceiver.java" \
  "$source_dir/RangeStore.java" "$source_dir/RangeTrackingSession.java" "$source_dir/RangeDisplayStore.java" \
  "$source_dir/Obd2PidDecoder.java" "$source_dir/Obd2ConsumptionAverager.java" \
  "$source_dir/Obd2Client.java" "$source_dir/Obd2Runtime.java" "$source_dir/DabStartupGate.java" "$source_dir/StandbyWakeDetector.java" "$source_dir/CarPlayReturnGate.java" "$source_dir/ConsumptionDisplay.java" "$source_dir/SeatbeltAlert.java" "$source_dir/DabReturnGate.java" "$source_dir/MapUpdateSchedule.java" "$source_dir/UsbImportLayout.java" "$source_dir/MarkerStyle.java" "$source_dir/ObdHistory.java" "$source_dir/MapResumePolicy.java" \
  "$source_dir/WeatherForecast.java" "$source_dir/Locality.java" "$source_dir/SolarDaylight.java" "$source_dir/ImageSampling.java" tests/*Test.java tests/DiagLogReset.java
for test_class in AverageDisplayFilterTest RangeDisplayFilterTest RangeEstimatorTest GpsFollowFilterTest DiagLogTest \
  OfflineGlyphValidatorTest CanFrameTest CanBindingTest CanReconnectTest PositionResultGateTest CameraMotionTest CameraAimTest BearingSmootherTest RpmFrozenTest SeatbeltAlertTest RoadSnapSelectorTest MvtRoadDecoderTest OfflineStyleTest LocalTileServerTest SettlementZoomGateTest SpeedLimitValueTest SpeedLimitGateTest SpeedLimitDiagnosticsTest SpeedCameraDecisionTest SpeedCameraGateTest GermanyOfflineUpdatePolicyTest TrafficPbfScannerTest GermanyRoadMatcherListenerTest GermanyRoadSnapParityTest MapFollowDiagnosticsTest TrafficDataBoundsTest MediaInfoTest PagerGestureTest SystemCanReceiverTest RangeTrackingSessionTest Obd2PidDecoderTest Obd2ConsumptionAveragerTest CombinedRangeModelTest Obd2ClientTest DabStartupGateTest StandbyWakeDetectorTest CarPlayReturnGateTest DabReturnGateTest MapUpdateScheduleTest UsbImportLayoutTest MarkerStyleTest ObdHistoryTest MapResumePolicyTest ConsumptionDisplayTest WeatherForecastTest LocalityTest SolarDaylightTest ImageSamplingTest RangeEstimatorRecoveryTest Obd2ResilienceTest DeepInvariantTest SourceAuditTest
do
  java -cp "$test_output" "de.ronny.pololauncher.$test_class"
done
