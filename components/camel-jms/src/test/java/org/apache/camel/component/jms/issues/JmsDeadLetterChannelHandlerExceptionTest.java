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


import java.util.UUID;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Handler;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.CamelJmsTestHelper;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentTransacted;

public class JmsDeadLetterChannelHandlerExceptionTest extends CamelTestSupport {
    
    public static class BadErrorHandler {
        @Handler
        public void onException(Exchange exchange, Exception exception) throws Exception {
            
            throw new RuntimeException("error in errorhandler");
        }
    }
   
    private final String testingEndpoint = "activemq:test." + getClass().getName();

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                
                errorHandler(deadLetterChannel("bean:" + BadErrorHandler.class.getName()).checkException());

                from(testingEndpoint).throwException(new RuntimeException("bad error"));
                
                
            }
        };
    }

    @Test
    public void shouldNotLoseMessagesOnExceptionInErrorHandler() throws Exception {
        UUID message = UUID.randomUUID();
        template.sendBody(testingEndpoint, message);

        Object dlqBody = consumer.receiveBody("activemq:ActiveMQ.DLQ", 3000);

        assertEquals(message, dlqBody);
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentTransacted(connectionFactory));
        return camelContext;
    }
}