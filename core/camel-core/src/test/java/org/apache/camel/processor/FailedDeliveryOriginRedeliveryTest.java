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
package org.apache.camel.processor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CamelLogger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every failed delivery message says where the failure happened, on a retry as well as when the delivery is exhausted
 * (CAMEL-24974).
 */
public class FailedDeliveryOriginRedeliveryTest extends ContextTestSupport {

    /** Such as: at foo[throwException1] ContextTestSupport:480 */
    private static final Pattern ORIGIN = Pattern.compile(" at foo\\[\\w+] \\S+:\\d+");

    private final RecordingLogger logger = new RecordingLogger();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setMessageHistory(true);
        context.setSourceLocationEnabled(true);
        return context;
    }

    @Test
    public void testEveryAttemptSaysWhere() throws Exception {
        try {
            template.sendBody("direct:start", "Hello World");
        } catch (Exception e) {
            // expected: the default error handler rethrows once redelivery is exhausted
        }

        // two attempts and then the exhausted message, all built from the captured failure origin
        assertEquals(3, logger.messages.size(), "Expected two attempts and an exhausted message: " + logger.messages);
        for (String msg : logger.messages) {
            assertTrue(msg.contains("Failed delivery for"), msg);
            assertTrue(ORIGIN.matcher(msg).find(), "Expected the route, node and source location in: " + msg);
        }
        assertTrue(logger.messages.stream().anyMatch(m -> m.contains("On delivery attempt")), logger.messages.toString());
        assertTrue(logger.messages.stream().anyMatch(m -> m.contains("Exhausted after delivery attempt")),
                logger.messages.toString());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(defaultErrorHandler()
                        .maximumRedeliveries(1).redeliveryDelay(0)
                        .logRetryAttempted(true).logExhausted(true).logger(logger));

                from("direct:start").routeId("foo")
                        .to("log:before")
                        .throwException(new IllegalArgumentException("Forced error"));
            }
        };
    }

    /** Keeps what the error handler logged, so the test can read the message instead of the log file. */
    private static final class RecordingLogger extends CamelLogger {

        private final List<String> messages = new CopyOnWriteArrayList<>();

        private RecordingLogger() {
            super(LoggerFactory.getLogger(FailedDeliveryOriginRedeliveryTest.class), LoggingLevel.ERROR);
        }

        @Override
        public void log(String message, Throwable exception, LoggingLevel loggingLevel) {
            messages.add(message);
            super.log(message, exception, loggingLevel);
        }

        @Override
        public void log(String message, LoggingLevel loggingLevel) {
            messages.add(message);
            super.log(message, loggingLevel);
        }
    }
}
