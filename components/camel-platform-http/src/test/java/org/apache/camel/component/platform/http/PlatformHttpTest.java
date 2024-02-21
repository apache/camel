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

import java.util.Iterator;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PlatformHttpTest extends AbstractPlatformHttpTest {

    @Test
    public void testGet() {
        given()
                .header("Accept", "application/json")
                .port(port)
                .expect()
                .statusCode(200)
                .when()
                .get("/get");
    }

    @Test
    public void testPost() {
        RequestSpecification request = RestAssured.given();
        request.body("test");
        Response response = request.get("/post");

        int statusCode = response.getStatusCode();
        assertEquals(200, statusCode);
        assertEquals("TEST", response.body().asString().trim());

        PlatformHttpComponent phc = getContext().getComponent("platform-http", PlatformHttpComponent.class);
        assertEquals(2, phc.getHttpEndpoints().size());
        Iterator<HttpEndpointModel> it = phc.getHttpEndpoints().iterator();
        assertEquals("/get", it.next().getUri());
        assertEquals("/post", it.next().getUri());
    }

    @Override
    protected RouteBuilder routes() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("platform-http:/get")
                        .setBody().constant("get");
                from("platform-http:/post")
                        .transform().body(String.class, b -> b.toUpperCase());
            }
        };
    }

}
