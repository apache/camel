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
package org.apache.camel.dsl.jbang.core.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

class LauncherHelperTest {

    // jar:nested: — Spring Boot 3.2+/4.x loader
    // Real URL form: jar:nested:/outer.jar/!BOOT-INF/lib/inner.jar!/
    // The outer-jar boundary is /! (slash-bang), not !/ (bang-slash).

    @Test
    void parsesNestedJarUrlOnLinux() {
        String result = LauncherHelper.parseJarPath(
                "jar:nested:/home/user/camel-launcher-4.23.0.jar/!BOOT-INF/lib/camel-jbang-core.jar!/");
        assertThat(result).isEqualTo("/home/user/camel-launcher-4.23.0.jar");
    }

    @Test
    void parsesNestedJarUrlWithPercentEncodedSpaces() {
        String result = LauncherHelper.parseJarPath(
                "jar:nested:/home/user/my%20app/camel-launcher.jar/!BOOT-INF/lib/camel-jbang-core.jar!/");
        assertThat(result).isEqualTo("/home/user/my app/camel-launcher.jar");
    }

    @Test
    void parsesNestedJarUrlWithWindowsDriveLetter() {
        // Platform-neutral: verify the outer jar filename is extracted regardless of OS path format
        String result = LauncherHelper.parseJarPath(
                "jar:nested:/C:/Users/user/camel-launcher-4.23.0.jar/!BOOT-INF/lib/camel-jbang-core.jar!/");
        assertThat(result).isNotNull().endsWith("camel-launcher-4.23.0.jar");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void parsesNestedJarUrlWindowsDriveLetterStripsLeadingSlash() {
        // On Windows, Path.of(URI) strips the /C:/ prefix — verify java -jar can use the result
        String result = LauncherHelper.parseJarPath(
                "jar:nested:/C:/Users/user/camel-launcher-4.23.0.jar/!BOOT-INF/lib/camel-jbang-core.jar!/");
        assertThat(result).doesNotStartWith("/C:");
    }

    // jar:file: — Spring Boot 2.x / shade plugin

    @Test
    void parsesJarFileUrlOnLinux() {
        String result = LauncherHelper.parseJarPath(
                "jar:file:/home/user/camel-launcher-4.23.0.jar!/BOOT-INF/classes/");
        assertThat(result).isEqualTo("/home/user/camel-launcher-4.23.0.jar");
    }

    @Test
    void parsesJarFileUrlWithPercentEncodedSpaces() {
        String result = LauncherHelper.parseJarPath(
                "jar:file:/home/user/my%20tools/camel-launcher.jar!/BOOT-INF/classes/");
        assertThat(result).isEqualTo("/home/user/my tools/camel-launcher.jar");
    }

    // file: — direct file URL

    @Test
    void parsesFileUrl() {
        String result = LauncherHelper.parseJarPath("file:/home/user/camel-launcher-4.23.0.jar");
        assertThat(result).isEqualTo("/home/user/camel-launcher-4.23.0.jar");
    }

    @Test
    void parsesFileUrlWithPercentEncodedSpaces() {
        String result = LauncherHelper.parseJarPath("file:/home/user/my%20tools/camel-launcher.jar");
        assertThat(result).isEqualTo("/home/user/my tools/camel-launcher.jar");
    }

    // edge cases

    @Test
    void returnsNullForUnknownScheme() {
        assertThat(LauncherHelper.parseJarPath("http://example.com/camel-launcher.jar")).isNull();
    }

    @Test
    void returnsNullForNestedJarUrlWithoutBoundarySeparator() {
        // Cannot determine outer JAR boundary without /!
        assertThat(LauncherHelper.parseJarPath("jar:nested:/home/user/camel-launcher.jar")).isNull();
    }
}
