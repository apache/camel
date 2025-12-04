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

import static org.junit.jupiter.api.Assertions.*;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.spi.ExchangeFormatter;
import org.apache.camel.support.processor.DefaultExchangeFormatter;
import org.junit.jupiter.api.Test;

/**
 * Custom Exchange Formatter test.
 */
public class LogCustomFormatterTest extends ContextTestSupport {

    private TestExchangeFormatter exchangeFormatter;

    @Test
    public void testCustomFormatterInComponent() {
        context.stop();

        LogComponent log = new LogComponent();
        exchangeFormatter = new TestExchangeFormatter();
        log.setExchangeFormatter(exchangeFormatter);
        context.addComponent("log", log);

        context.start();

        String endpointUri = "log:" + LogCustomFormatterTest.class.getCanonicalName();
        template.requestBody(endpointUri, "Hello World");
        template.requestBody(endpointUri, "Hello World");
        template.requestBody(endpointUri + "2", "Hello World");
        template.requestBody(endpointUri + "2", "Hello World");

        assertEquals(4, exchangeFormatter.getCounter());
    }

    @Test
    public void testCustomFormatterInRegistry() {
        context.stop();

        exchangeFormatter = new TestExchangeFormatter();
        context.getRegistry().bind("logFormatter", exchangeFormatter);

        context.start();

        String endpointUri = "log:" + LogCustomFormatterTest.class.getCanonicalName();
        template.requestBody(endpointUri, "Hello World");
        template.requestBody(endpointUri, "Hello World");
        template.requestBody(endpointUri + "2", "Hello World");
        template.requestBody(endpointUri + "2", "Hello World");

        assertEquals(4, exchangeFormatter.getCounter());
    }

    @Test
    public void testCustomFormatterInRegistryOptions() {
        context.stop();

        exchangeFormatter = new TestExchangeFormatter();
        context.getRegistry().bind("logFormatter", exchangeFormatter);
        assertEquals("", exchangeFormatter.getPrefix());

        context.start();

        String endpointUri = "log:" + LogCustomFormatterTest.class.getCanonicalName() + "?prefix=foo";
        template.requestBody(endpointUri, "Hello World");
        template.requestBody(endpointUri, "Hello World");

        assertEquals(2, exchangeFormatter.getCounter());
        assertEquals("foo", exchangeFormatter.getPrefix());
    }

    @Test
    public void testCustomFormatterInRegistryUnknownOption() {
        context.stop();

        exchangeFormatter = new TestExchangeFormatter();
        context.getRegistry().bind("logFormatter", exchangeFormatter);
        assertEquals("", exchangeFormatter.getPrefix());

        context.start();

        // unknown parameter
        Exception e = assertThrows(
                Exception.class,
                () -> {
                    String endpointUri2 =
                            "log:" + LogCustomFormatterTest.class.getCanonicalName() + "?prefix=foo&bar=no";
                    template.requestBody(endpointUri2, "Hello World");
                },
                "Should have thrown exception");

        ResolveEndpointFailedException cause = assertIsInstanceOf(ResolveEndpointFailedException.class, e.getCause());
        assertTrue(cause.getMessage().endsWith("Unknown parameters=[{bar=no}]"));
    }

    @Test
    public void testFormatterNotPickedUpWithDifferentKey() {
        context.stop();

        exchangeFormatter = new TestExchangeFormatter();
        context.getRegistry().bind("anotherFormatter", exchangeFormatter);
        context.getRegistry().bind("yetAnotherFormatter", new DefaultExchangeFormatter());

        context.start();

        String endpointUri = "log:" + LogCustomFormatterTest.class.getCanonicalName();
        template.requestBody(endpointUri, "Hello World");
        template.requestBody(endpointUri, "Hello World");
        template.requestBody(endpointUri + "2", "Hello World");
        template.requestBody(endpointUri + "2", "Hello World");

        assertEquals(0, exchangeFormatter.getCounter());
    }

    public static class TestExchangeFormatter implements ExchangeFormatter {
        private int counter;
        private boolean addTen;
        private String prefix = "";

        @Override
        public String format(Exchange exchange) {
            counter += addTen ? 10 : 1;
            return prefix + exchange.toString();
        }

        public int getCounter() {
            return counter;
        }

        public boolean isAddTen() {
            return addTen;
        }

        public void setAddTen(boolean addTen) {
            this.addTen = addTen;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }
    }
}
