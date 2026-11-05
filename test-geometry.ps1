$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
New-Item -Force -ItemType Directory '.geometry-check' | Out-Null
& javac -d '.geometry-check' 'src/main/java/dev/artmap/pen/math/CanvasAim.java' 'src/test/java/dev/artmap/pen/math/CanvasAimSelfTest.java'
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -cp '.geometry-check' dev.artmap.pen.math.CanvasAimSelfTest
exit $LASTEXITCODE
