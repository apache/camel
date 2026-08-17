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
package org.apache.camel.component.statestore.caffeine;

import java.util.Map;
import java.util.Properties;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.statestore.StateStoreBackend;
import org.apache.camel.component.statestore.StateStoreConstants;
import org.apache.camel.test.main.junit5.CamelMainTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that a Caffeine backend can be fully configured via properties (camel.beans.*) and auto-discovered without
 * explicit backend=# reference on the endpoint.
 */
class CaffeineStateStorePropertiesTest extends CamelMainTestSupport {

    @Override
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        Properties props = new Properties();
        props.setProperty("camel.beans.caffeineBackend",
                "#class:" + CaffeineStateStoreBackend.class.getName());
        props.setProperty("camel.beans.caffeineBackend.maximumSize", "5000");
        return props;
    }

    @Test
    void testBackendConfiguredViaProperties() {
        // Verify the bean was created and configured
        StateStoreBackend backend = context.getRegistry().lookupByNameAndType("caffeineBackend", StateStoreBackend.class);
        assertThat(backend).isNotNull().isInstanceOf(CaffeineStateStoreBackend.class);
        assertThat(((CaffeineStateStoreBackend) backend).getMaximumSize()).isEqualTo(5000);
    }

    @Test
    void testAutoDiscoveryWithPropertiesConfiguredBackend() {
        // No backend=#caffeineBackend in the URI — auto-discovery should find it
        Object previous = template.requestBodyAndHeaders(
                "direct:put", "hello",
                Map.of(StateStoreConstants.KEY, "key1"));
        assertThat(previous).isNull();

        Object result = template.requestBodyAndHeaders(
                "direct:get", null,
                Map.of(StateStoreConstants.KEY, "key1"));
        assertThat(result).isEqualTo("hello");
    }

    @Test
    void testExplicitBeanReferenceWithPropertiesConfiguredBackend() {
        // Explicit backend=#caffeineBackend — should also work
        Object previous = template.requestBodyAndHeaders(
                "direct:put-explicit", "world",
                Map.of(StateStoreConstants.KEY, "key2"));
        assertThat(previous).isNull();

        Object result = template.requestBodyAndHeaders(
                "direct:get-explicit", null,
                Map.of(StateStoreConstants.KEY, "key2"));
        assertThat(result).isEqualTo("world");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Auto-discovery (no backend reference)
                from("direct:put").to("state-store:myStore?operation=put");
                from("direct:get").to("state-store:myStore?operation=get");
                // Explicit bean reference
                from("direct:put-explicit").to("state-store:explicitStore?operation=put&backend=#caffeineBackend");
                from("direct:get-explicit").to("state-store:explicitStore?operation=get&backend=#caffeineBackend");
            }
        };
    }
}
