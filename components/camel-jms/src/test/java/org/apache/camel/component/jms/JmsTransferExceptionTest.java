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

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JmsTransferExceptionTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    private static final MyErrorLogger ERROR_LOGGER = new MyErrorLogger();
    private static int counter;
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    protected String getUri() {
        return "activemq:queue:JmsTransferExceptionTest?transferException=true";
    }

    @BeforeEach
    public void setUp() throws Exception {
        counter = 0;
    }

    @Test
    public void testOk() {
        Object out = template.requestBody(getUri(), "Hello World");
        assertEquals("Bye World", out);

        assertEquals(1, counter);
    }

    @Test
    public void testTransferException() {
        // we send something that causes a remote exception
        // then we expect our producer template to throw
        // an exception with the remote exception as cause
        String uri = getUri();
        RuntimeCamelException e = assertThrows(RuntimeCamelException.class, () -> template.requestBody(uri, "Kaboom"),
                "Should have thrown an exception");

        assertEquals("Boom", e.getCause().getMessage());
        assertNotNull(e.getCause().getStackTrace(), "Should contain a remote stacktrace");

        // we still try redeliver
        assertEquals(5, counter);

        // it's all the same exception so no suppressed
        assertEquals(0, e.getSuppressed().length);

        // and check what camel logged
        Throwable t = ERROR_LOGGER.getException();
        assertNotNull(t);
        assertEquals(0, t.getSuppressed().length);
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(defaultErrorHandler().maximumRedeliveries(4).logger(ERROR_LOGGER));

                from(getUri())
                        .process(exchange -> {
                            counter++;

                            String body = exchange.getIn().getBody(String.class);
                            if (body.equals("Kaboom")) {
                                throw new IllegalArgumentException("Boom");
                            }
                            exchange.getMessage().setBody("Bye World");
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
        consumer = camelContextExtension.getConsumerTemplate();
    }

    private static class MyErrorLogger extends CamelLogger {

        private Throwable exception;
        private String message;
        private LoggingLevel loggingLevel;

        @Override
        public void log(String message, Throwable exception, LoggingLevel loggingLevel) {
            super.log(message, exception, loggingLevel);
            this.message = message;
            this.exception = exception;
            this.loggingLevel = loggingLevel;
        }

        public Throwable getException() {
            return exception;
        }

        public String getMessage() {
            return message;
        }

        public LoggingLevel getLoggingLevel() {
            return loggingLevel;
        }
    }
}
