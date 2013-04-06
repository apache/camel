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
package org.apache.camel.component.seda;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.TestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * @version 
 */
public class SedaRouteTest extends TestSupport {

    @Override
    protected void setUp() throws Exception {
        // make SEDA testing faster
        System.setProperty("CamelSedaPollTimeout", "10");
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        System.clearProperty("CamelSedaPollTimeout");
        super.tearDown();
    }

    public void testSedaQueue() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        CamelContext context = new DefaultCamelContext();

        // lets add some routes
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("seda:test.a").to("seda:test.b");
                from("seda:test.b").process(new Processor() {
                    public void process(Exchange e) {
                        log.debug("Received exchange: " + e.getIn());
                        latch.countDown();
                    }
                });
            }
        });

        context.start();

        // now lets fire in a message
        Endpoint endpoint = context.getEndpoint("seda:test.a");
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader("cheese", 123);

        Producer producer = endpoint.createProducer();
        producer.process(exchange);

        // now lets sleep for a while
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        context.stop();
    }

    public void testThatShowsEndpointResolutionIsNotConsistent() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        CamelContext context = new DefaultCamelContext();

        // lets add some routes
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("seda:test.a").to("seda:test.b");
                from("seda:test.b").process(new Processor() {
                    public void process(Exchange e) {
                        log.debug("Received exchange: " + e.getIn());
                        latch.countDown();
                    }
                });
            }
        });

        context.start();

        // now lets fire in a message
        Endpoint endpoint = context.getEndpoint("seda:test.a");
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader("cheese", 123);

        Producer producer = endpoint.createProducer();
        producer.process(exchange);

        // now lets sleep for a while
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        context.stop();
    }
}