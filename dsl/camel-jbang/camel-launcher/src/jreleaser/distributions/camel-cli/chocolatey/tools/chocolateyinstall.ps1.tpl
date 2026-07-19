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

# {{jreleaserCreationStamp}}
$tools = Split-Path $MyInvocation.MyCommand.Definition
$package = Split-Path $tools
$app_home = Join-Path $package '{{distributionArtifactRootEntryName}}'
$app_exe = Join-Path $app_home 'bin/{{distributionExecutableWindows}}'

Install-ChocolateyZipPackage `
    -PackageName '{{chocolateyPackageName}}' `
    -Url '{{distributionUrl}}' `
    -Checksum '{{distributionChecksumSha256}}' `
    -ChecksumType 'sha256' `
    -UnzipLocation $package

# Remove native bootstrap executables shipped in the distribution zip; Chocolatey
# uses camel.bat (shimmed via Install-BinFile below) and does not support
# per-architecture exe selection. Native ARM64 support in Chocolatey is pending:
# https://github.com/chocolatey/choco/issues/1803
$bin = Join-Path $app_home 'bin'
foreach ($native_exe in @('camel-x64.exe', 'camel-arm64.exe')) {
    $native_path = Join-Path $bin $native_exe
    if (Test-Path -LiteralPath $native_path) {
        Remove-Item -LiteralPath $native_path -Force -ErrorAction Stop
    }
}

Install-BinFile -Name '{{distributionExecutableName}}' -Path $app_exe
