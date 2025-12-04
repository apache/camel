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
import static org.hamcrest.CoreMatchers.is;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.junit.jupiter.api.Test;

public class PlatformHttpReturnHttpRequestHeadersTest extends AbstractPlatformHttpTest {

    @Test
    void testReturnHttpRequestHeadersFalse() {
        given().header("Accept", "application/json")
                .header("User-Agent", "User-Agent-Camel")
                .port(port)
                .expect()
                .statusCode(200)
                .header("Accept", (String) null)
                .header("User-Agent", (String) null)
                .when()
                .get("/getWithoutRequestHeadersReturn");
    }

    @Test
    void testReturnHttpRequestHeadersTrue() {
        given().header("Accept", "application/json")
                .header("User-Agent", "User-Agent-Camel")
                .port(port)
                .expect()
                .statusCode(200)
                .header("Accept", "application/json")
                .header("User-Agent", "User-Agent-Camel")
                .when()
                .get("/getWithRequestHeadersReturn");
    }

    @Test
    void testReturnHttpRequestHeadersDefault() {
        given().header("Accept", "application/json")
                .header("User-Agent", "User-Agent-Camel")
                .port(port)
                .expect()
                .statusCode(200)
                .header("Accept", (String) null)
                .header("User-Agent", (String) null)
                .when()
                .get("/get");
    }

    @Test
    void testReturnHttpRequestHeadersFalseWithCustomHeaderFilterStrategy() {
        given().header("Accept", "application/json")
                .header("User-Agent", "User-Agent-Camel")
                .header("Custom_In_Header", "Custom_In_Header_Value")
                .header("Custom_Out_Header", "Custom_Out_Header_Value")
                .port(port)
                .expect()
                .statusCode(200)
                .header("Accept", (String) null)
                .header("User-Agent", (String) null)
                .header("Custom_In_Header", (String) null)
                .header("Custom_Out_Header", (String) null)
                .body(is("Custom_In_Header=, Custom_Out_Header=Custom_Out_Header_Value"))
                .when()
                .get("/getWithCustomHeaderFilterStrategy");
    }

    @Override
    protected RouteBuilder routes() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                DefaultHeaderFilterStrategy testHeaderFilterStrategy = new DefaultHeaderFilterStrategy();
                testHeaderFilterStrategy.getInFilter().add("Custom_In_Header");
                testHeaderFilterStrategy.getOutFilter().add("Custom_Out_Header");
                getContext().getRegistry().bind("testHeaderFilterStrategy", testHeaderFilterStrategy);

                from("platform-http:/getWithoutRequestHeadersReturn?returnHttpRequestHeaders=false")
                        .setBody()
                        .constant("getWithoutRequestHeadersReturn");
                from("platform-http:/getWithRequestHeadersReturn?returnHttpRequestHeaders=true")
                        .setBody()
                        .constant("getWithRequestHeadersReturn");
                from("platform-http:/getWithCustomHeaderFilterStrategy?headerFilterStrategy=#testHeaderFilterStrategy")
                        .setBody()
                        .simple(
                                "Custom_In_Header=${header.Custom_In_Header}, Custom_Out_Header=${header.Custom_Out_Header}");
                from("platform-http:/get").setBody().constant("get");
            }
        };
    }
}
