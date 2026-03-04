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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CamelLogger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class DefaultErrorHandlerLogExhaustedFalseTest extends ContextTestSupport {

    private final MyLogger logger = new MyLogger();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testLogExhaustedFalse() throws Exception {
        context.addRoutes(createRouteBuilder(false));
        context.start();

        logger.getLines().clear();
        try {
            template.sendBody("direct:start", "Hello World");
            fail();
        } catch (Exception e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Forced", e.getCause().getMessage());
        }
        Assertions.assertEquals(0, logger.getLines().size());
    }

    @Test
    public void testLogExhaustedTrue() throws Exception {
        context.addRoutes(createRouteBuilder(true));
        context.start();

        logger.getLines().clear();
        try {
            template.sendBody("direct:start", "Hello World");
            fail();
        } catch (Exception e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Forced", e.getCause().getMessage());
        }
        Assertions.assertEquals(1, logger.getLines().size());
        Assertions.assertTrue(logger.getLines().get(0).startsWith("Failed delivery"));
    }

    protected RoutesBuilder createRouteBuilder(boolean logExhausted) throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(defaultErrorHandler()
                        .logger(logger)
                        .logExhausted(logExhausted)
                        .logStackTrace(false)
                        .logExhaustedMessageBody(false)
                        .logExhaustedMessageHistory(false));

                from("direct:start")
                        .throwException(new IllegalArgumentException("Forced"));
            }
        };
    }

    private static class MyLogger extends CamelLogger {

        private final List<String> lines = new ArrayList<>();

        @Override
        public void log(String message) {
            lines.add(message);
            super.log(message);
        }

        public List<String> getLines() {
            return lines;
        }
    }
}
