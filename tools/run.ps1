param(
    [ValidateSet('Backend', 'Frontend', 'Test', 'Build', 'Install')]
    [string]$Task = 'Backend'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

function Initialize-Java {
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) { return }
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCommand) { return }
    $jdkDirectory = Join-Path $env:USERPROFILE '.jdks'
    if (Test-Path -LiteralPath $jdkDirectory) {
        $candidate = Get-ChildItem -LiteralPath $jdkDirectory -Directory |
            Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin/javac.exe') } |
            Sort-Object Name -Descending | Select-Object -First 1
        if ($candidate) { $env:JAVA_HOME = $candidate.FullName; return }
    }
    throw 'Java 25 vagy ujabb JDK szukseges. Allitsd be a JAVA_HOME valtozot.'
}

function Invoke-Maven([string[]]$MavenArgs) {
    Initialize-Java
    Push-Location (Join-Path $projectRoot 'backend')
    try {
        & ./mvnw.cmd @MavenArgs
        if ($LASTEXITCODE -ne 0) { throw 'A backend muvelet nem sikerult. Lasd a fenti hibat.' }
    } finally { Pop-Location }
}

function Invoke-Frontend([string[]]$PackageArgs) {
    $pnpm = Get-Command pnpm -ErrorAction SilentlyContinue
    Push-Location (Join-Path $projectRoot 'frontend')
    try {
        if ($pnpm) { & $pnpm.Source @PackageArgs }
        else {
            $runtime = Join-Path $env:USERPROFILE '.cache/codex-runtimes/codex-primary-runtime/dependencies/node'
            $bundledPnpm = Join-Path $runtime 'node_modules/pnpm/bin/pnpm.mjs'
            $bundledNode = Join-Path $runtime 'bin/node.exe'
            if (!(Test-Path -LiteralPath $bundledPnpm)) { throw 'Node.js es pnpm szukseges a frontendhez.' }
            $env:Path = (Join-Path $runtime 'bin') + ';' + $env:Path
            & $bundledNode $bundledPnpm @PackageArgs
        }
        if ($LASTEXITCODE -ne 0) { throw 'A frontend muvelet nem sikerult. Lasd a fenti hibat.' }
    } finally { Pop-Location }
}

switch ($Task) {
    'Backend' { Invoke-Maven @('test-compile', 'exec:java', '-Dexec.mainClass=hu.eszkozpont.LocalDevelopment', '-Dexec.classpathScope=test') }
    'Frontend' { Invoke-Frontend @('run', 'dev') }
    'Install' { Invoke-Frontend @('install', '--frozen-lockfile') }
    'Test' { Invoke-Maven @('test'); Invoke-Frontend @('run', 'test') }
    'Build' { Invoke-Maven @('-DskipTests', 'package'); Invoke-Frontend @('run', 'build') }
}
