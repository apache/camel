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
package org.apache.camel.component.kafka;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class KafkaAutowireTest {

    @RegisterExtension
    protected static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    private final CamelContext context = contextExtension.getContext();

    @BindToRegistry
    private final KafkaClientFactory clientFactory = new TestKafkaClientFactory();

    @Test
    void testKafkaComponentAutowiring() {
        KafkaComponent component = context.getComponent("kafka", KafkaComponent.class);
        assertSame(clientFactory, component.getKafkaClientFactory());

        KafkaEndpoint endpoint = context.getEndpoint("kafka:foo", KafkaEndpoint.class);
        assertSame(clientFactory, endpoint.getKafkaClientFactory());
    }

    /**
     * Verifies that autowiring works when the component is registered via
     * {@link CamelContext#addComponent(String, org.apache.camel.Component)}, which is the path used by Spring Boot. In
     * this path, the component is added before the context is started, so {@code doInit()} runs before the autowiring
     * lifecycle strategy. The default factory must not be created until {@code doStart()} to give autowiring a chance
     * to inject a custom factory.
     *
     * This is a regression test for <a href="https://issues.apache.org/jira/browse/CAMEL-24305">CAMEL-24305</a>.
     */
    @Test
    void testKafkaComponentAutowiringViaAddComponent() throws Exception {
        // Simulate the Spring Boot path: addComponent() is called before the context is started
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            KafkaClientFactory customFactory = new TestKafkaClientFactory();
            ctx.getRegistry().bind("kafkaClientFactory", customFactory);

            KafkaComponent component = new KafkaComponent();
            ctx.addComponent("kafka", component);

            ctx.start();

            assertSame(customFactory, component.getKafkaClientFactory(),
                    "Custom KafkaClientFactory should be autowired when using addComponent()");
        }
    }

    /**
     * Verifies that when no custom KafkaClientFactory is registered, the component creates a
     * {@link DefaultKafkaClientFactory} as the default.
     */
    @Test
    void testKafkaComponentDefaultFactoryWhenNoneRegistered() throws Exception {
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            KafkaComponent component = new KafkaComponent();
            ctx.addComponent("kafka", component);

            ctx.start();

            assertInstanceOf(DefaultKafkaClientFactory.class, component.getKafkaClientFactory(),
                    "Default KafkaClientFactory should be created when none is registered");
        }
    }

    static final class TestKafkaClientFactory extends DefaultKafkaClientFactory {

    }
}
