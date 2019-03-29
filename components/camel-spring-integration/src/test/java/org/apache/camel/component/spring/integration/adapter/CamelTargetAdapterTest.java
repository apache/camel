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
package org.apache.camel.component.spring.integration.adapter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

public class CamelTargetAdapterTest extends CamelSpringTestSupport {

    private static final String MESSAGE_BODY = "hello world";

    @Test
    public void testSendingOneWayMessage() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedBodiesReceived(MESSAGE_BODY);

        MessageChannel outputChannel = getMandatoryBean(MessageChannel.class, "channelA");
        outputChannel.send(new GenericMessage<Object>(MESSAGE_BODY));

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendingTwoWayMessage() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        MessageChannel requestChannel = getMandatoryBean(MessageChannel.class, "channelB");
        Message<?> message = new GenericMessage<Object>(MESSAGE_BODY);
        //Need to subscribe the responseChannel first
        DirectChannel responseChannel = getMandatoryBean(DirectChannel.class, "channelC");
        responseChannel.subscribe(new MessageHandler() {
            public void handleMessage(Message<?> message) {
                latch.countDown();
                assertEquals("Get the wrong result", MESSAGE_BODY + " is processed",  message.getPayload());
            }            
        });

        requestChannel.send(message);

        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testSendingTwoWayMessageWithMessageAddress() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        MessageChannel requestChannel = getMandatoryBean(MessageChannel.class, "channelD");
        DirectChannel responseChannel = getMandatoryBean(DirectChannel.class, "channelC");
        Map<String, Object> headers = new HashMap<>();
        headers.put(MessageHeaders.REPLY_CHANNEL, responseChannel);
        GenericMessage<String> message = new GenericMessage<>(MESSAGE_BODY, headers);
        responseChannel.subscribe(new MessageHandler() {
            public void handleMessage(Message<?> message) {
                latch.countDown();
                assertEquals("Get the wrong result", MESSAGE_BODY + " is processed",  message.getPayload());
            }            
        });

        requestChannel.send(message);

        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/spring/integration/adapter/CamelTarget.xml");
    }

}
