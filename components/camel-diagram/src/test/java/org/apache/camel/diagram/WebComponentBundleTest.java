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
package org.apache.camel.diagram;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebComponentBundleTest {

    @Test
    void bundledJsExistsInClasspath() {
        URL url = getClass().getClassLoader()
                .getResource("META-INF/resources/camel/diagram/camel-route-diagram.js");
        assertThat(url).as("camel-route-diagram.js must be bundled").isNotNull();
    }

    @Test
    void bundledJsIsNonEmpty() throws IOException {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("META-INF/resources/camel/diagram/camel-route-diagram.js")) {
            assertThat(is).isNotNull();
            assertThat(is.readAllBytes().length).isGreaterThan(1000);
        }
    }

    @Test
    void bundledJsContainsCustomElementRegistration() throws IOException {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("META-INF/resources/camel/diagram/camel-route-diagram.js")) {
            assertThat(is).isNotNull();
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content)
                    .as("bundle must register the camel-route-diagram custom element")
                    .contains("customElements.define")
                    .contains("camel-route-diagram");
        }
    }

    @Test
    void thirdPartyNoticesMentionsLucide() throws IOException {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("META-INF/resources/camel/diagram/THIRD-PARTY-NOTICES.txt")) {
            assertThat(is).isNotNull();
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content)
                    .as("THIRD-PARTY-NOTICES.txt must attribute Lucide with ISC license")
                    .contains("Lucide")
                    .contains("ISC");
        }
    }
}
