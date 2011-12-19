/**
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

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.integration.Message;
import org.springframework.integration.MessageChannel;
import org.springframework.integration.MessageHeaders;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageHandler;
import org.springframework.integration.message.GenericMessage;

public class CamelTargetAdapterTest extends CamelSpringTestSupport {
    private static final String MESSAGE_BODY = "hello world";

    @Test
    public void testSendingOneWayMessage() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedBodiesReceived(MESSAGE_BODY);
        MessageChannel outputChannel = applicationContext.getBean("channelA", MessageChannel.class);
        outputChannel.send(new GenericMessage<Object>(MESSAGE_BODY));
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testSendingTwoWayMessage() throws Exception {

        MessageChannel requestChannel = applicationContext.getBean("channelB", MessageChannel.class);
        Message message = new GenericMessage<Object>(MESSAGE_BODY);
        //Need to subscribe the responseChannel first
        DirectChannel responseChannel = (DirectChannel) applicationContext.getBean("channelC");
        responseChannel.subscribe(new MessageHandler() {
            public void handleMessage(Message<?> message) {
                String result = (String) message.getPayload();
                assertEquals("Get the wrong result", MESSAGE_BODY + " is processed",  result);                
            }            
        });
        requestChannel.send(message);
    }

    @Test
    public void testSendingTwoWayMessageWithMessageAddress() throws Exception {

        MessageChannel requestChannel = applicationContext.getBean("channelD", MessageChannel.class);
        DirectChannel responseChannel = applicationContext.getBean("channelC", DirectChannel.class);
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(MessageHeaders.REPLY_CHANNEL, responseChannel);
        GenericMessage<String> message = new GenericMessage<String>(MESSAGE_BODY, headers);
        responseChannel.subscribe(new MessageHandler() {
            public void handleMessage(Message<?> message) {
                String result = (String) message.getPayload();
                assertEquals("Get the wrong result", MESSAGE_BODY + " is processed",  result);                
            }            
        });
        requestChannel.send(message);        
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/spring/integration/adapter/CamelTarget.xml");
    }

}
