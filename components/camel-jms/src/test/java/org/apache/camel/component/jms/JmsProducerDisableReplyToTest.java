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
package org.apache.camel.component.jms;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * @version 
 */
public class JmsProducerDisableReplyToTest extends CamelTestSupport {

    @Test
    public void testProducerDisableReplyTo() throws Exception {
        // must start CamelContext because use route builder is false
        context.start();

        String url = "activemq:queue:foo?disableReplyTo=true";
        template.requestBody(url, "Hello World");

        Object out = consumer.receiveBody(url, 5000);
        assertEquals("Hello World", out);
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        // must be persistent so the consumer can receive the message as we receive AFTER the message
        // has been published
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createPersistentConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}