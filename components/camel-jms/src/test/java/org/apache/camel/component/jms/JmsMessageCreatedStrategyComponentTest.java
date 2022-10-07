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

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.activemq.services.ActiveMQService;
import org.junit.jupiter.api.Test;

public class JmsMessageCreatedStrategyComponentTest extends AbstractJMSTest {

    protected final String componentName = "activemq";

    @Test
    public void testMessageCreatedStrategy() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("beer", "Carlsberg");

        template.sendBody("activemq:queue:JmsMessageCreatedStrategyComponentTest", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    public String getComponentName() {
        return componentName;
    }

    @Override
    protected JmsComponent setupComponent(CamelContext camelContext, ActiveMQService service, String componentName) {
        final JmsComponent jms = super.setupComponent(camelContext, service, componentName);
        jms.setMessageCreatedStrategy(new MyMessageCreatedStrategy());
        return jms;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:JmsMessageCreatedStrategyComponentTest")
                        .to("mock:result");
            }
        };
    }

    private static class MyMessageCreatedStrategy implements MessageCreatedStrategy {

        @Override
        public void onMessageCreated(Message message, Session session, Exchange exchange, Throwable cause) {
            try {
                JmsMessageHelper.setProperty(message, "beer", "Carlsberg");
            } catch (JMSException e) {
                // ignore
            }
        }
    }
}
