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
package org.apache.camel.service.lra;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LRAUrlBuilderTest {

    @DisplayName("Tests that query() URL-encodes special characters in values")
    @Test
    void testQueryEncodesSpecialCharacters() {
        String url = new LRAUrlBuilder()
                .host("http://localhost:8080")
                .path("lra-participant")
                .query("key", "value with spaces&and=special+chars#hash")
                .build();

        assertTrue(url.contains("key=value+with+spaces%26and%3Dspecial%2Bchars%23hash"),
                "Special characters must be URL-encoded in query values: " + url);
    }

    @DisplayName("Tests that compensation URI with query params is properly encoded")
    @Test
    void testCompensationUriWithQueryParams() {
        String url = new LRAUrlBuilder()
                .host("http://localhost:8080")
                .path("lra-participant")
                .compensation("seda:cancelOrder?concurrentConsumers=2&size=1000")
                .build();

        URI uri = URI.create(url);
        assertNotNull(uri.getQuery(), "URL must have a valid query string");

        String query = uri.getRawQuery();
        assertTrue(query.contains("Camel-Saga-Compensate="),
                "Query must contain the compensation key");
        assertTrue(query.contains("seda%3AcancelOrder%3FconcurrentConsumers%3D2%26size%3D1000"),
                "Compensation URI must be fully encoded: " + query);
    }

    @DisplayName("Tests full round-trip: LRAUrlBuilder encodes, parseQuery decodes back to original")
    @Test
    void testCallbackUrlRoundTrip() throws Exception {
        String compensationUri = "seda:cancelOrder?concurrentConsumers=2&size=1000";
        String completionUri = "direct:complete";
        String optionValue = "ACME&region=EU";

        String url = new LRAUrlBuilder()
                .host("http://localhost:8080")
                .path("lra-participant")
                .query("myOption", optionValue)
                .compensation(compensationUri)
                .completion(completionUri)
                .path("compensate")
                .build();

        URI uri = URI.create(url);
        String rawQuery = uri.getRawQuery();

        Method parseQuery = LRASagaRoutes.class.getDeclaredMethod("parseQuery", String.class);
        parseQuery.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> parsed = (Map<String, String>) parseQuery.invoke(new LRASagaRoutes(null), rawQuery);

        assertEquals(compensationUri, parsed.get("Camel-Saga-Compensate"),
                "Compensation URI must survive encode/decode round-trip");
        assertEquals(completionUri, parsed.get("Camel-Saga-Complete"),
                "Completion URI must survive encode/decode round-trip");
        assertEquals(optionValue, parsed.get("myOption"),
                "Option value with special characters must survive encode/decode round-trip");
    }

    @DisplayName("Tests that simple direct: URIs still work after encoding")
    @Test
    void testSimpleDirectUri() {
        String url = new LRAUrlBuilder()
                .host("http://localhost:8080")
                .path("lra-participant")
                .compensation("direct://saga1_participant1_compensate")
                .completion("direct://saga1_participant1_complete")
                .path("compensate")
                .build();

        URI uri = URI.create(url);
        assertNotNull(uri.getQuery(), "URL must have a valid query string");
    }
}
