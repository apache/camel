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
package org.apache.camel.component.sap.netweaver;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NetWeaverProducerTest extends CamelTestSupport {

    private HttpServer server;
    private volatile String credentials;
    private volatile URI requestUri;

    @Override
    public void setupResources() throws Exception {
        server = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost")
                .register("*", (request, response, context) -> {
                    Header auth = request.getFirstHeader(HttpHeaders.AUTHORIZATION);
                    if (auth == null) {
                        // ask for basic authentication, so the client sends the credentials
                        response.setCode(HttpStatus.SC_UNAUTHORIZED);
                        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"netweaver\"");
                        return;
                    }
                    credentials = new String(
                            Base64.getDecoder().decode(auth.getValue().substring("Basic ".length())), StandardCharsets.UTF_8);
                    try {
                        requestUri = request.getUri();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                    response.setCode(HttpStatus.SC_OK);
                    response.setEntity(new StringEntity("{\"d\":{\"Name\":\"Camel\"}}", ContentType.APPLICATION_JSON));
                })
                .create();
        server.start();
    }

    @Override
    public void cleanupResources() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testUrlWithQueryParameters() {
        String base = "http://localhost:" + server.getLocalPort() + "/odata";
        NetWeaverEndpoint endpoint = context.getEndpoint(
                "sap-netweaver:" + base + "?username=camel&password=secret", NetWeaverEndpoint.class);
        endpoint.setUrl(base + "?sap-client=100");

        Object body = template.requestBodyAndHeader(endpoint, null, NetWeaverConstants.COMMAND, "Flights");

        assertThat(body).isEqualTo(Map.of("Name", "Camel"));
        assertThat(requestUri.getPath()).isEqualTo("/odata/Flights");
        assertThat(requestUri.getQuery()).isEqualTo("sap-client=100");
        assertThat(credentials).isEqualTo("camel:secret");
    }

    @Test
    public void testPasswordWithSpecialCharacters() {
        String base = "http://localhost:" + server.getLocalPort() + "/odata";

        Object body = template.requestBodyAndHeader(
                "sap-netweaver:" + base + "?username=camel&password=RAW(a&b=c+d)", null, NetWeaverConstants.COMMAND,
                "Flights");

        assertThat(body).isEqualTo(Map.of("Name", "Camel"));
        assertThat(requestUri.getPath()).isEqualTo("/odata/Flights");
        assertThat(requestUri.getQuery()).isNull();
        assertThat(credentials).isEqualTo("camel:a&b=c+d");
    }
}
