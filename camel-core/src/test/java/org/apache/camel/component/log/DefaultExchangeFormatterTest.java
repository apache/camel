/**
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
import org.apache.camel.processor.DefaultExchangeFormatter;

/**
 * Logger formatter test.
 */
public class DefaultExchangeFormatterTest extends ContextTestSupport {

    public void testSendMessageToLogDefault() throws Exception {
        template.sendBody("log:org.apache.camel.TEST", "Hello World");
    }

    public void testSendMessageToLogAllOff() throws Exception {
        template.sendBody("log:org.apache.camel.TEST?showBody=false&showBodyType=false&showExchangePattern=false", "Hello World");
    }

    public void testSendMessageToLogSingleOptions() throws Exception {
        template.sendBody("log:org.apache.camel.TEST?showExchangeId=true", "Hello World");
        template.sendBody("log:org.apache.camel.TEST?showExchangePattern=true", "Hello World");
        template.sendBody("log:org.apache.camel.TEST?showExchangePattern=false", "Hello World");
        template.sendBody("log:org.apache.camel.TEST?showProperties=true", "Hello World");
        template.sendBody("log:org.apache.camel.TEST?showHeaders=true", "Hello World");
        template.sendBody("log:org.apache.camel.TEST?showBodyType=true", "Hello World");
        template.sendBody("log:org.apache.camel.TEST?showBody=true", "Hello World");
        template.sendBody("log:org.apache.camel.TEST?showOut=true", "Hello World");
        template.sendBody("log:org.apache.camel.TEST?showOut=true&showHeaders=true", "Hello World");
        template.sendBody("log:org.apache.camel.TEST?showOut=true&showBodyType=true", "Hello World");
        template.sendBody("log:org.apache.camel.TEST?showOut=true&showBody=true", "Hello World");
        template.sendBody("log:org.apache.camel.TEST?showAll=true", "Hello World");

        template.sendBody("log:org.apache.camel.TEST?showFuture=true", new MyFuture(new Callable<String>() {
            public String call() throws Exception {
                return "foo";
            }
        }));
        template.sendBody("log:org.apache.camel.TEST?showFuture=false", new MyFuture(new Callable<String>() {
            public String call() throws Exception {
                return "bar";
            }
        }));
    }

    public void testSendMessageToLogMultiOptions() throws Exception {
        template.sendBody("log:org.apache.camel.TEST?showHeaders=true&showOut=true", "Hello World");
        template.sendBody("log:org.apache.camel.TEST?showProperties=true&showHeaders=true&showOut=true", "Hello World");
    }

    public void testSendMessageToLogShowFalse() throws Exception {
        template.sendBody("log:org.apache.camel.TEST?showBodyType=false", "Hello World");
    }

    public void testSendMessageToLogMultiLine() throws Exception {
        template.sendBody("log:org.apache.camel.TEST?multiline=true", "Hello World");
    }

    public void testSendByteArrayMessageToLogDefault() throws Exception {
        template.sendBody("log:org.apache.camel.TEST", "Hello World".getBytes());
    }

    public void testSendMessageToLogMaxChars() throws Exception {
        template.sendBody("log:org.apache.camel.TEST",
                "Hello World this is a very long string that is NOT going to be chopped by maxchars");

        template.sendBody("log:org.apache.camel.TEST?maxChars=50",
                "Hello World this is a very long string that is going to be chopped by maxchars");

        template.sendBody("log:org.apache.camel.TEST?maxChars=50&showAll=true&multiline=true",
                "Hello World this is a very long string that is going to be chopped by maxchars");
    }

    public void testSendExchangeWithOut() throws Exception {
        Endpoint endpoint = resolveMandatoryEndpoint("log:org.apache.camel.TEST?showAll=true&multiline=true");
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World");
        exchange.getOut().setBody(22);

        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        producer.stop();
    }

    public void testSendExchangeWithException() throws Exception {
        Endpoint endpoint = resolveMandatoryEndpoint("log:org.apache.camel.TEST?showException=true");
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World");
        exchange.setException(new IllegalArgumentException("Damn"));

        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        producer.stop();
    }

    public void testSendCaughtExchangeWithException() throws Exception {
        Endpoint endpoint = resolveMandatoryEndpoint("log:org.apache.camel.TEST?showCaughtException=true");
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World");
        exchange.setProperty(Exchange.EXCEPTION_CAUGHT, new IllegalArgumentException("I am caught"));

        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        producer.stop();
    }

    public void testSendCaughtExchangeWithExceptionAndMultiline() throws Exception {
        Endpoint endpoint = resolveMandatoryEndpoint("log:org.apache.camel.TEST?showCaughtException=true&multiline=true");
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World");
        exchange.setProperty(Exchange.EXCEPTION_CAUGHT, new IllegalArgumentException("I am caught"));

        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        producer.stop();
    }

    public void testSendExchangeWithExceptionAndStackTrace() throws Exception {
        Endpoint endpoint = resolveMandatoryEndpoint("log:org.apache.camel.TEST?showException=true&showStackTrace=true");
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World");
        exchange.setException(new IllegalArgumentException("Damn"));

        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        producer.stop();
    }

    public void testSendCaughtExchangeWithExceptionAndStackTrace() throws Exception {
        Endpoint endpoint = resolveMandatoryEndpoint("log:org.apache.camel.TEST?showCaughtException=true&showStackTrace=true");
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World");
        exchange.setProperty(Exchange.EXCEPTION_CAUGHT, new IllegalArgumentException("I am caught"));

        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        producer.stop();
    }

    public void testConfiguration() {
        DefaultExchangeFormatter formatter = new DefaultExchangeFormatter();

        assertFalse(formatter.isShowExchangeId());
        assertFalse(formatter.isShowProperties());
        assertFalse(formatter.isShowHeaders());
        assertTrue(formatter.isShowBodyType());
        assertTrue(formatter.isShowBody());
        assertFalse(formatter.isShowOut());
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
