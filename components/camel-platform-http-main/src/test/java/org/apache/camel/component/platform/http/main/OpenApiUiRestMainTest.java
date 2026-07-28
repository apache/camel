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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiUiRestMainTest {

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();

    private Main main;

    @BeforeEach
    void setUp() {
        Properties properties = new Properties();
        properties.setProperty("camel.server.enabled", "true");
        properties.setProperty("camel.server.port", Integer.toString(port.getPort()));
        properties.setProperty("camel.management.enabled", "true");
        properties.setProperty("camel.management.port", Integer.toString(port.getPort()));
        properties.setProperty("camel.management.openapiUiEnabled", "true");
        properties.setProperty("camel.rest.component", "platform-http");
        properties.setProperty("camel.rest.apiContextPath", OpenApiUiSupport.DEFAULT_SPEC_PATH);

        main = new Main();
        main.setOverrideProperties(properties);
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().apiProperty("api.title", "Sample API").apiProperty("api.version", "1.0.0");
                rest("/demo").get("/ping").to("direct:ping");
                from("direct:ping").setBody(constant("pong"));
            }
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        if (main != null) {
            main.stop();
        }
    }

    @Test
    void restOpenApiDocumentAndUiAreAvailable() throws Exception {
        main.start();

        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> spec = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port.getPort() + OpenApiUiSupport.DEFAULT_SPEC_PATH))
                .header("Accept", "application/json")
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(spec.statusCode()).isEqualTo(200);
        assertThat(spec.body()).contains("\"openapi\"");
        assertThat(spec.body()).contains("/demo/ping");

        HttpResponse<String> ui = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port.getPort() + OpenApiUiSupport.OPENAPI_UI_PATH))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(ui.statusCode()).isEqualTo(200);
        assertThat(ui.body()).contains("SwaggerUIBundle");
    }
}
