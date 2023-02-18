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
package org.apache.camel.component.log;

import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.AsyncCallback;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.LogListener;
import org.apache.camel.support.processor.CamelLogProcessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class LogEndpointTest extends ContextTestSupport {

    private static Exchange logged;

    @AfterAll
    public static void clean() {
        logged = null;
    }

    private static class MyLogger extends CamelLogProcessor {

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            logged = exchange;
            return super.process(exchange, callback);
        }

        @Override
        public String toString() {
            return "myLogger";
        }
    }

    private static class TestLogListener implements LogListener {

        private final AtomicReference<String> logged;

        public TestLogListener(AtomicReference<String> logged) {
            this.logged = logged;
        }

        @Override
        public String onLog(Exchange exchange, CamelLogger camelLogger, String message) {
            logged.set(message);
            return message;
        }
    }

    @Test
    public void testLogEndpoint() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start1", "Hello World");

        assertMockEndpointsSatisfied();

        assertNotNull(logged);
    }

    @Test
    public void testLogEndpointGroupSize() throws InterruptedException {
        MockEndpoint out = getMockEndpoint("mock:result");
        int expectedCount = 50;
        out.expectedMessageCount(expectedCount);
        for (int i = 0; i < expectedCount; i++) {
            template.sendBody("direct:start2", "blub");
        }
        out.assertIsSatisfied();
    }

    @Test
    public void testShowCaughtException() {
        final AtomicReference<String> logged = new AtomicReference<>();
        context.getCamelContextExtension().addLogListener(new TestLogListener(logged));
        Exchange ex = createExchangeWithBody(null);
        ex.setProperty(Exchange.EXCEPTION_CAUGHT, new RuntimeException("test"));
        template.send("log:testShowCaughtException?showCaughtException=true", ex);
        assertEquals(
                "Exchange[BodyType: null, Body: [Body is null], CaughtExceptionType: java.lang.RuntimeException, CaughtExceptionMessage: test]",
                logged.get());
    }

    @Test
    public void testShowException() {
        final AtomicReference<String> logged = new AtomicReference<>();
        context.getCamelContextExtension().addLogListener(new TestLogListener(logged));
        Exchange ex = createExchangeWithBody(null);
        ex.setException(new RuntimeException("test"));
        template.send("log:testShowException?showException=true", ex);
        assertEquals(
                "Exchange[BodyType: null, Body: [Body is null], ExceptionType: java.lang.RuntimeException, ExceptionMessage: test]",
                logged.get());
    }

    @Test
    public void testPlain() {
        String body = "Body as string";
        final AtomicReference<String> logged = new AtomicReference<>();
        context.getCamelContextExtension().addLogListener(new TestLogListener(logged));
        Exchange ex = createExchangeWithBody(body);
        template.send("log:info?plain=true", ex);
        assertEquals(body, logged.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                LogEndpoint end = new LogEndpoint();
                end.setComponent(context.getComponent("log"));
                end.setCamelContext(context);
                end.setLogger(new MyLogger());

                LogEndpoint endpoint = new LogEndpoint();
                endpoint.setLoggerName("loggerSetter");
                endpoint.setGroupSize(10);
                endpoint.setCamelContext(context);
                endpoint.setComponent(context.getComponent("log"));
                endpoint.start();

                assertEquals("log:myLogger", end.getEndpointUri());

                from("direct:start1").to(end).to("mock:result");

                from("direct:start2").to(endpoint).to("mock:result");
            }
        };
    }
}
