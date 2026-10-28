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
package org.apache.camel.component.openfeature;

import java.util.Map;

import dev.openfeature.sdk.providers.memory.Flag;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenFeatureInMemoryProviderTest extends CamelTestSupport {

    @SuppressWarnings("unchecked")
    private static Map<String, Flag<?>> flagDefinitions() {
        return Map.of(
                "feature-x", Flag.<Boolean> builder()
                        .variant("on", true)
                        .variant("off", false)
                        .defaultVariant("on")
                        .build(),
                "feature-y", Flag.<Boolean> builder()
                        .variant("on", true)
                        .variant("off", false)
                        .defaultVariant("off")
                        .build());
    }

    private final InMemoryProvider sharedProvider = new InMemoryProvider(flagDefinitions());

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("flags", sharedProvider);
        registry.bind("customProvider", sharedProvider);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:default-lookup")
                        .to("openfeature:flags?flagKey=feature-x");

                from("direct:explicit-ref")
                        .to("openfeature:myDomain?flagKey=feature-y&provider=#customProvider");

                from("direct:filter")
                        .filter().language("openfeature", "feature-x")
                            .to("mock:filtered")
                        .end();
            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.addComponent("openfeature", new OpenFeatureComponent());
        return camelContext;
    }

    @Test
    void testDefaultBeanLookup() {
        Object result = template.requestBody("direct:default-lookup", "ignored");
        assertThat(result).isEqualTo(true);
    }

    @Test
    void testExplicitProviderBeanReference() {
        Object result = template.requestBody("direct:explicit-ref", "ignored");
        assertThat(result).isEqualTo(false);
    }

    @Test
    void testLanguageWithInMemoryProvider() throws Exception {
        MockEndpoint filtered = getMockEndpoint("mock:filtered");
        filtered.expectedMessageCount(1);

        template.sendBody("direct:filter", "test-message");

        filtered.assertIsSatisfied();
    }
}
