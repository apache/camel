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

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Handler;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.test.infra.activemq.common.ConnectionFactoryHelper;
import org.apache.camel.test.infra.activemq.services.ActiveMQService;
import org.apache.camel.test.infra.activemq.services.ActiveMQServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentTransacted;
import static org.junit.jupiter.api.Assertions.assertNull;

// This test cannot run in parallel: it reads from the default DLQ and there could be more messages there
@Tags({ @Tag("not-parallel"), @Tag("transaction") })
public class JmsTransactedDeadLetterChannelHandlerRollbackOnExceptionTest extends CamelTestSupport {
    @RegisterExtension
    public ActiveMQService service = ActiveMQServiceFactory.createVMServiceInstance();

    public static class BadErrorHandler {
        @Handler
        public void onException(Exchange exchange, Exception exception) {
            throw new RuntimeCamelException("error in errorhandler");
        }
    }

    protected final String testingEndpoint = "activemq:test." + getClass().getName();

    protected boolean isHandleNew() {
        return true;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // we use DLC to handle the exception but if it throw a new exception
                // then the DLC handles that too (the transaction will always commit)
                errorHandler(deadLetterChannel("bean:" + BadErrorHandler.class.getName())
                        .deadLetterHandleNewException(isHandleNew())
                        .logNewException(true));

                from(testingEndpoint)
                        .log("Incoming JMS message ${body}")
                        .throwException(new RuntimeCamelException("bad error"));
            }
        };
    }

    @Test
    public void shouldNotLoseMessagesOnExceptionInErrorHandler() {
        template.sendBody(testingEndpoint, "Hello World");

        // as we handle new exception, then the exception is ignored
        // and causes the transaction to commit, so there is no message in the ActiveMQ DLQ queue
        Object dlqBody = consumer.receiveBody("activemq:ActiveMQ.DLQ", 2000);
        assertNull(dlqBody, "Should not rollback the transaction");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        // no redeliveries
        ConnectionFactory connectionFactory = ConnectionFactoryHelper.createConnectionFactory(service, 0);

        JmsComponent component = jmsComponentTransacted(connectionFactory);
        camelContext.addComponent("activemq", component);
        return camelContext;
    }

}
