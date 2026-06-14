[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$logDirectory = Join-Path $root "build/reports/build-warnings/logs"
$wrapper = if ($IsWindows) { Join-Path $root "gradlew.bat" } else { Join-Path $root "gradlew" }
$sourceFailures = [System.Collections.Generic.List[string]]::new()

@(
    (Join-Path $root "build/cloudsimplus-raw-m2"),
    (Join-Path $root "build/cloudsimplus-m2"),
    (Join-Path $root "build/cloudsimplus-version.txt"),
    (Join-Path $root "build/reports/build-warnings")
) | ForEach-Object {
    if (Test-Path -LiteralPath $_) {
        Remove-Item -LiteralPath $_ -Recurse -Force
    }
}

New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null

function Invoke-AuditedGradleTask {
    param(
        [Parameter(Mandatory = $true)]
        [string]$TaskName,
        [Parameter(Mandatory = $true)]
        [string]$LogName,
        [string[]]$AdditionalArguments = @()
    )

    $logPath = Join-Path $logDirectory $LogName
    $arguments = @(
        $TaskName,
        "--rerun-tasks",
        "--no-daemon",
        "--stacktrace",
        "--warning-mode=all",
        "--configuration-cache"
    ) + $AdditionalArguments

    Write-Host "Auditing Gradle task: $TaskName"
    & $wrapper @arguments 2>&1 | Tee-Object -FilePath $logPath
    if ($LASTEXITCODE -ne 0) {
        $sourceFailures.Add("$TaskName exited with code $LASTEXITCODE")
    }
}

Push-Location $root
try {
    Invoke-AuditedGradleTask "sanitizeCloudSimPlusJarManifest" "buildCloudSimPlusFromSource.log"
    Invoke-AuditedGradleTask "compileKotlin" "compileKotlin.log" @(
        "-x", "prepareCloudSimPlusSource",
        "-x", "buildCloudSimPlusFromSource",
        "-x", "sanitizeCloudSimPlusJarManifest"
    )
    Invoke-AuditedGradleTask "detekt" "detekt.log"
    Invoke-AuditedGradleTask "ktlintCheck" "ktlintCheck.log"

    & $wrapper verifyBuildWarnings --no-daemon --stacktrace --configuration-cache
    $auditExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

$auditReport = Join-Path $root "build/reports/build-warnings/audit.md"
if (Test-Path -LiteralPath $auditReport) {
    Write-Host ""
    Get-Content -LiteralPath $auditReport | Write-Host
}

if ($sourceFailures.Count -gt 0) {
    $sourceFailures | ForEach-Object { Write-Error $_ }
    exit 1
}
if ($auditExitCode -ne 0) {
    exit $auditExitCode
}
