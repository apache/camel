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
package org.apache.camel.impl;

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.EndpointUriFactory;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.RouteTopologyDumper.TopologyEdge;
import org.apache.camel.spi.RouteTopologyDumper.TopologyResult;
import org.apache.camel.support.component.EndpointUriFactorySupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the route topology dumper correctly uses endpoint identity query parameters to distinguish endpoints that
 * share the same scheme:context-path but differ in identity-bearing query parameters.
 */
class DefaultRouteTopologyDumperIdentityTest extends ContextTestSupport {

    @Override
    public boolean isUseAdviceWith() {
        // skip context start so seda does not enforce same-name/same-size constraint
        return true;
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        // Register a custom EndpointUriFactory that declares "size" as an endpoint identity
        // parameter for seda. The registry lookup takes priority over the built-in factory.
        registry.bind("sedaIdentityFactory", new SedaWithSizeIdentityFactory());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Two seda consumers with same queue name but different "size" values.
                // With "size" declared as identity, these are different destinations.
                from("seda:work?size=100").routeId("consumer-small")
                        .to("mock:small");

                from("seda:work?size=500").routeId("consumer-large")
                        .to("mock:large");

                // Producer sending to the small queue only
                from("direct:sender").routeId("sender")
                        .to("seda:work?size=100");
            }
        };
    }

    @Test
    void testIdentityParamsDistinguishEndpoints() {
        TopologyResult result = dumpTopology();

        // sender -> consumer-small should exist (same size=100)
        TopologyEdge toSmall = findEdge(result, "sender", "consumer-small");
        assertNotNull(toSmall, "sender should connect to consumer-small via seda:work?size=100");
        assertTrue(toSmall.endpoint().contains("size=100"));

        // sender -> consumer-large should NOT exist (different size)
        TopologyEdge toLarge = findEdge(result, "sender", "consumer-large");
        assertNull(toLarge, "sender should NOT connect to consumer-large (different size value)");
    }

    @Test
    void testIdentityParamsIncludedInNodeFrom() {
        TopologyResult result = dumpTopology();

        var smallNode = result.nodes().stream()
                .filter(n -> "consumer-small".equals(n.routeId()))
                .findFirst().orElse(null);
        assertNotNull(smallNode);
        assertEquals("seda:work?size=100", smallNode.from());

        var largeNode = result.nodes().stream()
                .filter(n -> "consumer-large".equals(n.routeId()))
                .findFirst().orElse(null);
        assertNotNull(largeNode);
        assertEquals("seda:work?size=500", largeNode.from());
    }

    @Test
    void testNonIdentityParamsStillStripped() {
        TopologyResult result = dumpTopology();
        // consumer-small URI is "seda:work?size=100" — only size (identity) should be kept
        var smallNode = result.nodes().stream()
                .filter(n -> "consumer-small".equals(n.routeId()))
                .findFirst().orElse(null);
        assertNotNull(smallNode);
        assertEquals("seda:work?size=100", smallNode.from());
    }

    private TopologyResult dumpTopology() {
        DefaultRouteTopologyDumper dumper = new DefaultRouteTopologyDumper();
        return dumper.dumpTopology(context);
    }

    private TopologyEdge findEdge(TopologyResult result, String fromRouteId, String toRouteId) {
        return result.edges().stream()
                .filter(e -> fromRouteId.equals(e.fromRouteId()) && toRouteId.equals(e.toRouteId()))
                .findFirst().orElse(null);
    }

    /**
     * A custom EndpointUriFactory for seda that declares "size" as an endpoint identity parameter. This overrides the
     * real seda factory via registry lookup priority.
     */
    static class SedaWithSizeIdentityFactory extends EndpointUriFactorySupport implements EndpointUriFactory {

        @Override
        public boolean isEnabled(String scheme) {
            return "seda".equals(scheme);
        }

        @Override
        public String buildUri(String scheme, Map<String, Object> properties, boolean encode) throws URISyntaxException {
            return scheme + ":" + properties.getOrDefault("name", "");
        }

        @Override
        public Set<String> propertyNames() {
            return Set.of("name", "size", "concurrentConsumers", "timeout");
        }

        @Override
        public Set<String> secretPropertyNames() {
            return Collections.emptySet();
        }

        @Override
        public Set<String> endpointIdentityPropertyNames() {
            return Set.of("size");
        }

        @Override
        public Map<String, String> multiValuePrefixes() {
            return Collections.emptyMap();
        }

        @Override
        public boolean isLenientProperties() {
            return false;
        }
    }
}
