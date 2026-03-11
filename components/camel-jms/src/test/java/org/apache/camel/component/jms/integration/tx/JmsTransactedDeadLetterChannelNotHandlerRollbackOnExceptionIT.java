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
package org.apache.camel.component.jms.integration.tx;

import jakarta.jms.ConnectionFactory;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Handler;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.test.infra.artemis.common.ConnectionFactoryHelper;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisVMService;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentTransacted;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tags({ @Tag("not-parallel"), @Tag("transaction") })
public class JmsTransactedDeadLetterChannelNotHandlerRollbackOnExceptionIT extends CamelTestSupport {

    private static final String DLQ_NAME
            = "DLQ." + JmsTransactedDeadLetterChannelNotHandlerRollbackOnExceptionIT.class.getSimpleName();

    @RegisterExtension
    public static ArtemisService service = createArtemisService();

    static ArtemisService createArtemisService() {
        ArtemisVMService svc = new ArtemisVMService();
        svc.customConfiguration(cfg -> {
            cfg.getAddressSettings().values()
                    .forEach(s -> {
                        s.setMaxDeliveryAttempts(1);
                        s.setDeadLetterAddress(SimpleString.of(DLQ_NAME));
                    });
            cfg.addQueueConfiguration(
                    QueueConfiguration.of(DLQ_NAME).setRoutingType(RoutingType.ANYCAST));
        });
        return svc;
    }

    public static class BadErrorHandler {
        @Handler
        public void onException(Exchange exchange, Exception exception) {
            throw new RuntimeCamelException("error in errorhandler");
        }
    }

    private final String testingEndpoint = "activemq:test." + getClass().getName();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // we use DLC to handle the exception but if it throw a new exception
                // then the DLC does NOT handle that (the transaction will rollback)
                errorHandler(deadLetterChannel("bean:" + BadErrorHandler.class.getName())
                        .deadLetterHandleNewException(false)
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

        // as we do not handle new exception, then the exception propagates back
        // and causes the transaction to rollback, and we can find the message in the DLQ
        Object dlqBody = consumer.receiveBody("activemq:" + DLQ_NAME, 30000);
        assertEquals("Hello World", dlqBody);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = ConnectionFactoryHelper.createConnectionFactory(service, 0);

        JmsComponent component = jmsComponentTransacted(connectionFactory);
        camelContext.addComponent("activemq", component);
        return camelContext;
    }

}
