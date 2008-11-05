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

import org.apache.camel.ExchangePattern;
import org.apache.camel.component.spring.integration.HelloWorldService;
import org.apache.camel.spring.SpringTestSupport;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.PollableChannel;
import org.springframework.integration.core.Message;
import org.springframework.integration.message.MessageHandler;

public class CamelSourceAdapterTest extends SpringTestSupport {
    public void testSendingOneWayMessage() throws Exception {
        DirectChannel channelA = (DirectChannel) applicationContext.getBean("channelA");
        channelA.subscribe(new MessageHandler() {
            public void handleMessage(Message<?> message) {
                assertEquals("We should get the message from channelA", message.getPayload(), "Willem");             
            }            
        });
        template.sendBody("direct:OneWay", "Willem");
    }

    public void testSendingTwoWayMessage() throws Exception {
        String result = (String) template.sendBody("direct:TwoWay", ExchangePattern.InOut, "Willem");
        assertEquals("Can't get the right response", result, "Hello Willem");
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("/org/apache/camel/component/spring/integration/adapter/CamelSource.xml");
    }

}
