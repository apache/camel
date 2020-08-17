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
package org.apache.camel.coap;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CoAPMethodTest extends CoAPTestSupport {

    @Test
    void testCoAPMethodDefaultGet() {
        // No body means GET
        String result = template.requestBody("coap://localhost:" + PORT + "/test/a", null, String.class);
        assertEquals("GET: /test/a", result);
    }

    @Test
    void testCoAPMethodDefaultPost() {
        // Providing a body means POST
        String result = template.requestBody("coap://localhost:" + PORT + "/test/b", "Camel", String.class);
        assertEquals("Hello Camel", result);
    }

    @Test
    void testCoAPMethodHeader() {
        String result = template.requestBodyAndHeader("coap://localhost:" + PORT + "/test/c", null, CoAPConstants.COAP_METHOD,
                "DELETE", String.class);
        assertEquals("DELETE: /test/c", result);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("coap://localhost:%d/test/a?coapMethodRestrict=GET", PORT).setBody(constant("GET: /test/a"));

                fromF("coap://localhost:%d/test/b?coapMethodRestrict=POST", PORT).setBody(simple("Hello ${body}"));

                fromF("coap://localhost:%d/test/c?coapMethodRestrict=DELETE", PORT).setBody(constant("DELETE: /test/c"));
            }
        };
    }
}
