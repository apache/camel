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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.jms.MessageFormatException;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A body that maps to no JMS message type must fail the exchange, and not be sent as an empty JMS message.
 */
public class JmsUnmappableBodyTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();

    private static final AtomicInteger REPLY_COUNTER = new AtomicInteger();

    protected CamelContext context;
    protected ProducerTemplate template;

    @Override
    protected String getComponentName() {
        return "jms";
    }

    @Test
    public void testProducerFailsOnUnmappableBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("jms:queue:JmsUnmappableBodyTest", new MyPojo("World")));
        MessageFormatException cause = findCause(e, MessageFormatException.class);
        assertNotNull(cause, "Expected a MessageFormatException, got: " + e);
        assertTrue(cause.getMessage().contains(MyPojo.class.getName()), cause.getMessage());
        assertTrue(cause.getMessage().contains("java.io.Serializable"), cause.getMessage());
        assertTrue(cause.getMessage().contains("jmsMessageType"), cause.getMessage());
        assertTrue(cause.getMessage().contains("marshal the body"), cause.getMessage());

        mock.assertIsSatisfied(1000);
    }

    @Test
    public void testProducerSendsNullBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isNull();

        template.sendBody("jms:queue:JmsUnmappableBodyTest", null);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testReplyFailsOnUnmappableBody() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("jms:queue:JmsUnmappableBodyTest.reply?requestTimeout=2000", "Hello World"));
        assertInstanceOf(ExchangeTimedOutException.class, e.getCause());

        // the reply could not be sent, so the request is rolled back and redelivered
        await().atMost(10, TimeUnit.SECONDS).until(() -> REPLY_COUNTER.get() >= 2);
    }

    private static <T extends Throwable> T findCause(Throwable t, Class<T> type) {
        Throwable cause = t;
        while (cause != null) {
            if (type.isInstance(cause)) {
                return type.cast(cause);
            }
            cause = cause.getCause() != cause ? cause.getCause() : null;
        }
        return null;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jms:queue:JmsUnmappableBodyTest").to("mock:result");

                from("jms:queue:JmsUnmappableBodyTest.reply?transacted=true")
                        .process(exchange -> {
                            REPLY_COUNTER.incrementAndGet();
                            exchange.getMessage().setBody(new MyPojo("Bye"));
                        });
            }
        };
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
    }

    /**
     * A POJO that is not Serializable and has no type converter to a JMS body.
     */
    public static class MyPojo {
        private final String name;

        public MyPojo(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
