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
package org.apache.camel.component.kafka.integration;

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.integration.common.KafkaAdminUtil;
import org.apache.camel.component.kafka.integration.common.KafkaTestUtil;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.apache.camel.test.infra.kafka.services.KafkaService;
import org.apache.camel.test.infra.kafka.services.KafkaServiceFactory;
import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class BaseEmbeddedKafkaTestSupport implements ConfigurableRoute {
    @Order(1)
    @RegisterExtension
    protected static KafkaService service = KafkaServiceFactory.createSingletonService();

    @Order(2)
    @RegisterExtension
    protected static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    protected static AdminClient kafkaAdminClient;

    @BeforeEach
    public void beforeClass() {
        KafkaTestUtil.setServiceProperties(service);
    }

    @BeforeEach
    public void setKafkaAdminClient() {
        if (kafkaAdminClient == null) {
            kafkaAdminClient = KafkaAdminUtil.createAdminClient(service);
        }
    }

    @ContextFixture
    public void configureKafka(CamelContext context) {
        context.getPropertiesComponent().setLocation("ref:prop");

        KafkaComponent kafka = new KafkaComponent(context);
        kafka.init();
        kafka.getConfiguration().setBrokers(service.getBootstrapServers());
        context.addComponent("kafka", kafka);
    }

    @RouteFixture
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(createRouteBuilder());
    }

    protected abstract RouteBuilder createRouteBuilder();

    protected Properties getDefaultProperties() {
        return KafkaTestUtil.getDefaultProperties(service);
    }

    protected static String getBootstrapServers() {
        return service.getBootstrapServers();
    }
}
