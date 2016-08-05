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

import static org.apache.camel.component.jms.JmsComponent.jmsComponentTransacted;

/**
 * @version 
 */
public class ConsumerTemplateJmsSelectorTest extends CamelTestSupport {

    @Test
    public void testJmsSelector() throws Exception {
        // must start CamelContext because use route builder is false
        context.start();

        template.sendBodyAndHeader("activemq:foo", "Hello World", "foo", "123");
        template.sendBodyAndHeader("activemq:foo", "Bye World", "foo", "456");

        String body = consumer.receiveBody("activemq:foo?selector=foo='456'", 5000, String.class);
        assertEquals("Bye World", body);

        body = consumer.receiveBody("activemq:foo", 5000, String.class);
        assertEquals("Hello World", body);
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        // must be persistent to rember the messages
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createPersistentConnectionFactory();
        JmsComponent component = jmsComponentTransacted(connectionFactory);
        camelContext.addComponent("activemq", component);
        return camelContext;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}