#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_dir"
test_output=$(mktemp -d)
trap 'rm -rf "$test_output"' EXIT HUP INT TERM
source_dir=app/src/main/java/de/ronny/pololauncher
java -m jdk.compiler/com.sun.tools.javac.Main ${JAVAC_RELEASE:---release 17} -d "$test_output" \
  tests/stubs/android/location/*.java tests/stubs/android/os/*.java \
  tests/stubs/android/content/*.java tests/stubs/android/graphics/*.java \
  tests/stubs/android/util/*.java tests/stubs/android/view/*.java \
  tests/stubs/android/view/animation/*.java tests/stubs/android/animation/*.java \
  tests/stubs/android/bluetooth/*.java \
  tests/stubs/android/app/*.java tests/stubs/android/database/*.java tests/stubs/android/net/*.java \
  tests/stubs/org/mapsforge/core/model/*.java tests/stubs/org/mapsforge/map/datastore/*.java tests/stubs/org/mapsforge/map/reader/*.java \
  "$source_dir/AverageDisplayFilter.java" "$source_dir/RangeDisplayFilter.java" "$source_dir/RangeEstimator.java" "$source_dir/VehicleState.java" \
  "$source_dir/VehicleRepository.java" "$source_dir/GpsFollowFilter.java" \
  "$source_dir/TravelBearingTracker.java" "$source_dir/LoadTimeoutGate.java" \
  "$source_dir/PositionResultGate.java" "$source_dir/CompletionListeners.java" \
  "$source_dir/RoadGeometry.java" "$source_dir/RoadPositionMatcher.java" "$source_dir/OfflineMapStore.java" \
  "$source_dir/SerialCanFramer.java" "$source_dir/CanFrameNormalizer.java" \
  "$source_dir/HctSyncDecoder.java" "$source_dir/Hex.java" "$source_dir/DiagLog.java" \
  "$source_dir/HctCanbusBinderRuntime.java" "$source_dir/MapFileActivation.java" \
  "$source_dir/MediaInfo.java" "$source_dir/PagedLauncherLayout.java" \
  "$source_dir/SystemCanReceiver.java" "$source_dir/ConsumptionLearner.java" \
  "$source_dir/RangeStore.java" "$source_dir/RangeTrackingSession.java" "$source_dir/RangeDisplayStore.java" \
  "$source_dir/Obd2PidDecoder.java" "$source_dir/Obd2Calibration.java" "$source_dir/Obd2ConsumptionAverager.java" \
  "$source_dir/Obd2Client.java" "$source_dir/Obd2Runtime.java" "$source_dir/DabStartupGate.java" "$source_dir/ConsumptionDisplay.java" tests/*Test.java
for test_class in AverageDisplayFilterTest RangeDisplayFilterTest RangeEstimatorTest GpsFollowFilterTest DiagLogTest \
  LoadTimeoutGateTest CanFrameTest CanBindingTest CanReconnectTest PositionResultGateTest RoadPositionMatcherTest CompletionListenersTest OfflineMapAsyncTest MapFileActivationTest MediaInfoTest PagerGestureTest SystemCanReceiverTest ConsumptionLearnerTest RangeLearningStoreTest RangeTrackingSessionTest Obd2PidDecoderTest Obd2ConsumptionAveragerTest RangeModeTest Obd2ClientTest DabStartupGateTest ConsumptionDisplayTest OfflineMapSizeProbeTest RangeEstimatorRecoveryTest Obd2CalibrationTest Obd2ResilienceTest SourceAuditTest
do
  java -cp "$test_output" "de.ronny.pololauncher.$test_class"
done
