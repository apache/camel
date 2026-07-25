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

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstallScriptFetcherTest {

    private static final String SH_HASH = "a".repeat(64);
    private static final String PS1_HASH = "b".repeat(64);

    @Test
    void parsesValidChecksumFile() {
        String content = "install_sh_sha256=" + SH_HASH + "\ninstall_ps1_sha256=" + PS1_HASH + "\n";

        var checksums = InstallScriptFetcher.parseChecksums(content.getBytes(StandardCharsets.UTF_8));

        assertThat(checksums).containsEntry("install_sh_sha256", SH_HASH).containsEntry("install_ps1_sha256", PS1_HASH);
    }

    @Test
    void rejectsWrongLineCount() {
        String content = "install_sh_sha256=" + SH_HASH + "\n";

        assertThatThrownBy(() -> InstallScriptFetcher.parseChecksums(content.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(SelfUpdateException.class);
    }

    @Test
    void rejectsUnknownKey() {
        String content = "install_sh_sha256=" + SH_HASH + "\ninstall_exe_sha256=" + PS1_HASH + "\n";

        assertThatThrownBy(() -> InstallScriptFetcher.parseChecksums(content.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(SelfUpdateException.class);
    }

    @Test
    void rejectsBadHexChecksum() {
        String content = "install_sh_sha256=not-hex\ninstall_ps1_sha256=" + PS1_HASH + "\n";

        assertThatThrownBy(() -> InstallScriptFetcher.parseChecksums(content.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(SelfUpdateException.class);
    }

    @Test
    void fromEnvironmentUsesProductionDefaultWhenUnset() {
        InstallScriptFetcher fetcher = InstallScriptFetcher.fromEnvironment();

        assertThat(fetcher.installBaseUrl()).isEqualTo("https://camel.apache.org");
    }
}
