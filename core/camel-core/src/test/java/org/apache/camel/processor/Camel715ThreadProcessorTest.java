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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Test;

/**
 * An old unit test from CAMEL-715 which reproduced a problem which we don't
 * have anymore in Camel threads EIP and the routing engine.
 */
public class Camel715ThreadProcessorTest extends Assert {
    private static final int ITERS = 50000;

    class SendingProcessor implements Processor {
        int iterationNumber;

        public SendingProcessor(int iter) {
            iterationNumber = iter;
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            Message in = exchange.getIn();
            in.setBody("a");
            // may set the property here
            exchange.setProperty("iterationNumber", iterationNumber);
        }
    }

    @Test
    public void testThreadProcessor() throws Exception {
        CamelContext context = new DefaultCamelContext();

        final CountDownLatch latch = new CountDownLatch(ITERS);

        context.addRoutes(new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("direct:a").threads(4).to("mock:input").process(new Processor() {
                    public void process(Exchange ex) throws Exception {
                        latch.countDown();
                    }
                });
            }
        });

        MockEndpoint mock = context.getEndpoint("mock:input", MockEndpoint.class);
        mock.expectedMessageCount(ITERS);

        final ProducerTemplate template = context.createProducerTemplate();

        final Endpoint e = context.getEndpoint("direct:a");
        context.start();

        for (int i = 0; i < ITERS; i++) {
            template.send(e, new SendingProcessor(i));
        }

        MockEndpoint.assertIsSatisfied(30, TimeUnit.SECONDS);

        latch.await(30, TimeUnit.SECONDS);

        for (int i = 0; i < ITERS; i++) {
            Integer number = mock.getReceivedExchanges().get(i).getProperty("iterationNumber", Integer.class);
            assertNotNull(number);
            assertEquals(i, number.intValue());
        }

        context.stop();
    }
}
