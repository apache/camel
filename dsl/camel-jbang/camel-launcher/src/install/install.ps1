#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

param(
    [string] $Version
)

$ErrorActionPreference = 'Stop'

# Test seams only: production installs never set these, so the defaults below are always used.
$ManifestBaseUrl = if ($env:CAMEL_INSTALL_MANIFEST_BASE_URL) { $env:CAMEL_INSTALL_MANIFEST_BASE_URL } else { 'https://camel.apache.org/camel-cli/releases' }
$MavenBaseUrl = if ($env:CAMEL_INSTALL_MAVEN_BASE_URL) { $env:CAMEL_INSTALL_MAVEN_BASE_URL } else { 'https://repo1.maven.org/maven2/org/apache/camel/camel-launcher' }
$CaCertPath = $env:CAMEL_INSTALL_CA_CERT

$InstallRoot = Join-Path $env:LOCALAPPDATA 'Apache Camel'
$DataRoot = Join-Path $InstallRoot 'cli\versions'
$BinDir = Join-Path $InstallRoot 'bin'

function Fail {
    param([string] $Message)
    [Console]::Error.WriteLine("install.ps1: $Message")
    exit 1
}

function Test-ValidVersion {
    param([string] $Value)
    return $Value -match '\A[0-9]+\.[0-9]+\.[0-9]+\z'
}

function Test-ValidSha256 {
    param([string] $Value, [string] $Label)
    if ($Value -notmatch '\A[0-9a-f]{64}\z') {
        Fail "$Label is not a 64-character lowercase hex value"
    }
}

