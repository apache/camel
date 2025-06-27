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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

public class PlatformHttpCamelHeadersTest extends AbstractPlatformHttpTest {

    @Test
    void testFilterCamelHeaders() {
        given()
                .header("Accept", "application/json")
                .header("User-Agent", "User-Agent-Camel")
                .header("caMElHttpResponseCode", "503")
                .port(port)
                .expect()
                .statusCode(200)
                .header("Accept", (String) null)
                .header("User-Agent", (String) null)
                .header("CamelHttpResponseCode", (String) null)
                .when()
                .get("/get");
    }

    @Override
    protected RouteBuilder routes() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("platform-http:/get")
                        .process(e -> {
                            Assertions.assertEquals("application/json", e.getMessage().getHeader("Accept"));
                            Assertions.assertEquals("User-Agent-Camel", e.getMessage().getHeader("User-Agent"));
                            Assertions.assertNull(e.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
                        })
                        .setBody().constant("");
            }
        };
    }

}
