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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With source location off there is no line to name, so the failed delivery message says the route and the node and
 * stops there rather than inventing one (CAMEL-24974).
 */
public class FailedDeliveryOriginNoSourceLocationTest extends ContextTestSupport {

    /** Such as: at foo[throwException1] ContextTestSupport:480 */
    private static final Pattern ORIGIN = Pattern.compile(" at foo\\[\\w+] \\S+:\\d+");

    /** The route and node alone, with nothing after them: node ids are numbered per JVM, so match the shape. */
    private static final Pattern NODE_ONLY = Pattern.compile(" at foo\\[\\w+]\\.");

    private final RecordingLogger logger = new RecordingLogger();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setMessageHistory(true);
        context.setSourceLocationEnabled(false);
        return context;
    }

    @Test
    public void testTheRouteAndNodeWithoutASourceLocation() throws Exception {
        getMockEndpoint("mock:dead").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        String msg = lastFailure();
        assertTrue(msg.contains("Failed delivery for"), msg);
        assertTrue(NODE_ONLY.matcher(msg).find(), "Expected the route and node in: " + msg);
        assertFalse(ORIGIN.matcher(msg).find(), "There is no line to name, so none is named: " + msg);
    }

    private String lastFailure() {
        assertFalse(logger.messages.isEmpty(), "The error handler should have logged the exhausted delivery");
        return logger.messages.get(logger.messages.size() - 1);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:dead").logExhausted(true).logger(logger));

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
            super(LoggerFactory.getLogger(FailedDeliveryOriginNoSourceLocationTest.class), LoggingLevel.ERROR);
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
