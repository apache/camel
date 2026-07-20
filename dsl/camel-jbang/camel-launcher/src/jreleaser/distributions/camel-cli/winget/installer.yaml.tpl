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
# yaml-language-server: $schema=https://aka.ms/winget-manifest.installer.1.12.0.schema.json
#
# Overrides the default JReleaser java-binary/winget template (which declares a single
# Architecture: neutral entry) to declare per-architecture installers for x64 and arm64.
# The camel-launcher distribution zip ships both camel-x64.exe and camel-arm64.exe in
# bin/ (see src/main/assembly/bin.xml), so each architecture entry selects the correct
# native exe via NestedInstallerFiles/RelativeFilePath.

PackageIdentifier: {{wingetPackageIdentifier}}
PackageVersion: {{wingetPackageVersion}}
ReleaseDate: {{wingetReleaseDate}}
Installers:
  - Architecture: x64
    InstallerUrl: {{distributionUrl}}
    InstallerSha256: {{distributionChecksumSha256}}
    InstallerType: zip
    NestedInstallerType: portable
    NestedInstallerFiles:
      - RelativeFilePath: {{distributionArtifactRootEntryName}}\bin\camel-x64.exe
        PortableCommandAlias: camel
    {{#wingetHasDependencies}}
    Dependencies:
      {{#wingetHasWindowsFeatures}}
      WindowsFeatures:
        {{#wingetWindowsFeatures}}- {{.}}{{/wingetWindowsFeatures}}
      {{/wingetHasWindowsFeatures}}
      {{#wingetHasWindowsLibraries}}
      WindowsLibraries:
        {{#wingetWindowsLibraries}}- {{.}}{{/wingetWindowsLibraries}}
      {{/wingetHasWindowsLibraries}}
      {{#wingetHasExternalDependencies}}
      ExternalDependencies:
        {{#wingetExternalDependencies}}- {{.}}{{/wingetExternalDependencies}}
      {{/wingetHasExternalDependencies}}
      {{#wingetHasPackageDependencies}}
      PackageDependencies:
        {{#wingetPackageDependencies}}
        - PackageIdentifier: {{packageIdentifier}}
          {{#minimumVersion}}MinimumVersion: {{.}}{{/minimumVersion}}
        {{/wingetPackageDependencies}}
      {{/wingetHasPackageDependencies}}
    {{/wingetHasDependencies}}
  - Architecture: arm64
    InstallerUrl: {{distributionUrl}}
    InstallerSha256: {{distributionChecksumSha256}}
    InstallerType: zip
    NestedInstallerType: portable
    NestedInstallerFiles:
      - RelativeFilePath: {{distributionArtifactRootEntryName}}\bin\camel-arm64.exe
        PortableCommandAlias: camel
    {{#wingetHasDependencies}}
    Dependencies:
      {{#wingetHasWindowsFeatures}}
      WindowsFeatures:
        {{#wingetWindowsFeatures}}- {{.}}{{/wingetWindowsFeatures}}
      {{/wingetHasWindowsFeatures}}
      {{#wingetHasWindowsLibraries}}
      WindowsLibraries:
        {{#wingetWindowsLibraries}}- {{.}}{{/wingetWindowsLibraries}}
      {{/wingetHasWindowsLibraries}}
      {{#wingetHasExternalDependencies}}
      ExternalDependencies:
        {{#wingetExternalDependencies}}- {{.}}{{/wingetExternalDependencies}}
      {{/wingetHasExternalDependencies}}
      {{#wingetHasPackageDependencies}}
      PackageDependencies:
        {{#wingetPackageDependencies}}
        - PackageIdentifier: {{packageIdentifier}}
          {{#minimumVersion}}MinimumVersion: {{.}}{{/minimumVersion}}
        {{/wingetPackageDependencies}}
      {{/wingetHasPackageDependencies}}
    {{/wingetHasDependencies}}
ManifestType: installer
ManifestVersion: 1.12.0
