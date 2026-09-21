$ErrorActionPreference = 'Stop'
# Gleiche Testliste wie tests/run-tests.sh (dort ist die maßgebliche Fassung).
$projectRoot = Split-Path $PSScriptRoot -Parent
$testOutput = Join-Path ([System.IO.Path]::GetTempPath()) ('polo-tests-' + [guid]::NewGuid().ToString('N'))
$originalLocation = Get-Location
New-Item -ItemType Directory -Path $testOutput | Out-Null
try {
    Set-Location -LiteralPath $projectRoot
    $stubDirs = @('android/location','android/os','android/content','android/graphics','android/util','android/view','android/view/animation','android/animation','android/bluetooth','android/app','android/database','android/net','org/mapsforge/core/model','org/mapsforge/map/datastore','org/mapsforge/map/reader')
    $stubSources = @($stubDirs | ForEach-Object { Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot ('stubs/' + $_)) -Filter '*.java' | ForEach-Object { $_.FullName } })
    $testSources = @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*Test.java' | ForEach-Object { $_.FullName })
    $names = @('AverageDisplayFilter','RangeDisplayFilter','RangeEstimator','VehicleState','VehicleRepository','GpsFollowFilter','TravelBearingTracker','LoadTimeoutGate','PositionResultGate','CompletionListeners','RoadGeometry','RoadPositionMatcher','OfflineMapStore','SerialCanFramer','CanFrameNormalizer','HctSyncDecoder','Hex','DiagLog','HctCanbusBinderRuntime','MapFileActivation','MediaInfo','PagedLauncherLayout','SystemCanReceiver','ConsumptionLearner','RangeStore','RangeTrackingSession','RangeDisplayStore','Obd2PidDecoder','Obd2Calibration','Obd2ConsumptionAverager','Obd2Client','Obd2Runtime','DabStartupGate','ConsumptionDisplay')
    $productionSources = @($names | ForEach-Object { Join-Path $projectRoot ('app/src/main/java/de/ronny/pololauncher/' + $_ + '.java') })
    & java -m jdk.compiler/com.sun.tools.javac.Main --release 17 -d $testOutput @stubSources @productionSources @testSources
    if ($LASTEXITCODE -ne 0) { throw 'Testkompilierung fehlgeschlagen' }
    $suites = @('AverageDisplayFilterTest','RangeDisplayFilterTest','RangeEstimatorTest','GpsFollowFilterTest','DiagLogTest','LoadTimeoutGateTest','CanFrameTest','CanBindingTest','CanReconnectTest','PositionResultGateTest','RoadPositionMatcherTest','CompletionListenersTest','OfflineMapAsyncTest','MapFileActivationTest','MediaInfoTest','PagerGestureTest','SystemCanReceiverTest','ConsumptionLearnerTest','RangeLearningStoreTest','RangeTrackingSessionTest','Obd2PidDecoderTest','Obd2ConsumptionAveragerTest','RangeModeTest','Obd2ClientTest','DabStartupGateTest','ConsumptionDisplayTest','OfflineMapSizeProbeTest','RangeEstimatorRecoveryTest','Obd2CalibrationTest','Obd2ResilienceTest','SourceAuditTest')
    foreach ($suite in $suites) {
        & java -cp $testOutput ('de.ronny.pololauncher.' + $suite)
        if ($LASTEXITCODE -ne 0) { throw ('Test fehlgeschlagen: ' + $suite) }
    }
} finally {
    Set-Location -LiteralPath $originalLocation.Path
    Remove-Item -LiteralPath $testOutput -Recurse -Force
}
