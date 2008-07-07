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

import org.apache.camel.component.spring.integration.HelloWorldService;
import org.apache.camel.spring.SpringTestSupport;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.integration.channel.MessageChannel;
import org.springframework.integration.message.Message;

public class CamelSourceAdapterTest extends SpringTestSupport {
    public void testSendingOneWayMessage() throws Exception {
        MessageChannel channelA = (MessageChannel) applicationContext.getBean("channelA");
        template.sendBody("direct:OneWay", "Willem");
        Message message = channelA.receive();
        assertEquals("We should get the message from channelA", message.getPayload(), "Willem");

    }

    public void testSendingTwoWayMessage() throws Exception {
        String result = (String) template.sendBody("direct:TwoWay", "Willem");
        assertEquals("Can't get the right response", result, "Hello Willem");
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("/org/apache/camel/component/spring/integration/adapter/CamelSource.xml");
    }

}
