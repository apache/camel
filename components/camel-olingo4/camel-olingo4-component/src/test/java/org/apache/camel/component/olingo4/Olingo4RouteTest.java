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

package org.apache.camel.component.olingo4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Olingo4RouteTest extends AbstractOlingo4WireMockTestSupport {

    @SuppressWarnings("unchecked")
    protected <T> T requestBody(String endpoint, Object body, Map<String, Object> headers)
            throws CamelExecutionException {
        return (T) template().requestBodyAndHeaders(endpoint, body, headers);
    }

    @Override
    public String getClassIdentifier() {
        return "Olingo4RouteTest";
    }

    @Order(1)
    @Test
    public void testRead() {
        // Read entity set of the People object
        final ClientEntitySet entities = (ClientEntitySet) requestBody("direct:readentities", null, null);
        assertNotNull(entities);
        assertEquals(20, entities.getEntities().size());
    }

    @Order(2)
    @Test
    public void testReadWithQueryParams() {
        final Map<String, Object> headers = new HashMap<>();
        headers.put("CamelOlingo4.queryParams", Map.of("$top", "5"));

        // Read entity set of the People object
        final ClientEntitySet entities = (ClientEntitySet) requestBody("direct:readentities", null, headers);
        assertNotNull(entities);
        assertEquals(5, entities.getEntities().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() throws IOException {
                from("direct:readentities").to("olingo4://read/People?serviceUri=" + getResolvedTestServiceBaseUrl());
            }
        };
    }
}
