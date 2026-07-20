<!--
    Licensed to the Apache Software Foundation (ASF) under one or more
    contributor license agreements.  See the NOTICE file distributed with
    this work for additional information regarding copyright ownership.
    The ASF licenses this file to You under the Apache License, Version 2.0
    (the "License"); you may not use this file except in compliance with
    the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
-->

<!--
    As of the pinned JReleaser plugin version, the SDKMAN packager has no local template
    extension point. It only exposes `packagers.sdkman.releaseNotesUrl`, and candidate
    publication happens through SDKMAN's Vendor API at publish time, not `prepare`.
    This file is not consumed by JReleaser; it holds the required note text for the
    `publish` implementation to include when composing the SDKMAN release announcement.
-->

Apache Camel CLI requires Java 17 or newer. If you do not already have a
compatible Java, "sdk install java" provides one and SDKMAN will export
JAVA_HOME for it; any other Java 17+ runtime works equally well. The bin/camel
launcher discovers Java via JAVACMD, JAVA_HOME, PATH, then CAMEL_FALLBACK_JAVA,
and prints a diagnostic listing these sources if none qualify.
