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

package org.apache.camel.component.kafka.integration.health;

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.component.kafka.MockConsumerInterceptor;
import org.apache.camel.component.kafka.integration.common.KafkaTestUtil;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.impl.health.DefaultHealthCheckRegistry;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.api.ConfigurableContext;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.apache.camel.test.infra.kafka.services.KafkaService;
import org.apache.camel.test.infra.kafka.services.KafkaServiceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class KafkaHealthCheckTestSupport implements ConfigurableRoute, ConfigurableContext {
    protected static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerUnresolvableHealthCheckIT.class);
    @Order(1)
    @RegisterExtension
    protected static KafkaService service = KafkaServiceFactory.createService();

    @Order(2)
    @RegisterExtension
    protected static CamelContextExtension contextExtension = new DefaultCamelContextExtension();
    protected org.apache.kafka.clients.producer.KafkaProducer<String, String> producer;
    @EndpointInject("mock:result")
    protected MockEndpoint to;
    protected boolean serviceShutdown = false;

    @BeforeAll
    public static void beforeClass() {
        service.initialize();

        LOG.info("### Embedded Kafka cluster broker list: {}", service.getBootstrapServers());
        System.setProperty("bootstrapServers", service.getBootstrapServers());
        System.setProperty("brokers", service.getBootstrapServers());
    }

    @AfterEach
    public void after() {
        if (producer != null) {
            producer.close();
        }
    }

    @BeforeEach
    public void before() {
        if (!serviceShutdown) {
            Properties props = KafkaTestUtil.getDefaultProperties(service);
            producer = new org.apache.kafka.clients.producer.KafkaProducer<>(props);
            MockConsumerInterceptor.recordsCaptured.clear();
        }
    }

    @ContextFixture
    private void configureHealthRegistry(CamelContext context) {
        // install health check manually (yes a bit cumbersome)
        HealthCheckRegistry registry = new DefaultHealthCheckRegistry();
        registry.setCamelContext(context);
        Object hc = registry.resolveById("context");
        registry.register(hc);
        hc = registry.resolveById("routes");
        registry.register(hc);
        hc = registry.resolveById("consumers");
        registry.register(hc);
        context.getCamelContextExtension().addContextPlugin(HealthCheckRegistry.class, registry);
    }

    @Override
    @RouteFixture
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(createRouteBuilder());
    }

    protected abstract RoutesBuilder createRouteBuilder();
}
