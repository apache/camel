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

import java.io.ByteArrayInputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Under json binding, a binary response is written as declared by produces instead of being json marshalled.
 */
public class VertxPlatformHttpRestBindingProducesTest {

    private static final byte[] PAYLOAD = { 'P', 'K', 3, 4, 0, 1, 2 };

    @Test
    public void testBinaryBodyIsNotJsonMarshalled() throws Exception {
        CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    restConfiguration().bindingMode(RestBindingMode.json);

                    rest("/files")
                            .get("/bytes").produces("application/octet-stream").to("direct:bytes")
                            .get("/stream").produces("application/octet-stream").to("direct:stream");

                    from("direct:bytes").setBody(constant(PAYLOAD));
                    from("direct:stream").process(e -> e.getMessage().setBody(new ByteArrayInputStream(PAYLOAD)));
                }
            });
            context.start();

            for (String path : new String[] { "/files/bytes", "/files/stream" }) {
                byte[] body = given()
                        .when()
                        .get(path)
                        .then()
                        .statusCode(200)
                        .header("Content-Type", equalTo("application/octet-stream"))
                        .extract().asByteArray();
                assertArrayEquals(PAYLOAD, body, path);
            }
        } finally {
            context.stop();
        }
    }
}
