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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.ExceptionListener;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.ExceptionHandler;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The exceptionHandler of a consumer gets the JMS exceptions of its listener container, such as a connection to the
 * broker that cannot be established.
 */
public class JmsConsumerExceptionHandlerTest {

    @Test
    public void testExceptionHandlerGetsTheConnectionErrors() throws Exception {
        FailingConnectionFactory cf = new FailingConnectionFactory();
        RecordingExceptionHandler handler = new RecordingExceptionHandler();
        try (CamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("handler", handler);
            context.addComponent("jms", JmsComponent.jmsComponent(cf));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("jms:queue:JmsConsumerExceptionHandlerTest.a?exceptionHandler=#handler&recoveryInterval=100")
                            .to("mock:a");
                }
            });
            context.start();

            await().atMost(10, TimeUnit.SECONDS).until(() -> handler.exceptions.size() >= 3);

            assertEquals("JMS exception in the listener container of the consumer on destination:"
                         + " JmsConsumerExceptionHandlerTest.a",
                    handler.messages.get(0));
            JMSException exception = assertInstanceOf(JMSException.class, handler.exceptions.get(0));
            assertEquals("Broker is down", exception.getMessage());
        }
    }

    @Test
    public void testExceptionHandlerAndExceptionListenerGetTheConnectionErrors() throws Exception {
        FailingConnectionFactory cf = new FailingConnectionFactory();
        RecordingExceptionHandler handler = new RecordingExceptionHandler();
        RecordingExceptionListener listener = new RecordingExceptionListener();
        try (CamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("handler", handler);
            context.getRegistry().bind("listener", listener);
            context.addComponent("jms", JmsComponent.jmsComponent(cf));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("jms:queue:JmsConsumerExceptionHandlerTest.b"
                         + "?exceptionHandler=#handler&exceptionListener=#listener&recoveryInterval=100")
                            .to("mock:b");
                }
            });
            context.start();

            await().atMost(10, TimeUnit.SECONDS)
                    .until(() -> handler.exceptions.size() >= 3 && listener.exceptions.size() >= 3);

            assertEquals("Broker is down", listener.exceptions.get(0).getMessage());
            assertEquals("Broker is down", handler.exceptions.get(0).getMessage());
        }
    }

    @Test
    public void testExceptionListenerAloneGetsTheConnectionErrors() throws Exception {
        FailingConnectionFactory cf = new FailingConnectionFactory();
        RecordingExceptionListener listener = new RecordingExceptionListener();
        try (CamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("listener", listener);
            context.addComponent("jms", JmsComponent.jmsComponent(cf));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("jms:queue:JmsConsumerExceptionHandlerTest.c?exceptionListener=#listener&recoveryInterval=100")
                            .routeId("c").to("mock:c");
                }
            });
            context.start();

            await().atMost(10, TimeUnit.SECONDS).until(() -> listener.exceptions.size() >= 3);

            assertEquals("Broker is down", listener.exceptions.get(0).getMessage());
            JmsConsumer consumer = (JmsConsumer) context.getRoute("c").getConsumer();
            assertEquals(listener, consumer.getListenerContainer().getExceptionListener());
        }
    }

    @Test
    public void testNoExceptionListenerWithoutTheOptions() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.addComponent("jms", JmsComponent.jmsComponent(new FailingConnectionFactory()));
            context.start();

            JmsEndpoint endpoint = context.getEndpoint("jms:queue:JmsConsumerExceptionHandlerTest.d", JmsEndpoint.class);
            JmsConsumer consumer = endpoint.createConsumer(exchange -> {
            });
            assertNull(consumer.getListenerContainer().getExceptionListener());
        }
    }

    @Test
    public void testFailingExceptionHandlerDoesNotStopTheRecovery() throws Exception {
        FailingConnectionFactory cf = new FailingConnectionFactory();
        RecordingExceptionHandler handler = new RecordingExceptionHandler(true);
        try (CamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("handler", handler);
            context.addComponent("jms", JmsComponent.jmsComponent(cf));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("jms:queue:JmsConsumerExceptionHandlerTest.e?exceptionHandler=#handler&recoveryInterval=100")
                            .to("mock:e");
                }
            });
            context.start();

            // the container goes on trying to connect after the handler failed
            await().atMost(10, TimeUnit.SECONDS).until(() -> handler.exceptions.size() >= 3 && cf.attempts.get() >= 4);
        }
    }

    @Test
    public void testBridgeErrorHandlerIsRejected() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.addComponent("jms", JmsComponent.jmsComponent(new FailingConnectionFactory()));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("jms:queue:JmsConsumerExceptionHandlerTest.f?bridgeErrorHandler=true").to("mock:f");
                }
            });

            Exception e = assertThrows(Exception.class, context::start);
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            assertInstanceOf(IllegalArgumentException.class, root);
            assertTrue(root.getMessage().startsWith("BridgeErrorHandler is not supported on JMS endpoint"),
                    root.getMessage());
            assertTrue(root.getMessage().contains("use the exceptionHandler or exceptionListener option"), root.getMessage());
        }
    }

    private static final class FailingConnectionFactory implements ConnectionFactory {
        private final AtomicInteger attempts = new AtomicInteger();

        @Override
        public Connection createConnection() throws JMSException {
            attempts.incrementAndGet();
            throw new JMSException("Broker is down");
        }

        @Override
        public Connection createConnection(String userName, String password) throws JMSException {
            return createConnection();
        }

        @Override
        public JMSContext createContext() {
            throw new UnsupportedOperationException();
        }

        @Override
        public JMSContext createContext(String userName, String password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JMSContext createContext(String userName, String password, int sessionMode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JMSContext createContext(int sessionMode) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingExceptionHandler implements ExceptionHandler {
        private final List<String> messages = new CopyOnWriteArrayList<>();
        private final List<Throwable> exceptions = new CopyOnWriteArrayList<>();
        private final boolean fail;

        RecordingExceptionHandler() {
            this(false);
        }

        RecordingExceptionHandler(boolean fail) {
            this.fail = fail;
        }

        @Override
        public void handleException(Throwable exception) {
            handleException(null, null, exception);
        }

        @Override
        public void handleException(String message, Throwable exception) {
            handleException(message, null, exception);
        }

        @Override
        public void handleException(String message, Exchange exchange, Throwable exception) {
            messages.add(String.valueOf(message));
            exceptions.add(exception);
            if (fail) {
                throw new IllegalStateException("Handler failed");
            }
        }
    }

    private static final class RecordingExceptionListener implements ExceptionListener {
        private final List<JMSException> exceptions = new CopyOnWriteArrayList<>();

        @Override
        public void onException(JMSException exception) {
            exceptions.add(exception);
        }
    }
}
