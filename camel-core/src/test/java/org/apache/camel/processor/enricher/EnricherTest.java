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
package org.apache.camel.processor.enricher;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

public class EnricherTest extends ContextTestSupport {

    private static SampleAggregator aggregationStrategy = new SampleAggregator();

    protected MockEndpoint mock;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mock = getMockEndpoint("mock:mock");
    }

    // -------------------------------------------------------------
    //  InOnly routes
    // -------------------------------------------------------------

    public void testEnrichInOnly() throws InterruptedException {
        mock.expectedBodiesReceived("test:blah");
        mock.message(0).exchangeProperty(Exchange.TO_ENDPOINT).isEqualTo("mock://mock");
        template.sendBody("direct:enricher-test-1", "test");
        mock.assertIsSatisfied();
    }

    public void testEnrichFaultInOnly() throws InterruptedException {
        mock.expectedMessageCount(0);
        Exchange exchange = template.send("direct:enricher-test-3", new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setBody("test");
            }
        });
        mock.assertIsSatisfied();
        assertEquals("test", exchange.getIn().getBody());
        assertTrue(exchange.getOut() != null && exchange.getOut().isFault());
        assertEquals("failed", exchange.getOut().getBody());
        assertEquals("direct://enricher-fault-resource", exchange.getProperty(Exchange.TO_ENDPOINT));
        assertNull(exchange.getException());
    }

    public void testEnrichErrorInOnly() throws InterruptedException {
        mock.expectedMessageCount(0);
        Exchange exchange = template.send("direct:enricher-test-4", new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setBody("test");
            }
        });
        mock.assertIsSatisfied();
        assertEquals("test", exchange.getIn().getBody());
        assertEquals("failed", exchange.getException().getMessage());
        assertFalse(exchange.hasOut());
    }

    // -------------------------------------------------------------
    //  InOut routes
    // -------------------------------------------------------------

    public void testEnrichInOut() throws InterruptedException {
        String result = (String) template.sendBody("direct:enricher-test-5", ExchangePattern.InOut, "test");
        assertEquals("test:blah", result);
    }

    public void testEnrichInOutPlusHeader() throws InterruptedException {
        Exchange exchange = template.send("direct:enricher-test-5", ExchangePattern.InOut, new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setHeader("foo", "bar");
                exchange.getIn().setBody("test");
            }
        });
        assertEquals("bar", exchange.getIn().getHeader("foo"));
        assertEquals("test:blah", exchange.getIn().getBody());
        assertTrue(exchange.hasOut());
        assertNull(exchange.getException());
    }

    public void testEnrichFaultInOut() throws InterruptedException {
        Exchange exchange = template.send("direct:enricher-test-7", ExchangePattern.InOut, new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setBody("test");
            }
        });
        assertEquals("test", exchange.getIn().getBody());
        assertTrue(exchange.getOut() != null && exchange.getOut().isFault());
        assertEquals("failed", exchange.getOut().getBody());
        assertNull(exchange.getException());
    }

    public void testEnrichErrorInOut() throws InterruptedException {
        Exchange exchange = template.send("direct:enricher-test-8", ExchangePattern.InOut, new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setBody("test");
            }
        });
        assertEquals("test", exchange.getIn().getBody());
        assertEquals("failed", exchange.getException().getMessage());
        assertFalse(exchange.hasOut());
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // -------------------------------------------------------------
                //  InOnly routes
                // -------------------------------------------------------------

                from("direct:enricher-test-1")
                    .enrich("direct:enricher-constant-resource", aggregationStrategy)
                    .to("mock:mock");

                from("direct:enricher-test-3")
                    .enrich("direct:enricher-fault-resource", aggregationStrategy)
                    .to("mock:mock");

                from("direct:enricher-test-4").errorHandler(noErrorHandler()) // avoid re-deliveries
                    .enrich("direct:enricher-error-resource", aggregationStrategy).to("mock:mock");

                // -------------------------------------------------------------
                //  InOut routes
                // -------------------------------------------------------------

                from("direct:enricher-test-5")
                    .enrich("direct:enricher-constant-resource", aggregationStrategy);

                from("direct:enricher-test-7")
                    .enrich("direct:enricher-fault-resource", aggregationStrategy);

                from("direct:enricher-test-8").errorHandler(noErrorHandler()) // avoid re-deliveries
                    .enrich("direct:enricher-error-resource", aggregationStrategy);

                // -------------------------------------------------------------
                //  Enricher resources
                // -------------------------------------------------------------

                from("direct:enricher-constant-resource").transform().constant("blah");
                
                from("direct:enricher-fault-resource").errorHandler(noErrorHandler()).process(new FailureProcessor(false));
                from("direct:enricher-error-resource").errorHandler(noErrorHandler()).process(new FailureProcessor(true));
            }
        };
    }

}
