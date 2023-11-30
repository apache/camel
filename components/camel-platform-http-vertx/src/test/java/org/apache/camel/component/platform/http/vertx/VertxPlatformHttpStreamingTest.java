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
package org.apache.camel.component.platform.http.vertx;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import io.restassured.http.ContentType;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class VertxPlatformHttpStreamingTest {

    @Test
    void testStreamingWithStringRequestAndResponseBody() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/streaming?useStreaming=true")
                            .transform().simple("Hello ${body}");
                }
            });

            context.start();

            String requestBody = "Vert.x Platform HTTP";
            given()
                    .body(requestBody)
                    .post("/streaming")
                    .then()
                    .statusCode(200)
                    .body(is("Hello " + requestBody));
        } finally {
            context.stop();
        }
    }

    @Test
    void testStreamingWithFileRequestAndResponseBody() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();
        String content = "Hello World";
        Path testFile = Files.createTempFile("platform-http-testing", "txt");
        Files.writeString(testFile, content);

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/streaming?useStreaming=true")
                            .log("Done processing request");
                }
            });

            context.start();

            given()
                    .body(testFile.toFile())
                    .post("/streaming")
                    .then()
                    .statusCode(200)
                    .body(is(content));
        } finally {
            context.stop();
            Files.deleteIfExists(testFile);
        }
    }

    @Test
    void testStreamingWithFormUrlEncodedBody() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/streaming?useStreaming=true")
                            .setBody().simple("foo = ${header.foo}");
                }
            });

            context.start();

            given()
                    .contentType(ContentType.URLENC)
                    .formParam("foo", "bar")
                    .post("/streaming")
                    .then()
                    .statusCode(200)
                    .body(is("foo = bar"));
        } finally {
            context.stop();
        }
    }

    @Test
    void testStreamingWithMultiPartRequestRejected() throws Exception {
        String content = "Hello World";
        Path testFile = Files.createTempFile("platform-http-testing", "txt");
        Files.writeString(testFile, content);

        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext(configuration -> {
            VertxPlatformHttpServerConfiguration.BodyHandler bodyHandler
                    = new VertxPlatformHttpServerConfiguration.BodyHandler();
            // turn on file uploads
            bodyHandler.setHandleFileUploads(true);
            bodyHandler.setUploadsDirectory(testFile.toFile().getParent());
            configuration.setBodyHandler(bodyHandler);
        });

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/streaming?useStreaming=true")
                            .setBody().constant("multipart request should have been rejected");
                }
            });

            context.start();

            given()
                    .multiPart(testFile.toFile())
                    .post("/streaming")
                    .then()
                    .statusCode(500);
        } finally {
            context.stop();
            Files.deleteIfExists(testFile);
        }
    }

    @Test
    void testStreamingWithSpecificEncoding() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();
        Path input = Files.createTempFile("platform-http-input", "dat");
        Path output = Files.createTempFile("platform-http-output", "dat");

        String fileContent = "Content with special character ð";
        Files.writeString(input, fileContent, StandardCharsets.ISO_8859_1);

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/streaming?useStreaming=true")
                            .log("Done echoing back request body as response body");
                }
            });

            context.start();

            InputStream response = given()
                    .body(new FileInputStream(input.toFile()))
                    .post("/streaming")
                    .then()
                    .statusCode(200)
                    .extract()
                    .body()
                    .asInputStream();

            try (FileOutputStream fos = new FileOutputStream(output.toFile())) {
                IOHelper.copy(response, fos);
            }

            assertEquals(fileContent, Files.readString(output, StandardCharsets.ISO_8859_1));
        } finally {
            context.stop();
        }
    }

    @Test
    void testStreamingWithClosedInputStreamResponse() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/streaming?useStreaming=true")
                            .process(new Processor() {
                                @Override
                                public void process(Exchange exchange) throws Exception {
                                    // Simulate an error processing an input stream by closing it ahead of the response being written
                                    // Verifies the response promise.fail is called correctly
                                    InputStream stream = getClass().getResourceAsStream("/authentication/auth.properties");
                                    if (stream != null) {
                                        stream.close();
                                    }
                                    exchange.getMessage().setBody(stream);
                                }
                            });
                }
            });

            context.start();

            given()
                    .get("/streaming")
                    .then()
                    .statusCode(500);
        } finally {
            context.stop();
        }
    }

    @Test
    void testStreamingWithUnconvertableResponseType() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/streaming?useStreaming=true")
                            .process(new Processor() {
                                @Override
                                public void process(Exchange exchange) {
                                    // Force a type conversion exception and verify the response promise.fail is called correctly
                                    exchange.getMessage().setBody(new TestBean());
                                }
                            });
                }
            });

            context.start();

            given()
                    .get("/streaming")
                    .then()
                    .statusCode(500);
        } finally {
            context.stop();
        }
    }

    static final class TestBean {
    }
}
