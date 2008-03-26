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

import junit.framework.Assert;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test for the <tt>transferExchange=true</tt> option.
 *
 * @version $Revision$
 */
public class MinaTransferExchangeOptionTest extends ContextTestSupport {

    private static final String URI = "mina:tcp://localhost:6321?sync=true&transferExchange=true";

    public void testMinaTransferExchangeOption() throws Exception {
        Endpoint endpoint = context.getEndpoint(URI);
        Exchange exchange = endpoint.createExchange();

        Message message = exchange.getIn();
        message.setBody("Hello!");
        message.setHeader("cheese", "feta");
        exchange.setProperty("ham", "old");

        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);

        Message out = exchange.getOut();
        assertNotNull(out);
        assertEquals("Goodbye!", out.getBody());
        assertEquals("cheddar", out.getHeader("cheese"));
        assertEquals("fresh", exchange.getProperty("salami"));

        // in should stay the same
        Message in = exchange.getIn();
        assertNotNull(in);
        assertEquals("Hello!", in.getBody());
        assertEquals("feta", in.getHeader("cheese"));
        // however the shared properties have changed
        assertEquals("fresh", exchange.getProperty("salami"));
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(URI).process(new Processor() {
                    public void process(Exchange e) throws InterruptedException {
                        Assert.assertNotNull(e.getIn().getBody());
                        Assert.assertNotNull(e.getIn().getHeaders());
                        Assert.assertNotNull(e.getProperties());
                        Assert.assertEquals("Hello!", e.getIn().getBody());
                        Assert.assertEquals("feta", e.getIn().getHeader("cheese"));
                        Assert.assertEquals("old", e.getProperty("ham"));
                        Assert.assertEquals(ExchangePattern.InOut, e.getPattern());

                        e.getOut().setBody("Goodbye!");
                        e.getOut().setHeader("cheese", "cheddar");
                        e.setProperty("salami", "fresh");
                    }
                });
            }
        };
    }
}
