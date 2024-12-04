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
package org.apache.camel.component.sjms2.consumer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms2.Sjms2Component;
import org.apache.camel.component.sjms2.support.Jms2TestSupport;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;

public class InOnlyTopicDurableConsumerTest extends Jms2TestSupport {

    private static final String CONNECTION_ID = "test-connection-1";
    private CountDownLatch latch = new CountDownLatch(2);
    @RegisterExtension
    public static ArtemisService service = ArtemisServiceFactory.createTCPAllProtocolsService();

    @Test
    public void testDurableTopic() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        MockEndpoint mock2 = getMockEndpoint("mock:result2");
        mock2.expectedBodiesReceived("Hello World");

        // wait a bit and send the message
        template.sendBody("sjms2:topic:foo", "Hello World");

        if (!latch.await(1000, TimeUnit.MILLISECONDS)) {
            Assertions.fail("Message is not received as expected");
        }

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        Sjms2Component sjms = context.getComponent("sjms2", Sjms2Component.class);

        // need to use a pooled CF so we reuse same connection for multiple client connections
        JmsPoolConnectionFactory pcf = new JmsPoolConnectionFactory();
        pcf.setConnectionFactory(sjms.getConnectionFactory());
        sjms.setConnectionFactory(pcf);
        sjms.setClientId(CONNECTION_ID);

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("sjms2:topic:foo?durableSubscriptionName=bar1")
                        .process(e -> latch.countDown())
                        .to("mock:result");

                from("sjms2:topic:foo?durableSubscriptionName=bar2")
                        .process(e -> latch.countDown())
                        .to("mock:result2");
            }
        };
    }

    protected ConnectionFactory getConnectionFactory() throws Exception {
        return getConnectionFactory(service.serviceAddress());
    }
}
