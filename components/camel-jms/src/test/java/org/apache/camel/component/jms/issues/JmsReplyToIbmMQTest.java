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
package org.apache.camel.component.jms.issues;

import jakarta.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.ibmmq.common.ConnectionFactoryHelper;
import org.apache.camel.test.infra.ibmmq.services.IbmMQService;
import org.apache.camel.test.infra.ibmmq.services.IbmMQServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Flaky on GitHub Actions")
public class JmsReplyToIbmMQTest extends CamelTestSupport {

    @RegisterExtension
    public static IbmMQService service = IbmMQServiceFactory.createService();

    @Test
    public void testCustomJMSReplyToInOut() {
        template.sendBody("jms:queue:DEV.QUEUE.1", "What is your name?");

        String reply
                = consumer.receiveBody("jms:queue:DEV.QUEUE.2", 5000, String.class);
        assertEquals("My name is Camel", reply);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jms:queue:DEV.QUEUE.1?replyTo=queue:DEV.QUEUE.2")
                        .to("log:hello")
                        .transform(constant("My name is Camel"));
            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory
                = ConnectionFactoryHelper.createConnectionFactory(
                        service.queueManager(), service.channel(), service.listenerPort());
        camelContext.addComponent("jms", jmsComponentAutoAcknowledge(connectionFactory));
        return camelContext;
    }
}
