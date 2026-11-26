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
package org.apache.camel.component.platform.http.main;

import java.time.Duration;

import io.vertx.core.http.CookieSameSite;
import org.apache.camel.CamelContext;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServerConfiguration;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.main.HttpServerConfigurationProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultMainHttpServerFactoryTest {

    private final CamelContext context = new DefaultCamelContext();
    private final DefaultMainHttpServerFactory factory = new DefaultMainHttpServerFactory();

    @AfterEach
    void stopContext() {
        context.stop();
    }

    private VertxPlatformHttpServerConfiguration newServer(HttpServerConfigurationProperties configuration) {
        MainHttpServer server = (MainHttpServer) factory.newHttpServer(context, configuration);
        return server.getConfiguration();
    }

    @Test
    void defaultsKeepCorsAndSessionDisabled() {
        VertxPlatformHttpServerConfiguration server = newServer(new HttpServerConfigurationProperties(null));

        assertThat(server.getCors().isEnabled()).isFalse();
        assertThat(server.getSessionConfig().isEnabled()).isFalse();
        assertThat(server.getBodyHandler().isMergeFormAttributes()).isTrue();
        assertThat(server.getBodyHandler().isDeleteUploadedFilesOnEnd()).isTrue();
        assertThat(server.getBodyHandler().isPreallocateBodyBuffer()).isTrue();
    }

    @Test
    void bodyHandlerFlagsAreMapped() {
        VertxPlatformHttpServerConfiguration server = newServer(new HttpServerConfigurationProperties(null)
                .withMergeFormAttributes(false)
                .withDeleteUploadedFilesOnEnd(false)
                .withPreallocateBodyBuffer(false));

        assertThat(server.getBodyHandler().isMergeFormAttributes()).isFalse();
        assertThat(server.getBodyHandler().isDeleteUploadedFilesOnEnd()).isFalse();
        assertThat(server.getBodyHandler().isPreallocateBodyBuffer()).isFalse();
    }

    @Test
    void corsOptionsAreMapped() {
        VertxPlatformHttpServerConfiguration.Cors cors = newServer(new HttpServerConfigurationProperties(null)
                .withCorsEnabled(true)
                .withCorsOrigins(" https://a.example , https://b.example,")
                .withCorsMethods("GET,PUT")
                .withCorsHeaders("X-A")
                .withCorsExposedHeaders("X-B,X-C")
                .withCorsAccessControlMaxAge(60))
                .getCors();

        assertThat(cors.isEnabled()).isTrue();
        assertThat(cors.getOrigins()).containsExactly("https://a.example", "https://b.example");
        assertThat(cors.getMethods()).containsExactly("GET", "PUT");
        assertThat(cors.getHeaders()).containsExactly("X-A");
        assertThat(cors.getExposedHeaders()).containsExactly("X-B", "X-C");
        assertThat(cors.getAccessControlMaxAge()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void corsWithoutListsAllowsWhatIsRequested() {
        VertxPlatformHttpServerConfiguration.Cors cors = newServer(new HttpServerConfigurationProperties(null)
                .withCorsEnabled(true))
                .getCors();

        assertThat(cors.isEnabled()).isTrue();
        assertThat(cors.getOrigins()).isEmpty();
        assertThat(cors.getMethods()).isEmpty();
        assertThat(cors.getHeaders()).isEmpty();
        assertThat(cors.getExposedHeaders()).isEmpty();
        assertThat(cors.getAccessControlMaxAge()).isNull();
    }

    @Test
    void sessionOptionsAreMapped() {
        VertxPlatformHttpServerConfiguration.SessionConfig session = newServer(new HttpServerConfigurationProperties(null)
                .withSessionEnabled(true)
                .withSessionStoreType("Clustered")
                .withSessionCookieName("sid")
                .withSessionCookiePath("/app")
                .withSessionTimeout(5000)
                .withSessionCookieSecure(true)
                .withSessionCookieHttpOnly(false)
                .withSessionCookieSameSite("none")
                .withSessionIdMinLength(32))
                .getSessionConfig();

        assertThat(session.isEnabled()).isTrue();
        assertThat(session.getStoreType()).isEqualTo(VertxPlatformHttpServerConfiguration.SessionStoreType.CLUSTERED);
        assertThat(session.getSessionCookieName()).isEqualTo("sid");
        assertThat(session.getSessionCookiePath()).isEqualTo("/app");
        assertThat(session.getSessionTimeOut()).isEqualTo(5000);
        assertThat(session.isCookieSecure()).isTrue();
        assertThat(session.isCookieHttpOnly()).isFalse();
        assertThat(session.getCookieSameSite()).isEqualTo(CookieSameSite.NONE);
        assertThat(session.getSessionIdMinLength()).isEqualTo(32);
    }

    @Test
    void sessionDefaults() {
        VertxPlatformHttpServerConfiguration.SessionConfig session = newServer(new HttpServerConfigurationProperties(null)
                .withSessionEnabled(true))
                .getSessionConfig();

        assertThat(session.getStoreType()).isEqualTo(VertxPlatformHttpServerConfiguration.SessionStoreType.LOCAL);
        assertThat(session.getSessionCookieName()).isEqualTo("vertx-web.session");
        assertThat(session.getSessionCookiePath()).isEqualTo("/");
        assertThat(session.getSessionTimeOut()).isEqualTo(1800000);
        assertThat(session.isCookieSecure()).isFalse();
        assertThat(session.isCookieHttpOnly()).isTrue();
        assertThat(session.getCookieSameSite()).isEqualTo(CookieSameSite.STRICT);
        assertThat(session.getSessionIdMinLength()).isEqualTo(16);
    }

    @Test
    void invalidSameSiteFails() {
        HttpServerConfigurationProperties configuration = new HttpServerConfigurationProperties(null)
                .withSessionEnabled(true)
                .withSessionCookieSameSite("sometimes");

        assertThatThrownBy(() -> newServer(configuration))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("camel.server.sessionCookieSameSite")
                .hasMessageContaining("none,strict,lax");
    }
}
