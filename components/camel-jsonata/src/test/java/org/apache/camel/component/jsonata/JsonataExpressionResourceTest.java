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
package org.apache.camel.component.jsonata;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CatalogCamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When the producer opens the resource of the endpoint, and the allowContextMapAll option that jsonata does not use.
 */
class JsonataExpressionResourceTest extends CamelTestSupport {

    private static final String SPEC = "classpath:org/apache/camel/component/jsonata/firstSample/expressions.spec";

    @Test
    void headerExpressionDoesNotNeedTheEndpointResource() {
        // the endpoint resource was opened before the header was read, so a missing resource failed the exchange
        Exchange result = template.request(
                "jsonata:classpath:org/apache/camel/component/jsonata/missing.spec"
                                           + "?allowTemplateFromHeader=true&contentCache=false&outputType=JsonString",
                exchange -> {
                    exchange.getIn().setHeader(JsonataConstants.JSONATA_RESOURCE_URI, "name");
                    exchange.getIn().setBody("{\"name\":\"Camel\"}");
                });

        assertNull(result.getException(), () -> "the exchange failed with " + result.getException());
        assertEquals("\"Camel\"", result.getMessage().getBody(String.class));
    }

    @Test
    void endpointResourceIsOpenedOnlyWithoutHeaderExpression() throws Exception {
        AtomicInteger opened = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        JsonataComponent component = context.getComponent("jsonata", JsonataComponent.class);
        JsonataEndpoint endpoint = new JsonataEndpoint("jsonata:counting", component, "counting", null) {
            @Override
            protected InputStream loadResource(String uri) {
                opened.incrementAndGet();
                return new ByteArrayInputStream("name".getBytes(StandardCharsets.UTF_8)) {
                    @Override
                    public void close() {
                        closed.incrementAndGet();
                    }
                };
            }
        };
        endpoint.setContentCache(false);
        endpoint.setAllowTemplateFromHeader(true);
        endpoint.setOutputType(JsonataInputOutputType.JsonString);
        context.addEndpoint("jsonata:counting", endpoint);

        Exchange fromHeader = template.request(endpoint, exchange -> {
            exchange.getIn().setHeader(JsonataConstants.JSONATA_RESOURCE_URI, "name");
            exchange.getIn().setBody("{\"name\":\"Camel\"}");
        });

        // the stream of the endpoint resource was opened and then replaced by the header, so it was never closed
        assertNull(fromHeader.getException(), () -> "the exchange failed with " + fromHeader.getException());
        assertEquals(0, opened.get(), "the endpoint resource is not opened when the header replaces it");

        Exchange fromResource = template.request(endpoint, exchange -> exchange.getIn().setBody("{\"name\":\"Camel\"}"));

        assertNull(fromResource.getException(), () -> "the exchange failed with " + fromResource.getException());
        assertEquals("\"Camel\"", fromResource.getMessage().getBody(String.class));
        assertEquals(1, opened.get());
        assertEquals(1, closed.get(), "the stream of the endpoint resource is closed");
    }

    @Test
    void allowContextMapAllIsDeprecated() throws Exception {
        // jsonata never builds a context map, so the option inherited from ResourceEndpoint has no effect
        JsonNode option = JsonMapper.builder().build()
                .readTree(((CatalogCamelContext) context).getComponentParameterJsonSchema("jsonata"))
                .path("properties").path("allowContextMapAll");
        assertTrue(option.path("deprecated").asBoolean(), () -> "allowContextMapAll is not deprecated: " + option);

        // a uri that sets the option keeps working
        assertNotNull(context.getEndpoint("jsonata:" + SPEC + "?allowContextMapAll=true", JsonataEndpoint.class));
    }
}
