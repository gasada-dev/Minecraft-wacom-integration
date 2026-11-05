$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
}
if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
    throw 'Install JDK 25 and configure JAVA_HOME or PATH.'
}
& .\gradlew.bat --no-daemon --console=plain clean build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Get-ChildItem 'build\libs\*.jar' | Where-Object Name -NotLike '*-sources.jar' | ForEach-Object {
    Write-Host "Verified mod: $($_.FullName)"
    Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256
}
