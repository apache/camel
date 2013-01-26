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

/**
 * Unit test for the <tt>transferExchange=true</tt> option.
 *
 * @version 
 */
public class MinaVMTransferExchangeOptionTest extends BaseMinaTest {

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
        Endpoint endpoint = context.getEndpoint("mina:vm://localhost:{{port}}?sync=true&encoding=UTF-8&transferExchange=true");
        Exchange exchange = endpoint.createExchange();

        Message message = exchange.getIn();
        message.setBody("Hello!");
        message.setHeader("cheese", "feta");
        exchange.setProperty("ham", "old");
        exchange.setProperty("setException", setException);

        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);

        return exchange;
    }

    private void assertExchange(Exchange exchange, boolean hasFault) {
        if (!hasFault) {
            Message out = exchange.getOut();
            assertNotNull(out);
            assertFalse(out.isFault());
            assertEquals("Goodbye!", out.getBody());
            assertEquals("cheddar", out.getHeader("cheese"));
        } else {
            Message fault = exchange.getOut();
            assertNotNull(fault);
            assertTrue(fault.isFault());
            assertNotNull(fault.getBody());
            assertTrue("Should get the InterrupteException exception", fault.getBody() instanceof InterruptedException);
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

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("mina:vm://localhost:{{port}}?sync=true&encoding=UTF-8&transferExchange=true").process(new Processor() {
                    public void process(Exchange e) throws InterruptedException {
                        assertNotNull(e.getIn().getBody());
                        assertNotNull(e.getIn().getHeaders());
                        assertNotNull(e.getProperties());
                        assertEquals("Hello!", e.getIn().getBody());
                        assertEquals("feta", e.getIn().getHeader("cheese"));
                        assertEquals("old", e.getProperty("ham"));
                        assertEquals(ExchangePattern.InOut, e.getPattern());
                        Boolean setException = (Boolean) e.getProperty("setException");

                        if (setException) {
                            e.getOut().setFault(true);
                            e.getOut().setBody(new InterruptedException());
                            e.getOut().setHeader("hello", "nihao");
                        } else {
                            e.getOut().setBody("Goodbye!");
                            e.getOut().setHeader("cheese", "cheddar");
                        }
                        e.setProperty("salami", "fresh");
                        e.setProperty("Charset", Charset.defaultCharset());
                    }
                });
            }
        };
    }

}