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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import java.io.File;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

public class VertxPlatformHttpFileResponseTest {

    @Test
    void testFileResponse() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();
        final File file = new File("src/test/resources/dummy.txt");

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/file").setBody(constant(file));
                }
            });

            context.start();

            String requestBody = "Give me a file";
            given().body(requestBody).get("/file").then().statusCode(200).body(is("Hello World from this file"));
        } finally {
            context.stop();
        }
    }

    @Test
    void testFileEndpointResponse() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/file")
                            .pollEnrich("file:src/test/resources/?fileName=dummy.txt&noop=true", 5000);
                }
            });

            context.start();

            String requestBody = "Give me a file";
            given().body(requestBody).get("/file").then().statusCode(200).body(is("Hello World from this file"));
        } finally {
            context.stop();
        }
    }
}
