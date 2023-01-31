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
package org.apache.camel.component.jms;

import jakarta.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.test.infra.artemis.common.ConnectionFactoryHelper;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentTransacted;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConsumerTemplateJmsSelectorTest extends AbstractPersistentJMSTest {

    @Test
    public void testJmsSelector() {
        // must start CamelContext because use route builder is false
        context.start();

        template.sendBodyAndHeader("activemq:ConsumerTemplateJmsSelectorTest", "Hello World", "foo", "123");
        template.sendBodyAndHeader("activemq:ConsumerTemplateJmsSelectorTest", "Bye World", "foo", "456");

        String body = consumer.receiveBody("activemq:ConsumerTemplateJmsSelectorTest?selector=foo='456'", 5000, String.class);
        assertEquals("Bye World", body);

        body = consumer.receiveBody("activemq:ConsumerTemplateJmsSelectorTest", 5000, String.class);
        assertEquals("Hello World", body);
    }

    @Override
    protected void createConnectionFactory(CamelContext camelContext) {
        ConnectionFactory connectionFactory = ConnectionFactoryHelper.createConnectionFactory(service);
        JmsComponent component = jmsComponentTransacted(connectionFactory);
        camelContext.addComponent("activemq", component);
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
