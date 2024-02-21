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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.support.processor.DefaultExchangeFormatter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Logger formatter test.
 */
public class DefaultExchangeFormatterTest extends ContextTestSupport {

    @Test
    public void testSendMessageToLogDefault() {
        assertDoesNotThrow(() -> template.sendBody("log:org.apache.camel.TEST", "Hello World"));
    }

    @Test
    public void testSendMessageToLogAllOff() {
        assertDoesNotThrow(
                () -> template.sendBody("log:org.apache.camel.TEST?showBody=false&showBodyType=false&showExchangePattern=false",
                        "Hello World"));
    }

    @Test
    public void testSendMessageToLogSingleOptions() {
        assertDoesNotThrow(() -> template.sendBody("log:org.apache.camel.TEST?showExchangeId=true", "Hello World"));
        assertDoesNotThrow(() -> template.sendBody("log:org.apache.camel.TEST?showExchangePattern=true", "Hello World"));
        assertDoesNotThrow(() -> template.sendBody("log:org.apache.camel.TEST?showExchangePattern=false", "Hello World"));
        assertDoesNotThrow(() -> template.sendBody("log:org.apache.camel.TEST?showProperties=true", "Hello World"));
        assertDoesNotThrow(() -> template.sendBody("log:org.apache.camel.TEST?showHeaders=true", "Hello World"));
        assertDoesNotThrow(() -> template.sendBody("log:org.apache.camel.TEST?showBodyType=true", "Hello World"));
        assertDoesNotThrow(() -> template.sendBody("log:org.apache.camel.TEST?showBody=true", "Hello World"));
        assertDoesNotThrow(() -> template.sendBody("log:org.apache.camel.TEST?showAll=true", "Hello World"));

        assertDoesNotThrow(() -> template.sendBody("log:org.apache.camel.TEST?showFuture=true", new MyFuture(() -> "foo")));

        assertDoesNotThrow(() -> template.sendBody("log:org.apache.camel.TEST?showFuture=false", new MyFuture(() -> "bar")));
    }

    @Test
    public void testSendMessageToLogMultiOptions() {
        assertDoesNotThrow(() -> template.sendBody("log:org.apache.camel.TEST?showHeaders=true", "Hello World"));
        assertDoesNotThrow(
                () -> template.sendBody("log:org.apache.camel.TEST?showAllProperties=true&showHeaders=true", "Hello World"));
    }

    @Test
    public void testSendMessageToLogShowFalse() {
        assertDoesNotThrow(() -> template.sendBody("log:org.apache.camel.TEST?showBodyType=false", "Hello World"));
    }

    @Test
    public void testSendMessageToLogMultiLine() {
        assertDoesNotThrow(() -> template.sendBody("log:org.apache.camel.TEST?multiline=true", "Hello World"));
    }

    @Test
    public void testSendByteArrayMessageToLogDefault() {
        assertDoesNotThrow(() -> template.sendBody("log:org.apache.camel.TEST", "Hello World".getBytes()));
    }

    @Test
    public void testSendMessageToLogMaxChars() {
        assertDoesNotThrow(() -> template.sendBody("log:org.apache.camel.TEST",
                "Hello World this is a very long string that is NOT going to be chopped by maxchars"));

        assertDoesNotThrow(() -> template.sendBody("log:org.apache.camel.TEST?maxChars=50",
                "Hello World this is a very long string that is going to be chopped by maxchars"));

        assertDoesNotThrow(() -> template.sendBody("log:org.apache.camel.TEST?maxChars=50&showAll=true&multiline=true",
                "Hello World this is a very long string that is going to be chopped by maxchars"));
    }

    @Test
    public void testSendExchangeWithException() throws Exception {
        Endpoint endpoint = resolveMandatoryEndpoint("log:org.apache.camel.TEST?showException=true");
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World");
        exchange.setException(new IllegalArgumentException("Damn"));

        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        assertMockEndpointsSatisfied();
        producer.stop();
    }

    @Test
    public void testSendCaughtExchangeWithException() throws Exception {
        Endpoint endpoint = resolveMandatoryEndpoint("log:org.apache.camel.TEST?showCaughtException=true");
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World");
        exchange.setProperty(Exchange.EXCEPTION_CAUGHT, new IllegalArgumentException("I am caught"));

        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        assertMockEndpointsSatisfied();
        producer.stop();
    }

    @Test
    public void testSendCaughtExchangeWithExceptionAndMultiline() throws Exception {
        Endpoint endpoint = resolveMandatoryEndpoint("log:org.apache.camel.TEST?showCaughtException=true&multiline=true");
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World");
        exchange.setProperty(Exchange.EXCEPTION_CAUGHT, new IllegalArgumentException("I am caught"));

        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        assertMockEndpointsSatisfied();
        producer.stop();
    }

    @Test
    public void testSendExchangeWithExceptionAndStackTrace() throws Exception {
        Endpoint endpoint = resolveMandatoryEndpoint("log:org.apache.camel.TEST?showException=true&showStackTrace=true");
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World");
        exchange.setException(new IllegalArgumentException("Damn"));

        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        assertMockEndpointsSatisfied();
        producer.stop();
    }

    @Test
    public void testSendCaughtExchangeWithExceptionAndStackTrace() throws Exception {
        Endpoint endpoint = resolveMandatoryEndpoint("log:org.apache.camel.TEST?showCaughtException=true&showStackTrace=true");
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World");
        exchange.setProperty(Exchange.EXCEPTION_CAUGHT, new IllegalArgumentException("I am caught"));

        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        assertMockEndpointsSatisfied();
        producer.stop();
    }

    @Test
    public void testConfiguration() {
        DefaultExchangeFormatter formatter = new DefaultExchangeFormatter();

        assertFalse(formatter.isShowExchangeId());
        assertFalse(formatter.isShowProperties());
        assertFalse(formatter.isShowHeaders());
        assertFalse(formatter.isShowVariables());
        assertTrue(formatter.isShowBodyType());
        assertTrue(formatter.isShowBody());
        assertFalse(formatter.isShowException());
        assertFalse(formatter.isShowCaughtException());
        assertFalse(formatter.isShowStackTrace());
        assertFalse(formatter.isShowAll());
        assertFalse(formatter.isMultiline());
        assertEquals(10000, formatter.getMaxChars());
    }

    private static class MyFuture extends FutureTask<String> {

        MyFuture(Callable<String> callable) {
            super(callable);
        }

        MyFuture(Runnable runnable, String o) {
            super(runnable, o);
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public String get() throws InterruptedException, ExecutionException {
            return "foo";
        }

        @Override
        public String toString() {
            return "ThisIsMyFuture";
        }
    }
}
