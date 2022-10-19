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

import io.restassured.http.ContentType;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

public class PlatformHttpProxyTest extends AbstractPlatformHttpTest {
    @Test
    void testProxy() {
        given()
                .body("hello")
                .proxy("http://localhost:" + AbstractPlatformHttpTest.port)
                .contentType(ContentType.HTML)
                .when().get("http://neverssl.com:80")
                .then()
                .statusCode(200)
                .body(containsString("<html>"));
    }

    @Override
    protected RouteBuilder routes() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("platform-http:proxy")
                        .toD("${headers." + Exchange.HTTP_SCHEME + "}://" +
                             "${headers." + Exchange.HTTP_HOST + "}:" +
                             "${headers." + Exchange.HTTP_PORT + "}" +
                             "${headers." + Exchange.HTTP_PATH + "}?bridgeEndpoint=true");
            }
        };
    }

}
