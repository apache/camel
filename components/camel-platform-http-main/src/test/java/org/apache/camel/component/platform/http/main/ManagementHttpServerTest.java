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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagementHttpServerTest {

    private CamelContext camelContext;

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();

    @Test
    public void statusIsNotSatisfied() throws IOException, InterruptedException {
        ManagementHttpServer server = new ManagementHttpServer();

        camelContext = new DefaultCamelContext();
        server.setCamelContext(camelContext);

        camelContext.getRegistry().bind("fake", new MainHttpFakeHealthCheck());

        server.setHost("0.0.0.0");
        server.setPort(port.getPort());
        server.setPath("/");

        server.setHealthCheckEnabled(true);
        server.setHealthPath("/q/health");
        server.start();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port.getPort() + "/q/health/ready"))
                .build();

        HttpResponse<String> response = HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(503, response.statusCode());

    }

    @Test
    public void downloadListsApplicationResources() throws Exception {
        ManagementHttpServer server = new ManagementHttpServer();

        camelContext = new DefaultCamelContext();
        // files that camel run adds to the classpath, only files inside the project directory are included
        String clazz = "target/classes/" + ManagementHttpServer.class.getName().replace('.', '/') + ".class";
        camelContext.getPropertiesComponent().addInitialProperty("camel.jbang.classpathFiles",
                "pom.xml,../pom.xml," + clazz);
        server.setCamelContext(camelContext);

        server.setHost("0.0.0.0");
        server.setPort(port.getPort());
        server.setPath("/");
        server.setDownloadEnabled(true);
        server.start();
        try {
            HttpResponse<String> response = get("/q/download?classpath=true");
            assertEquals(200, response.statusCode());
            List<String> names = response.body().lines().toList();
            // resources from the classpath
            assertTrue(names.contains("log4j2.properties"), names.toString());
            assertTrue(names.contains("basic-auth.properties"), names.toString());
            // files from the project directory
            assertTrue(names.contains("pom.xml"), names.toString());
            assertFalse(names.contains("../pom.xml"), names.toString());
            assertFalse(names.contains(clazz), names.toString());
            // no classes or content of dependency JARs
            assertTrue(names.stream().noneMatch(n -> n.endsWith(".class")), names.toString());
            assertTrue(names.stream().noneMatch(n -> n.startsWith("META-INF/")), names.toString());

            response = get("/q/download/*.jks?classpath=true");
            assertEquals("test-camel-main-auth-jwt.jks", response.body());

            response = get("/q/download/pom.xml");
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("<artifactId>camel-platform-http-main</artifactId>"));

            // classes cannot be downloaded
            response = get("/q/download/" + clazz);
            assertEquals(204, response.statusCode());
        } finally {
            server.stop();
        }
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port.getPort() + path))
                .build();
        return HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
    }

}
