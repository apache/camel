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
package org.apache.camel.component.platform.http;

import io.restassured.RestAssured;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;

public class RestPlatformHttpContextPathConfigurationTest extends AbstractPlatformHttpTest {

    @Test
    public void contextPath() {
        RestAssured.get("/rest/get")
                .then()
                .body(containsString("GET: /get"));

        RestAssured.given()
                .contentType("text/plain")
                .post("/rest/post")
                .then()
                .body(containsString("POST: /post"));
    }

    @Override
    protected RouteBuilder routes() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration()
                        .component("platform-http")
                        .contextPath("/rest");

                rest()
                        .get("/get").to("direct:get")
                        .post("/post").consumes("text/plain").produces("text/plain").to("direct:post");

                from("direct:get")
                        .setBody(constant("GET: /get"));
                from("direct:post")
                        .setBody(constant("POST: /post"));

            }
        };
    }

}
