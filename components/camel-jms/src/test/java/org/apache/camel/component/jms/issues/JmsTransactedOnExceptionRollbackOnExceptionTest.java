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
package org.apache.camel.component.jms.issues;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Handler;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.CamelJmsTestHelper;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentTransacted;

public class JmsTransactedOnExceptionRollbackOnExceptionTest extends CamelTestSupport {
    
    public static class BadErrorHandler {
        @Handler
        public void onException(Exchange exchange, Exception exception) throws Exception {
            throw new RuntimeException("error in errorhandler");
        }
    }
   
    protected final String testingEndpoint = "activemq:test." + getClass().getName();

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // we attempt to handle the exception but if it throw a new exception
                // then it causes the JMS transaction to rollback
                onException(Exception.class).handled(true).bean(BadErrorHandler.class);

                from(testingEndpoint)
                    .log("Incoming JMS message ${body}")
                    .throwException(new RuntimeException("bad error"));
            }
        };
    }

    @Test
    public void shouldNotLoseMessagesOnExceptionInErrorHandler() throws Exception {
        template.sendBody(testingEndpoint, "Hello World");

        Object dlqBody = consumer.receiveBody("activemq:ActiveMQ.DLQ", 2000);
        assertEquals("Hello World", dlqBody);
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        // no redeliveries
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory(null, 0);
        JmsComponent component = jmsComponentTransacted(connectionFactory);
        camelContext.addComponent("activemq", component);
        return camelContext;
    }

}