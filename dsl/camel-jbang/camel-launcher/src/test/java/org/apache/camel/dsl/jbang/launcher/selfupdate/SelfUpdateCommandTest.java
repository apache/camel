/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.dsl.jbang.launcher.selfupdate;

import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SelfUpdateCommandTest {

    private static String installerCommand(String version) {
        return FileUtil.isWindows()
                ? "irm https://camel.apache.org/install.ps1 -OutFile install.ps1; .\\install.ps1 -Version " + version
                : "curl -fsSL https://camel.apache.org/install.sh | sh -s -- --version " + version;
    }

    @Test
    void ltsPinRefusalSuggestsReRunningTheInstallerWithAnExactVersion() {
        assertThat(SelfUpdateCommand.selectorPinRefusal(Pin.parse("LTS"), "4.23.0"))
                .isEqualTo("this install is pinned to LTS - to pin an exact version instead, re-run the installer"
                           + " with that version: " + installerCommand("4.23.0"));
    }

    @Test
    void linePinRefusalNamesTheReleaseLine() {
        assertThat(SelfUpdateCommand.selectorPinRefusal(Pin.parse("4.22"), "4.22.1"))
                .isEqualTo("this install is pinned to the 4.22 release line - to pin an exact version instead,"
                           + " re-run the installer with that version: " + installerCommand("4.22.1"))
                .doesNotContain("pinned-version");
    }
}
