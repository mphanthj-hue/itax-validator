param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$ItaxHome,

    [Parameter(Mandatory = $true, Position = 1, ValueFromRemainingArguments = $true)]
    [string[]]$Targets
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $PSCommandPath
$ProjectDir = Split-Path -Parent $ScriptDir
$TargetDir = "$ProjectDir\target"
$LibDir = "$TargetDir\lib"

if (-not (Test-Path -LiteralPath $TargetDir)) {
    Write-Error "Build not found. Run 'mvn package' first."
    exit 2
}

$JarFile = Get-ChildItem "$TargetDir\itax-validator-*.jar" | Select-Object -First 1
if (-not $JarFile) {
    Write-Error "Validator JAR not found in $TargetDir"
    exit 2
}

$Classpath = @($JarFile.FullName)
if (Test-Path -LiteralPath $LibDir) {
    Get-ChildItem "$LibDir\*.jar" | ForEach-Object { $Classpath += $_.FullName }
}
$Cp = $Classpath -join ";"

Push-Location $ItaxHome
try {
    & "java" -cp "$Cp" "seatechit.ihtkk.tool.validator.TaxValidatorCLI" "$ItaxHome" @Targets
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