if ($CaCertPath) {
    # Test seam only: trusts the loopback fixture's self-signed CA for this process without touching
    # the real Windows certificate store. Production installs never set CAMEL_INSTALL_CA_CERT.
    $installerCaCert = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($CaCertPath)
    [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12
    [System.Net.ServicePointManager]::ServerCertificateValidationCallback = {
        param($sender, $certificate, $chain, $sslPolicyErrors)
        $verifyChain = New-Object System.Security.Cryptography.X509Certificates.X509Chain
        $verifyChain.ChainPolicy.ExtraStore.Add($installerCaCert) | Out-Null
        $verifyChain.ChainPolicy.RevocationMode = [System.Security.Cryptography.X509Certificates.X509RevocationMode]::NoCheck
        $verifyChain.ChainPolicy.VerificationFlags = [System.Security.Cryptography.X509Certificates.X509VerificationFlags]::AllowUnknownCertificateAuthority
        $leaf = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($certificate)
        if (-not $verifyChain.Build($leaf)) {
            return $false
        }
        $root = $verifyChain.ChainElements[$verifyChain.ChainElements.Count - 1].Certificate
        return $root.Thumbprint -eq $installerCaCert.Thumbprint
    }.GetNewClosure()
}

# Downloads $Url to $OutFile; used for both the manifest and archive fetches.
function Save-RemoteFile {
    param([string] $Url, [string] $OutFile)
    try {
        Invoke-WebRequest -Uri $Url -OutFile $OutFile -UseBasicParsing | Out-Null
    } catch {
        Fail "failed to download $Url"
    }
}

# Reads $Path line by line without ever dot-sourcing, invoking, or evaluating its content.
function Read-Manifest {
    param([string] $Path)

    # Drop comment lines (properties-style '#', and the '##' ASF license header the website's manifest
    # generator prepends) before validation; they carry no data. Blank lines are kept so they still
    # trip the exactly-four-lines / blank-line checks below.
    $lines = @(Get-Content -LiteralPath $Path -Encoding UTF8 | Where-Object { -not $_.StartsWith('#') })
    if ($lines.Count -ne 4) {
        Fail "manifest must contain exactly four lines"
    }

    $known = @('format', 'version', 'tar_sha256', 'zip_sha256')
    $values = New-Object 'System.Collections.Generic.Dictionary[string,string]' ([StringComparer]::OrdinalIgnoreCase)
    foreach ($line in $lines) {
        if ([string]::IsNullOrEmpty($line)) {
            Fail "manifest contains a blank line"
        }
        $parts = $line.Split('=', 2)
        if ($parts.Count -ne 2 -or [string]::IsNullOrEmpty($parts[0])) {
            Fail "manifest contains a blank line"
        }
        $key = $parts[0]
        $value = $parts[1]
        if ([string]::IsNullOrEmpty($value)) {
            Fail "manifest key '$key' has an empty value"
        }
        if ($values.ContainsKey($key)) {
            Fail "manifest has duplicate key: $key"
        }
        if ($known -notcontains $key.ToLowerInvariant()) {
            Fail "manifest has unknown key: $key"
        }
        $values[$key] = $value
    }

    foreach ($required in $known) {
        if (-not $values.ContainsKey($required)) {
            Fail "manifest is missing a required key"
        }
    }

    if ($values['format'] -ne '1') {
        Fail "unsupported manifest format: $($values['format'])"
    }
    if (-not (Test-ValidVersion $values['version'])) {
        Fail "manifest version is not a valid X.Y.Z value"
    }
    Test-ValidSha256 $values['tar_sha256'] 'manifest tar_sha256'
    Test-ValidSha256 $values['zip_sha256'] 'manifest zip_sha256'

    return $values
}

# Lists archive entries via System.IO.Compression before Expand-Archive ever runs, and rejects absolute
# paths, traversal, symlink/reparse-point entries, multiple top-level roots, and a missing launcher.
function Test-ArchiveEntry {
    param([string] $ArchivePath, [string] $Version)

    $expectedRoot = "camel-launcher-$Version"
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($ArchivePath)
    try {
        $roots = New-Object 'System.Collections.Generic.HashSet[string]'
        $foundBatchLauncher = $false
        foreach ($entry in $zip.Entries) {
            $name = $entry.FullName
            if ([string]::IsNullOrEmpty($name)) {
                continue
            }
            if ($name.StartsWith('/') -or $name.StartsWith('\') -or ($name.Length -ge 2 -and $name[1] -eq ':')) {
                Fail "archive contains an absolute path entry: $name"
            }
            $normalized = $name.Replace('\', '/')
            $segments = $normalized.Split('/')
            if ($segments -contains '..') {
                Fail "archive contains a path traversal entry: $name"
            }
            $unixMode = ([uint32]$entry.ExternalAttributes -shr 16) -band 0xF000
            if ($unixMode -eq 0xA000) {
                Fail "archive contains a symbolic link or reparse point entry, which is not allowed"
            }
            [void]$roots.Add($segments[0])
            if ($normalized -eq "$expectedRoot/bin/camel.bat") {
                $foundBatchLauncher = $true
            }
        }
        if ($roots.Count -ne 1) {
            Fail "archive must contain exactly one top-level directory"
        }
        if (-not $roots.Contains($expectedRoot)) {
            Fail "archive top-level directory does not match expected version: $($roots -join ',')"
        }
        if (-not $foundBatchLauncher) {
            Fail "archive is missing bin\camel.bat"
        }
    } finally {
        $zip.Dispose()
    }
}

# Runs the freshly staged upstream launcher; a nonzero exit (e.g. no Java 17+ available) aborts the
# install and leaves the previously active installation untouched.
function Test-StagedLauncher {
    param([string] $LauncherPath)
    try {
        & $LauncherPath 'version' *> $null
    } catch {
        Fail "staged launcher failed verification (Java 17+ required)"
    }
    if ($LASTEXITCODE -ne 0) {
        Fail "staged launcher failed verification (Java 17+ required)"
    }
}

function Set-CamelShim {
    param([string] $Version, [string] $StagedRoot)

    $targetDir = Join-Path $DataRoot $Version
    New-Item -ItemType Directory -Force -Path $DataRoot | Out-Null
    if (Test-Path -LiteralPath $targetDir) {
        Remove-Item -LiteralPath $targetDir -Recurse -Force
    }
    Move-Item -LiteralPath $StagedRoot -Destination $targetDir

    New-Item -ItemType Directory -Force -Path $BinDir | Out-Null
    $launcherPath = Join-Path $targetDir 'bin\camel.bat'
    $shimContent = "@echo off`r`ncall `"$launcherPath`" %*`r`nexit /b %ERRORLEVEL%`r`n"
    $tempShim = Join-Path $BinDir ".camel.$PID.tmp.cmd"
    # Write without a BOM: Windows PowerShell 5.1's 'Set-Content -Encoding UTF8' prepends one, which
    # cmd.exe treats as part of the first line, breaking '@echo off' and emitting a stray error.
    [System.IO.File]::WriteAllText($tempShim, $shimContent, (New-Object System.Text.UTF8Encoding($false)))
    $finalShim = Join-Path $BinDir 'camel.cmd'
    Move-Item -LiteralPath $tempShim -Destination $finalShim -Force
}

# Adds $Dir once, case-insensitively, to the current user's PATH (registry-level, no elevation) and to
# this process; the machine PATH is never written. The value is read and written through the registry
# with DoNotExpandEnvironmentNames so existing %VAR% references are preserved rather than flattened,
# and the original value kind (typically REG_EXPAND_SZ) is kept intact.
function Add-UserPath {
    param([string] $Dir)

    $key = [Microsoft.Win32.Registry]::CurrentUser.CreateSubKey('Environment')
    try {
        $userPath = $key.GetValue('Path', '', [Microsoft.Win32.RegistryValueOptions]::DoNotExpandEnvironmentNames)
        $kind = if ($userPath) { $key.GetValueKind('Path') } else { [Microsoft.Win32.RegistryValueKind]::ExpandString }
        $entries = @()
        if ($userPath) {
            $entries = $userPath.Split(';') | Where-Object { $_ -ne '' }
        }
        $present = $entries | Where-Object { $_.TrimEnd('\') -ieq $Dir.TrimEnd('\') }
        if (-not $present) {
            $newPath = if ($entries.Count -gt 0) { ($entries + $Dir) -join ';' } else { $Dir }
            $key.SetValue('Path', $newPath, $kind)
        }
    } finally {
        $key.Close()
    }
    if (($env:Path -split ';') -notcontains $Dir) {
        $env:Path = "$env:Path;$Dir"
    }
}

if ($Version -and -not (Test-ValidVersion $Version)) {
    Fail "invalid -Version value: $Version (expected X.Y.Z)"
}

New-Item -ItemType Directory -Force -Path $InstallRoot | Out-Null
$stagingRoot = Join-Path $InstallRoot ("staging." + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $stagingRoot | Out-Null

try {
    if ($Version) {
        $manifestUrl = "$ManifestBaseUrl/$Version.properties"
    } else {
        $manifestUrl = "$ManifestBaseUrl/latest.properties"
    }
    $manifestFile = Join-Path $stagingRoot 'manifest.properties'
    Save-RemoteFile -Url $manifestUrl -OutFile $manifestFile

    $manifest = Read-Manifest -Path $manifestFile
    $resolvedVersion = $manifest['version']

    if ($Version -and $Version -ne $resolvedVersion) {
        Fail "manifest version ($resolvedVersion) does not match requested version ($Version)"
    }

    $archiveUrl = "$MavenBaseUrl/$resolvedVersion/camel-launcher-$resolvedVersion-bin.zip"
    $archiveFile = Join-Path $stagingRoot "camel-launcher-$resolvedVersion-bin.zip"
    Save-RemoteFile -Url $archiveUrl -OutFile $archiveFile

    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $archiveFile).Hash.ToLowerInvariant()
    if ($actualHash -ne $manifest['zip_sha256']) {
        Fail "checksum mismatch for downloaded archive"
    }

    Test-ArchiveEntry -ArchivePath $archiveFile -Version $resolvedVersion

    $extractDir = Join-Path $stagingRoot 'extract'
    Expand-Archive -LiteralPath $archiveFile -DestinationPath $extractDir -Force

    $stagedRoot = Join-Path $extractDir "camel-launcher-$resolvedVersion"
    $stagedLauncher = Join-Path $stagedRoot 'bin\camel.bat'
    Test-StagedLauncher -LauncherPath $stagedLauncher

    Set-CamelShim -Version $resolvedVersion -StagedRoot $stagedRoot
    Add-UserPath -Dir $BinDir

    Write-Host "Installed Camel CLI $resolvedVersion to $(Join-Path $DataRoot $resolvedVersion)"
} finally {
    if (Test-Path -LiteralPath $stagingRoot) {
        Remove-Item -LiteralPath $stagingRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}
