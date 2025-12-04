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

import static io.restassured.RestAssured.given;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PlatformHttpConsumesPlusSignTest extends AbstractPlatformHttpTest {

    @Test
    void testConsumesPlusSign() {
        given().header("Accept", "application/fhir+json")
                .header("User-Agent", "User-Agent-Camel")
                .port(port)
                .expect()
                .statusCode(200)
                .header("Accept", (String) null)
                .header("User-Agent", (String) null)
                .when()
                .get("/get");

        PlatformHttpEndpoint phe =
                (PlatformHttpEndpoint) getContext().getEndpoints().iterator().next();
        Assertions.assertEquals("application/fhir+json,text/plain", phe.getConsumes());
    }

    @Override
    protected RouteBuilder routes() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("platform-http:/get?consumes=application/fhir+json, text/plain")
                        .process(e -> {
                            Assertions.assertEquals(
                                    "application/fhir+json", e.getMessage().getHeader("Accept"));
                            Assertions.assertEquals(
                                    "User-Agent-Camel", e.getMessage().getHeader("User-Agent"));
                            Assertions.assertNull(e.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
                        })
                        .setBody()
                        .constant("");
            }
        };
    }
}
