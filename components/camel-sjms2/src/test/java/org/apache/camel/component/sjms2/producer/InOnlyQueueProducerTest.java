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
package org.apache.camel.component.sjms2.producer;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.TextMessage;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsConstants;
import org.apache.camel.component.sjms2.support.Jms2TestSupport;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.infra.artemis.services.ArtemisTestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InOnlyQueueProducerTest extends Jms2TestSupport {

    private static final String TEST_DESTINATION_NAME = "sync.queue.producer.test";
    @RegisterExtension
    public static ArtemisTestService service = ArtemisServiceFactory.createTCPAllProtocolsService();

    @Test
    public void testInOnlyQueueProducer() throws Exception {
        MessageConsumer mc = createQueueConsumer(TEST_DESTINATION_NAME);
        assertNotNull(mc);
        final String expectedBody = "Hello World!";
        MockEndpoint mock = getMockEndpoint("mock:result");

        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(expectedBody);

        template.sendBody("direct:start", expectedBody);
        Message message = mc.receive(5000);
        assertNotNull(message);
        assertTrue(message instanceof TextMessage);

        TextMessage tm = (TextMessage) message;
        String text = tm.getText();
        assertNotNull(text);

        template.sendBody("direct:finish", text);

        mock.assertIsSatisfied();
        mc.close();
    }

    @Test
    public void testInOnlyQueueProducerHeader() throws Exception {
        MessageConsumer mc = createQueueConsumer("foo");
        assertNotNull(mc);

        final String expectedBody = "Hello World!";
        MockEndpoint mock = getMockEndpoint("mock:result");

        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(expectedBody);

        template.sendBodyAndHeader("direct:start", expectedBody, SjmsConstants.JMS_DESTINATION_NAME, "foo");
        Message message = mc.receive(5000);
        assertNotNull(message);
        assertTrue(message instanceof TextMessage);

        TextMessage tm = (TextMessage) message;
        String text = tm.getText();
        assertNotNull(text);

        template.sendBody("direct:finish", text);

        mock.assertIsSatisfied();
        mc.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        //                        .setHeader("JMSDeliveryMode", constant(1))
                        .to("sjms2:queue:" + TEST_DESTINATION_NAME + "?deliveryMode=1");

                from("direct:finish")
                        .to("log:test.log.1?showBody=true", "mock:result");
            }
        };
    }

    protected ConnectionFactory getConnectionFactory() throws Exception {
        return getConnectionFactory(service.serviceAddress());
    }
}
