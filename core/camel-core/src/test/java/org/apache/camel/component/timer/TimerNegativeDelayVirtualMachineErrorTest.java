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
package org.apache.camel.component.timer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.awaitility.Awaitility.await;

/**
 * Verifies that a VirtualMachineError (StackOverflowError) thrown in a timer route with a negative delay (executor
 * path) is caught by the executor loop's catch (Throwable) block and logged at ERROR. Without the fix the error
 * propagates uncaught and kills the executor thread silently — the ERROR log line is never emitted.
 *
 * @see <a href="https://issues.apache.org/jira/browse/CAMEL-27470">CAMEL-27470</a>
 */
@Isolated
class TimerNegativeDelayVirtualMachineErrorTest extends ContextTestSupport {

    private final List<String> capturedErrors = new CopyOnWriteArrayList<>();
    private AbstractAppender appender;

    /**
     * Register the log appender before the CamelContext starts. With delay=-1 the executor fires immediately on context
     * start, so the appender must be in place before startCamelContext() runs (which happens inside setUp()). Using
     * doPreSetup() ensures registration happens before ContextTestSupport.setUp() starts the context.
     */
    @Override
    protected void doPreSetup() {
        appender = new AbstractAppender(
                "timer-consumer-errors-" + UUID.randomUUID(), null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getLoggerName().contains("TimerConsumer") && event.getLevel() == Level.ERROR) {
                    capturedErrors.add(event.getMessage().getFormattedMessage());
                }
            }
        };
        appender.start();
        LoggerContext logging = (LoggerContext) LogManager.getContext(false);
        logging.getConfiguration().getRootLogger().addAppender(appender, Level.ERROR, null);
        logging.updateLoggers();
        super.doPreSetup();
    }

    @Override
    protected void doPostTearDown() {
        try {
            if (appender != null) {
                LoggerContext logging = (LoggerContext) LogManager.getContext(false);
                logging.getConfiguration().getRootLogger().removeAppender(appender.getName());
                logging.updateLoggers();
                appender.stop();
            }
        } finally {
            super.doPostTearDown();
        }
    }

    @Test
    void negativeDelayRouteLoopTerminatesAndLogsErrorOnVirtualMachineError() {
        // The executor path catches Throwable: when a StackOverflowError escapes
        // getProcessor().process(), the catch block must run and emit the ERROR log.
        // Without the fix the error propagates uncaught and the ERROR is never logged.
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> capturedErrors.stream().anyMatch(msg -> msg.contains("has been terminated")));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("timer:negDelay?delay=-1&period=0&synchronous=true")
                        .routeId("route-neg-delay")
                        .process(exchange -> {
                            throw new StackOverflowError("simulated StackOverflowError in negative-delay route");
                        });
            }
        };
    }
}
