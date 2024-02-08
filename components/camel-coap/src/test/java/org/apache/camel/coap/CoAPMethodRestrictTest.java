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
import org.eclipse.californium.elements.config.Configuration;
import org.eclipse.californium.elements.util.Bytes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CoAPMethodRestrictTest extends CoAPTestSupport {

    @Test
    void testDefaultCoAPMethodRestrict() {
        Configuration.createStandardWithoutFile();

        // All request methods should be valid on this endpoint
        assertCoAPMethodRestrictResponse("/test", CoAPConstants.METHOD_RESTRICT_ALL, "GET: /test");
    }

    @Test
    void testSpecifiedCoAPMethodRestrict() {
        Configuration.createStandardWithoutFile();

        // Only GET is valid for /test/a
        assertCoAPMethodRestrictResponse("/test/a", "GET", "GET: /test/a");

        // Only DELETE is valid for /test/a/b
        assertCoAPMethodRestrictResponse("/test/a/b", "DELETE", "DELETE: /test/a/b");

        // Only DELETE & GET are valid for /test/a/b/c
        assertCoAPMethodRestrictResponse("/test/a/b/c", "DELETE,GET", "DELETE & GET: /test/a/b/c");

        // Only GET is valid for /test/b
        assertCoAPMethodRestrictResponse("/test/b", "GET", "GET: /test/b");
    }

    private void assertCoAPMethodRestrictResponse(String path, String methodRestrict, String expectedResponse) {
        for (String method : CoAPConstants.METHOD_RESTRICT_ALL.split(",")) {
            String result = template.requestBodyAndHeader("coap://localhost:" + PORT + path, null, CoAPConstants.COAP_METHOD,
                    method, String.class);
            if (methodRestrict.contains(method)) {
                assertEquals(expectedResponse, result);
            } else {
                assertArrayEquals(Bytes.EMPTY, result.getBytes());
            }
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("coap://localhost:%d/test", PORT).setBody(constant("GET: /test"));

                fromF("coap://localhost:%d/test/a?coapMethodRestrict=GET", PORT).setBody(constant("GET: /test/a"));

                fromF("coap://localhost:%d/test/a/b?coapMethodRestrict=DELETE", PORT).setBody(constant("DELETE: /test/a/b"));

                fromF("coap://localhost:%d/test/a/b/c?coapMethodRestrict=DELETE,GET", PORT)
                        .setBody(constant("DELETE & GET: /test/a/b/c"));

                fromF("coap://localhost:%d/test/b?coapMethodRestrict=GET", PORT).setBody(constant("GET: /test/b"));
            }
        };
    }
}
