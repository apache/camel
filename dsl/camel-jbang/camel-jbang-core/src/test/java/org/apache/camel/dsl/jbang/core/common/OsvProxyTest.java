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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.camel.catalog.cve.HttpProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-27342: the proxy of the CVE audit is the one of the Maven settings, else the one of HTTPS_PROXY.
 */
class OsvProxyTest {

    @TempDir
    Path dir;

    @Test
    void theMavenSettingsComeFirst() throws Exception {
        Path settings = dir.resolve("settings.xml");
        Files.writeString(settings, """
                <settings>
                  <localRepository>%s</localRepository>
                  <proxies>
                    <proxy>
                      <id>corp</id>
                      <active>true</active>
                      <protocol>http</protocol>
                      <host>proxy.corp.com</host>
                      <port>3128</port>
                      <username>scott</username>
                      <password>tiger</password>
                    </proxy>
                  </proxies>
                </settings>
                """.formatted(dir.resolve("repo")));

        HttpProxy proxy = OsvProxy.resolve(settings.toString(), null, Map.of("HTTPS_PROXY", "http://ci:8080"));

        assertThat(proxy).isEqualTo(new HttpProxy("proxy.corp.com", 3128, "scott", "tiger", null));
    }

    @Test
    void elseTheEnvironment() {
        HttpProxy proxy = OsvProxy.resolve("false", null, Map.of("HTTPS_PROXY", "http://ci:8080", "NO_PROXY", "localhost"));

        assertThat(proxy).isEqualTo(new HttpProxy("ci", 8080, null, null, List.of("localhost")));
        assertThat(OsvProxy.resolve("false", null, Map.of())).isNull();
    }
}
