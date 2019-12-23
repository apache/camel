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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

public class PipelineTest extends ContextTestSupport {

    /**
     * Simple processor the copies the in to the out and increments a counter.
     * Used to verify that the pipeline actually takes the output of one stage
     * of the pipe and feeds it in as input into the next stage.
     */
    private static final class InToOut implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            exchange.getOut().copyFrom(exchange.getIn());
            Integer counter = exchange.getIn().getHeader("copy-counter", Integer.class);
            if (counter == null) {
                counter = 0;
            }
            exchange.getOut().setHeader("copy-counter", counter + 1);
        }
    }

    protected MockEndpoint resultEndpoint;

    @Test
    public void testSendMessageThroughAPipeline() throws Exception {
        resultEndpoint.expectedBodiesReceived(4);

        Exchange results = template.request("direct:a", new Processor() {
            public void process(Exchange exchange) {
                // now lets fire in a message
                Message in = exchange.getIn();
                in.setBody(1);
                in.setHeader("foo", "bar");
            }
        });

        resultEndpoint.assertIsSatisfied();

        assertEquals("Result body", 4, results.getMessage().getBody());
    }

    @Test
    public void testResultsReturned() throws Exception {
        Exchange exchange = template.request("direct:b", new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setBody("Hello World");
            }
        });

        assertEquals("Hello World", exchange.getMessage().getBody());
        assertEquals(3, exchange.getMessage().getHeader("copy-counter"));
    }

    @Test
    public void testOnlyProperties() {
        Exchange exchange = template.request("direct:b", new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setHeader("header", "headerValue");
            }
        });

        assertEquals("headerValue", exchange.getMessage().getHeader("header"));
        assertEquals(3, exchange.getMessage().getHeader("copy-counter"));
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        resultEndpoint = getMockEndpoint("mock:result");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        final Processor processor = new Processor() {
            public void process(Exchange exchange) {
                Integer number = exchange.getIn().getBody(Integer.class);
                if (number == null) {
                    number = 0;
                }
                number = number + 1;
                exchange.getMessage().setBody(number);
            }
        };

        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: example
                from("direct:a").pipeline("direct:x", "direct:y", "direct:z", "mock:result");
                // END SNIPPET: example

                from("direct:x").process(processor);
                from("direct:y").process(processor);
                from("direct:z").process(processor);

                // Create a route that uses the InToOut processor 3 times. the
                // copy-counter header should be == 3
                from("direct:b").process(new InToOut()).process(new InToOut()).process(new InToOut());
            }
        };
    }

}
