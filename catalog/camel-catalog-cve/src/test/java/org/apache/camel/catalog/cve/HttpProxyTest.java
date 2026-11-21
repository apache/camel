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
package org.apache.camel.catalog.cve;

import java.net.Proxy;
import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-27342: the proxy of the CVE audit, from the environment variables of a CI runner or the Maven settings.
 */
class HttpProxyTest {

    @Test
    void parsesTheProxyUrl() {
        HttpProxy proxy = HttpProxy.parse("http://proxy.corp.com:3128", null);
        assertThat(proxy.host()).isEqualTo("proxy.corp.com");
        assertThat(proxy.port()).isEqualTo(3128);
        assertThat(proxy.hasCredentials()).isFalse();
        assertThat(proxy.authenticator()).isNull();

        // no scheme, and port 80 by default
        assertThat(HttpProxy.parse("proxy.corp.com", null).port()).isEqualTo(80);
        assertThat(HttpProxy.parse("  ", null)).isNull();
        assertThat(HttpProxy.parse(null, null)).isNull();
    }

    @Test
    void parsesTheUsernameAndPassword() {
        // URL encoded: the password is p@ss+w:rd
        HttpProxy proxy = HttpProxy.parse("http://scott:p%40ss+w%3Ard@proxy:8080", null);
        assertThat(proxy.username()).isEqualTo("scott");
        assertThat(proxy.password()).isEqualTo("p@ss+w:rd");
        assertThat(proxy.hasCredentials()).isTrue();
        assertThat(proxy.authenticator()).isNotNull();
        // the password is never printed
        assertThat(proxy).hasToString("scott@proxy:8080");
    }

    @Test
    void readsTheEnvironmentOfCiRunners() {
        HttpProxy proxy = HttpProxy.fromEnvironment(Map.of(
                "https_proxy", "http://secure:8443",
                "HTTP_PROXY", "http://plain:8080",
                "no_proxy", "localhost, .corp.com"));
        assertThat(proxy.host()).isEqualTo("secure");
        assertThat(proxy.nonProxyHosts()).containsExactly("localhost", ".corp.com");

        assertThat(HttpProxy.fromEnvironment(Map.of("HTTP_PROXY", "http://plain:8080")).host()).isEqualTo("plain");
        assertThat(HttpProxy.fromEnvironment(Map.of())).isNull();
    }

    @Test
    void bypassesTheNonProxyHosts() {
        // as NO_PROXY and the Maven settings write them
        HttpProxy proxy
                = new HttpProxy("proxy", 8080, null, null, HttpProxy.splitHosts("localhost|*.corp.com,.intra,nexus.lan"));
        assertThat(proxy.bypasses("localhost")).isTrue();
        assertThat(proxy.bypasses("repo.corp.com")).isTrue();
        assertThat(proxy.bypasses("corp.com")).isTrue();
        assertThat(proxy.bypasses("a.b.intra")).isTrue();
        assertThat(proxy.bypasses("nexus.lan")).isTrue();
        assertThat(proxy.bypasses("eu.nexus.lan")).isTrue();
        assertThat(proxy.bypasses("api.osv.dev")).isFalse();
        assertThat(proxy.bypasses("notcorp.com")).isFalse();

        assertThat(proxy.selector().select(URI.create("https://api.osv.dev/v1/querybatch")))
                .singleElement().extracting(Proxy::type).isEqualTo(Proxy.Type.HTTP);
        assertThat(proxy.selector().select(URI.create("https://nexus.lan/osv/all.zip")))
                .containsExactly(Proxy.NO_PROXY);

        HttpProxy all = new HttpProxy("proxy", 8080, null, null, List.of("*"));
        assertThat(all.bypasses("api.osv.dev")).isTrue();
    }
}
