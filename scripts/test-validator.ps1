param(
    [string]$ItaxHome = "C:\itaxview\iTax Viewer",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$ProjectDir = Split-Path -Parent (Split-Path -Parent $PSCommandPath)

if (-not $SkipBuild) {
    Write-Host "=== BUILD ===" -ForegroundColor Cyan
    Push-Location $ProjectDir
    try {
        mvn package -B -q
        if ($LASTEXITCODE -ne 0) { throw "Build failed" }
    } finally { Pop-Location }
    Write-Host "Build OK`n" -ForegroundColor Green
}

$JarFile = Get-ChildItem "$ProjectDir\target\itax-validator-*.jar" | Select-Object -First 1
if (-not $JarFile) { throw "JAR not found" }

if (-not (Test-Path "$ItaxHome\data\DMucTKhai.xml")) {
    Write-Warning "ITAX_HOME not found at '$ItaxHome'. Use: $($PSCommandPath) -ItaxHome <path>"
    exit 2
}

$TestDir = "$ProjectDir\src\test\resources"
$tests = @(
    @{Name="Valid XML"; Path="$TestDir\valid-gtgt.xml"; ExpectedExit=0; Type="valid"},
    @{Name="Missing maTKhai"; Path="$TestDir\invalid-missing-makhai.xml"; ExpectedExit=1; Type="invalid"},
    @{Name="Wrong root"; Path="$TestDir\invalid-wrong-root.xml"; ExpectedExit=1; Type="invalid"},
    @{Name="Directory walk"; Path="$TestDir"; ExpectedExit=1; Type="mixed"},
    @{Name="Real signed XML"; Path="$ItaxHome\itax.xml"; ExpectedExit=0; Type="valid"}
)

$passed = 0; $failed = 0
Write-Host "=== INTEGRATION TESTS ===" -ForegroundColor Cyan

foreach ($t in $tests) {
    $result = & java -jar "$($JarFile.FullName)" "$ItaxHome" "$($t.Path)" 2>&1
    $exit = $LASTEXITCODE

    if ($exit -eq $t.ExpectedExit) {
        Write-Host "  [PASS] $($t.Name) (exit=$exit)" -ForegroundColor Green
        $passed++
    } else {
        Write-Host "  [FAIL] $($t.Name) (expected $($t.ExpectedExit), got $exit)" -ForegroundColor Red
        $failed++
    }
}

Write-Host "`n=== RESULTS: $passed passed, $failed failed ===" -ForegroundColor Cyan
exit ($failed -gt 0 ? 1 : 0)
