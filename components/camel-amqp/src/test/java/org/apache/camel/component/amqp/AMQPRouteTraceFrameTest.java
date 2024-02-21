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
package org.apache.camel.component.amqp;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.amqp.AMQPComponent.amqpComponent;
import static org.apache.camel.component.amqp.AMQPConnectionDetails.discoverAMQP;

public class AMQPRouteTraceFrameTest extends AMQPTestSupport {

    @RegisterExtension
    protected static ArtemisService service = ArtemisServiceFactory.createSingletonAMQPService();

    @RegisterExtension
    protected static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    private MockEndpoint resultEndpoint;

    private final String expectedBody = "Hello there!";
    private ProducerTemplate template;

    @BeforeEach
    void setupTemplate() {
        resultEndpoint = contextExtension.getMockEndpoint("mock:result");
        template = contextExtension.getProducerTemplate();
    }

    @Test
    public void testTraceFrame() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.message(0).header("cheese").isEqualTo(123);

        template.sendBodyAndHeader("amqp-with-trace:queue:ping", expectedBody, "cheese", 123);
        resultEndpoint.assertIsSatisfied();
    }

    @ContextFixture
    public void configureContext(CamelContext context) {
        System.setProperty(AMQPConnectionDetails.AMQP_PORT, String.valueOf(service.brokerPort()));
        context.getRegistry().bind("amqpConnection", discoverAMQP(context));

        JmsConnectionFactory connectionFactory
                = new JmsConnectionFactory(service.serviceAddress() + "?amqp.traceFrames=true");

        AMQPComponent amqp = amqpComponent(service.serviceAddress());
        amqp.getConfiguration().setConnectionFactory(connectionFactory);

        context.addComponent("amqp-with-trace", amqp);
    }

    @RouteFixture
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("amqp-with-trace:queue:ping")
                        .to("log:routing")
                        .to("mock:result");
            }
        });
    }
}
