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
package org.apache.camel.component.mina;

import java.nio.charset.Charset;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit test for the <tt>transferExchange=true</tt> option.
 */
public class MinaTransferExchangeOptionTest extends BaseMinaTest {

    private static final Logger LOG = LoggerFactory.getLogger(MinaTransferExchangeOptionTest.class);

    @Test
    public void testMinaTransferExchangeOptionWithoutException() throws Exception {
        Exchange exchange = sendExchange(false);
        assertExchange(exchange, false);
    }

    @Test
    public void testMinaTransferExchangeOptionWithException() throws Exception {
        Exchange exchange = sendExchange(true);
        assertExchange(exchange, true);
    }

    private Exchange sendExchange(boolean setException) throws Exception {
        Endpoint endpoint = context.getEndpoint(String.format("mina:tcp://localhost:%1$s?sync=true&encoding=UTF-8&transferExchange=true", getPort()));
        Producer producer = endpoint.createProducer();
        Exchange exchange = endpoint.createExchange();
        //Exchange exchange = endpoint.createExchange();

        Message message = exchange.getIn();
        message.setBody("Hello!");
        message.setHeader("cheese", "feta");
        exchange.setProperty("ham", "old");
        exchange.setProperty("setException", setException);

        producer.start();
        producer.process(exchange);

        return exchange;
    }

    private void assertExchange(Exchange exchange, boolean hasException) {
        if (!hasException) {
            Message out = exchange.getMessage();
            assertNotNull(out);
            assertEquals("Goodbye!", out.getBody());
            assertEquals("cheddar", out.getHeader("cheese"));
        } else {
            Message fault = exchange.getMessage();
            assertNotNull(fault);
            assertNotNull(fault.getBody());
            assertTrue("Should get the InterruptedException exception", fault.getBody() instanceof InterruptedException);
            assertEquals("nihao", fault.getHeader("hello"));
        }


        // in should stay the same
        Message in = exchange.getIn();
        assertNotNull(in);
        assertEquals("Hello!", in.getBody());
        assertEquals("feta", in.getHeader("cheese"));
        // however the shared properties have changed
        assertEquals("fresh", exchange.getProperty("salami"));
        assertNull(exchange.getProperty("Charset"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            public void configure() {
                from(String.format("mina:tcp://localhost:%1$s?sync=true&encoding=UTF-8&transferExchange=true", getPort())).process(new Processor() {

                    public void process(Exchange e) throws InterruptedException {
                        LOG.debug("Enter Processor...");
                        assertNotNull(e.getIn().getBody());
                        LOG.debug("Enter Processor...1");
                        assertNotNull(e.getIn().getHeaders());
                        LOG.debug("Enter Processor...2");
                        assertNotNull(e.getProperties());
                        LOG.debug("Enter Processor...3");
                        assertEquals("Hello!", e.getIn().getBody());
                        LOG.debug("Enter Processor...4");
                        assertEquals("feta", e.getIn().getHeader("cheese"));
                        LOG.debug("Enter Processor...5");
                        assertEquals("old", e.getProperty("ham"));
                        LOG.debug("Enter Processor...6");
                        assertEquals(ExchangePattern.InOut, e.getPattern());
                        LOG.debug("Enter Processor...7");
                        Boolean setException = (Boolean) e.getProperty("setException");

                        if (setException) {
                            e.getOut().setBody(new InterruptedException());
                            e.getOut().setHeader("hello", "nihao");
                        } else {
                            e.getOut().setBody("Goodbye!");
                            e.getOut().setHeader("cheese", "cheddar");
                        }
                        e.setProperty("salami", "fresh");
                        e.setProperty("Charset", Charset.defaultCharset());
                        LOG.debug("Exit Processor...");
                    }
                });
            }
        };
    }
}
