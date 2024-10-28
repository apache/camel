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
package org.apache.camel.component.amqp.artemis;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.amqp.AMQPComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.artemis.common.ArtemisProperties;
import org.apache.camel.test.infra.artemis.services.ArtemisAMQPService;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.api.ConfigurableContext;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AMQP tests with SASL Authentication.
 */
public class AMQPAuthRouteTest implements ConfigurableContext, ConfigurableRoute {

    private ProducerTemplate template;
    private MockEndpoint resultEndpoint;

    static {
        // artemis service authentication
        System.setProperty(ArtemisProperties.ARTEMIS_AUTHENTICATION_ENABLED, "true");
    }

    @Order(1)
    @RegisterExtension
    protected static ArtemisService service
            = new ArtemisServiceFactory.SingletonArtemisService(new ArtemisAMQPService(), "artemis-amqp-auth");

    @Order(2)
    @RegisterExtension
    protected static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    @AfterAll
    static void afterAll() {
        // restore default
        System.setProperty(ArtemisProperties.ARTEMIS_AUTHENTICATION_ENABLED, "false");
    }

    @BeforeEach
    void setupTemplate() {
        template = contextExtension.getProducerTemplate();
        resultEndpoint = contextExtension.getMockEndpoint("mock:result");
    }

    @Test
    public void testConfigJmsQueue() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.message(0).header("cheese").isEqualTo(123);

        template.sendBodyAndHeader("amqp-auth:queue:ping-auth", "Hello World", "cheese", 123);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testConfigRequestReply() {
        String response = template.requestBody("amqp-auth:queue:inOut-auth", "Hello World", String.class);
        assertEquals("response", response);
    }

    @Test
    public void testConfigJmsTopic() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.message(0).header("cheese").isEqualTo(123);
        template.sendBodyAndHeader("amqp-auth:topic:ping-auth", "Hello World", "cheese", 123);
        resultEndpoint.assertIsSatisfied();
    }

    @ContextFixture
    public void configureContext(CamelContext context) {
        AMQPComponent customizedComponent = new AMQPComponent();
        customizedComponent.setPort(service.brokerPort());
        // default ArtemisService user/password
        customizedComponent.setUsername("camel");
        customizedComponent.setPassword("rider");
        context.addComponent("amqp-auth", customizedComponent);
    }

    @RouteFixture
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(createRouteBuilder());
    }

    private static RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("amqp-auth:queue:ping-auth")
                        .to("log:routing")
                        .to("mock:result");

                from("amqp-auth:queue:inOut-auth")
                        .setBody().constant("response");

                from("amqp-auth:topic:ping-auth")
                        .to("log:routing")
                        .to("mock:result");
            }
        };
    }
}
