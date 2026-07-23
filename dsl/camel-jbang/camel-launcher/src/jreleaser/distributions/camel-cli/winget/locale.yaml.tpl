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
# yaml-language-server: $schema=https://aka.ms/winget-manifest.defaultLocale.1.12.0.schema.json

PackageIdentifier: {{wingetPackageIdentifier}}
PackageVersion: {{wingetPackageVersion}}
PackageLocale: {{wingetPackageLocale}}
Publisher: {{wingetPackagePublisher}}
PublisherUrl: {{wingetPublisherUrl}}
PublisherSupportUrl: {{wingetPublisherSupportUrl}}
Author: {{wingetAuthor}}
PackageName: {{wingetPackageName}}
PackageUrl: {{wingetPackageUrl}}
License: {{projectLicense}}
LicenseUrl: {{projectLinkLicense}}
Copyright: {{projectCopyright}}
ShortDescription: {{projectDescription}}
Description: {{projectLongDescription}}
Moniker: {{wingetMoniker}}
{{#wingetHasTags}}
Tags:
  {{#wingetTags}}
  - {{.}}
  {{/wingetTags}}
{{/wingetHasTags}}
ReleaseNotesUrl: {{releaseNotesUrl}}
ManifestType: {{wingetManifestType}}
ManifestVersion: 1.12.0
