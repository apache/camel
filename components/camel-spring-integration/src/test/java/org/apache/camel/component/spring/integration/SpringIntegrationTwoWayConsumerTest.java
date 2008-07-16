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

package org.apache.camel.component.spring.integration;

import java.util.Map;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringTestSupport;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.integration.channel.MessageChannel;
import org.springframework.integration.message.Message;
import org.springframework.integration.message.StringMessage;


public class SpringIntegrationTwoWayConsumerTest extends SpringTestSupport {
    private static final String MESSAGE_BODY = "Request message";
    public void testDelyConfiguration() throws Exception {
        SpringIntegrationEndpoint endpoint = (SpringIntegrationEndpoint)resolveMandatoryEndpoint("spring-integration://requestChannel?outputChannel=responseChannel&inOut=true&consumer.delay=5000");
        Map map = endpoint.getConsumerProperties();
        assertEquals("There should have a delay property ", map.size(), 1);
        assertEquals("The delay value is not right", map.get("delay"), "5000");
    }

    public void testSendingTwoWayMessage() throws Exception {

        MessageChannel requestChannel = (MessageChannel) applicationContext.getBean("requestChannel");
        Message message = new StringMessage(MESSAGE_BODY);
        message.getHeader().setReturnAddress("responseChannel");
        requestChannel.send(message);

        MessageChannel responseChannel = (MessageChannel) applicationContext.getBean("responseChannel");
        Message responseMessage = responseChannel.receive();
        String result = (String) responseMessage.getPayload();

        assertEquals("Get the wrong result", MESSAGE_BODY + " is processed",  result);
    }

    public ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/spring/integration/twoWayConsumer.xml");
    }


}
