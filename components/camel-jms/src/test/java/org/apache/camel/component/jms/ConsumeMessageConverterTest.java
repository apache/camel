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

import javax.jms.BytesMessage;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.springframework.jms.support.converter.MessageConversionException;
import org.springframework.jms.support.converter.MessageConverter;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentClientAcknowledge;

/**
 * @version $Revision$
 */
public class ConsumeMessageConverterTest extends ContextTestSupport {

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
        camelContext.addComponent("activemq", jmsComponentClientAcknowledge(connectionFactory));

        return camelContext;
    }

    public void testTextMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(TextMessage.class);

        template.sendBody("activemq:queue:hello", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testBytesMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(BytesMessage.class);

        template.sendBody("activemq:queue:hello", "Hello World".getBytes());

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // added disableReplyTo=false to make sure the endpoint is different than the ones we use for
                // sending otherwise they would also use the same custom message converter
                JmsEndpoint endpoint = context.getEndpoint("activemq:queue:hello?disableReplyTo=false", JmsEndpoint.class);
                endpoint.getConfiguration().setMessageConverter(new MyMessageConverter());

                from(endpoint).to("mock:result");
            }
        };
    }

    private class MyMessageConverter implements MessageConverter {

        public Message toMessage(Object object, Session session) throws JMSException, MessageConversionException {
            return null;
        }

        public Object fromMessage(Message message) throws JMSException, MessageConversionException {
            // just return the underlying JMS message directly so we can test that this converter is used
            return message;
        }
    }

}
