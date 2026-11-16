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
package org.apache.camel.component.undertow;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.undertow.Undertow;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentManager;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UndertowServlet61Test extends BaseUndertowTest {

    @Test
    void servlet61AndCamelConsumerCoexist() throws Exception {
        DeploymentManager deployment = Servlets.newContainer().addDeployment(Servlets.deployment()
                .setClassLoader(getClass().getClassLoader())
                .setContextPath("/")
                .setDeploymentName("servlet61")
                .addServlet(Servlets.servlet("echo", AsyncEchoServlet.class)
                        .addMapping("/servlet").setAsyncSupported(true)));
        deployment.deploy();
        Undertow server = null;
        try {
            server = Undertow.builder().addHttpListener(getPort2(), "localhost")
                    .setHandler(deployment.start()).build();
            server.start();

            assertEquals(6, deployment.getDeployment().getServletContext().getMajorVersion());
            assertEquals(1, deployment.getDeployment().getServletContext().getMinorVersion());
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> servlet = client.send(HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + getPort2() + "/servlet"))
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString("Hello Servlet"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, servlet.statusCode());
            assertEquals("Hello Servlet", servlet.body());

            HttpResponse<String> camel = client.send(HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + getPort() + "/camel"))
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString("Hello Camel"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, camel.statusCode());
            assertEquals("Hello Camel", camel.body());
        } finally {
            if (server != null) {
                server.stop();
            }
            deployment.stop();
            deployment.undeploy();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("undertow:http://localhost:{{port}}/camel")
                        .convertBodyTo(String.class);
            }
        };
    }

    public static class AsyncEchoServlet extends HttpServlet {

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) {
            AsyncContext async = request.startAsync();
            async.start(() -> {
                try {
                    response.setCharacterEncoding(StandardCharsets.UTF_8);
                    response.getOutputStream().write(ByteBuffer.wrap(request.getInputStream().readAllBytes()));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } finally {
                    async.complete();
                }
            });
        }
    }
}
