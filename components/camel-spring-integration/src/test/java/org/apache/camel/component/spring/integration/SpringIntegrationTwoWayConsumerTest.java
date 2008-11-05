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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.spring.SpringTestSupport;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.integration.channel.AbstractPollableChannel;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.Message;
import org.springframework.integration.core.MessageChannel;
import org.springframework.integration.core.MessageHeaders;
import org.springframework.integration.message.GenericMessage;
import org.springframework.integration.message.MessageHandler;


public class SpringIntegrationTwoWayConsumerTest extends SpringTestSupport {
    private static final String MESSAGE_BODY = "Request message";    

    public void testSendingTwoWayMessage() throws Exception {
        
        MessageChannel requestChannel = (MessageChannel) applicationContext.getBean("requestChannel");
        Map<String, Object> maps = new HashMap<String, Object>();
        maps.put(MessageHeaders.REPLY_CHANNEL, "responseChannel");
        Message<String> message = new GenericMessage<String>(MESSAGE_BODY, maps);
        DirectChannel responseChannel = (DirectChannel) applicationContext.getBean("responseChannel");
        responseChannel.subscribe(new MessageHandler() {
            public void handleMessage(Message<?> message) {
                String result = (String) message.getPayload();
                assertEquals("Get the wrong result", MESSAGE_BODY + " is processed",  result);                
            }             
        });
        requestChannel.send(message);        
        
    }

    public ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/spring/integration/twoWayConsumer.xml");
    }


}
