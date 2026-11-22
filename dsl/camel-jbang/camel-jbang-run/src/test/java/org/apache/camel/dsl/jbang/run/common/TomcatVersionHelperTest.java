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
package org.apache.camel.dsl.jbang.run.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TomcatVersionHelperTest {

    @Test
    void mapsSpringBootVersionsToTomcatVersions() {
        assertThat(TomcatVersionHelper.tomcatVersion("3.5.0")).isEqualTo("10.1");
        assertThat(TomcatVersionHelper.tomcatVersion("4.2.0")).isEqualTo("11.0");
    }

    @Test
    void usesTomcat11WhenSpringBootVersionIsNotSpecified() {
        assertThat(TomcatVersionHelper.tomcatVersion(null)).isEqualTo("11.0");
    }

    @Test
    void usesTheTomcatMirrorByDefault() {
        assertThat(TomcatVersionHelper.defaultBaseImage("4.2.0", "21"))
                .isEqualTo("mirror.gcr.io/library/tomcat:11.0-jdk21-temurin");
    }

    @Test
    void requiresAnExplicitCompatibleCamelVersionForSpringBoot3WhenUnset() {
        assertThat(TomcatVersionHelper.compatibilityError("3.5.0", null))
                .contains("--camel-spring-boot-version=4.18.1")
                .contains("--camel-version=4.18.1");

        assertThat(TomcatVersionHelper.compatibilityError("3.5.0", " "))
                .contains("--camel-spring-boot-version=4.18.1");
    }

    @Test
    void validatesSpringBootAndCamelSpringBootCompatibility() {
        assertThat(TomcatVersionHelper.compatibilityError("3.5.0", "4.24.0"))
                .contains("earlier than 4.19.0");

        assertThat(TomcatVersionHelper.compatibilityError("3.5.0", "4.18.1"))
                .isNull();

        assertThat(TomcatVersionHelper.compatibilityError("4.2.0", "4.24.0"))
                .isNull();

        assertThat(TomcatVersionHelper.compatibilityError("4.2.0", "4.18.1"))
                .contains("4.19.0 or later");
    }
}
