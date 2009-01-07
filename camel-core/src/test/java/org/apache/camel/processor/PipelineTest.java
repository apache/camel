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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision$
 */
public class PipelineTest extends ContextTestSupport {
    
    /**
     * Simple processor the copies the in to the out and increments a counter.
     * Used to verify that the pipeline actually takes the output of one stage of 
     * the pipe and feeds it in as input into the next stage.
     */
    private final class InToOut implements Processor {
        public void process(Exchange exchange) throws Exception {            
            exchange.getOut(true).copyFrom(exchange.getIn());
            Integer counter = exchange.getIn().getHeader("copy-counter", Integer.class);
            if (counter == null) {
                counter = 0;
            }
            exchange.getOut().setHeader("copy-counter", counter + 1);
        }
    }

    /**
     * Simple processor the copies the in to the fault and increments a counter.
     */
    private final class InToFault implements Processor {
        public void process(Exchange exchange) throws Exception {
            exchange.getFault(true).setBody(exchange.getIn().getBody());
            Integer counter = exchange.getIn().getHeader("copy-counter", Integer.class);
            if (counter == null) {
                counter = 0;
            }
            exchange.getFault().setHeader("copy-counter", counter + 1);
        }
    }

    protected MockEndpoint resultEndpoint;

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

        assertEquals("Result body", 4, results.getOut().getBody());
    }

    
    public void testResultsReturned() throws Exception {
        Exchange exchange = template.request("direct:b", new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setBody("Hello World");
            }
        });
        
        assertEquals("Hello World", exchange.getOut().getBody());
        assertEquals(3, exchange.getOut().getHeader("copy-counter"));        
    }

    /**
     * Disabled for now until we figure out fault processing in the pipeline.
     * 
     * @throws Exception
     */
    public void testFaultStopsPipeline() throws Exception {
        Exchange exchange = template.request("direct:c", new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setBody("Fault Message");
            }
        });
        
        // Check the fault..
        assertEquals("Fault Message", exchange.getFault().getBody());
        assertEquals(2, exchange.getFault().getHeader("copy-counter"));        
        
        // Check the out Message.. It should have only been processed once.
        // since the fault should stop it from going to the next process.
        assertEquals(1, exchange.getOut().getHeader("copy-counter"));                
    }

    public void testOnlyProperties() {
        Exchange exchange = template.request("direct:b", new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setHeader("header", "headerValue");
            }
        });
        
        assertEquals("headerValue", exchange.getOut().getHeader("header"));
        assertEquals(3, exchange.getOut().getHeader("copy-counter"));  
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        resultEndpoint = getMockEndpoint("mock:result");
    }

    protected RouteBuilder createRouteBuilder() {
        final Processor processor = new Processor() {
            public void process(Exchange exchange) {
                Integer number = exchange.getIn().getBody(Integer.class);
                if (number == null) {
                    number = 0;
                }
                number = number + 1;
                exchange.getOut().setBody(number);
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
                
                // Create a route that uses the  InToOut processor 3 times. the copy-counter header should be == 3
                from("direct:b").process(new InToOut()).process(new InToOut()).process(new InToOut());
                // Create a route that uses the  InToFault processor.. the last InToOut will not be called since the Fault occurs before.
                from("direct:c").process(new InToOut()).process(new InToFault()).process(new InToOut());
            }
        };
    }

}
