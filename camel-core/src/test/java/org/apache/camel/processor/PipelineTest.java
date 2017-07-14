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

import javax.activation.DataHandler;
import javax.activation.FileDataSource;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class PipelineTest extends ContextTestSupport {
    
    /**
     * Simple processor the copies the in to the out and increments a counter.
     * Used to verify that the pipeline actually takes the output of one stage of 
     * the pipe and feeds it in as input into the next stage.
     */
    private static final class InToOut implements Processor {
        public void process(Exchange exchange) throws Exception {            
            exchange.getOut().copyFrom(exchange.getIn());
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
    private static final class InToFault implements Processor {
        public void process(Exchange exchange) throws Exception {
            exchange.getOut().setFault(true);
            exchange.getOut().setBody(exchange.getIn().getBody());
            Integer counter = exchange.getIn().getHeader("copy-counter", Integer.class);
            if (counter == null) {
                counter = 0;
            }
            exchange.getOut().setHeader("copy-counter", counter + 1);
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
        assertTrue(exchange.getOut() != null && exchange.getOut().isFault());
        assertEquals("Fault Message", exchange.getOut().getBody());
        assertEquals(2, exchange.getOut().getHeader("copy-counter"));        
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
    
    public void testCopyInOutExchange() {
        Exchange exchange = template.request("direct:start", new Processor() {
            public void process(Exchange exchange) {
                exchange.setPattern(ExchangePattern.InOut);
                exchange.getIn().setBody("test");
            }
        });
        // there is always breadcrumb header
        assertEquals("There should have no message header", 1, exchange.getOut().getHeaders().size());
        assertEquals("There should have no attachments", 0, exchange.getOut().getAttachmentObjects().size());
        assertEquals("There should have no attachments", 0, exchange.getOut().getAttachments().size());
        assertEquals("Get a wrong message body", "test", exchange.getOut().getBody());
        assertNull(exchange.getOut().getHeader("test"));
        assertNull(exchange.getOut().getAttachmentObject("test1.xml"));
        assertNull(exchange.getOut().getAttachment("test1.xml"));
        
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
                
                from("direct:start")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            exchange.getOut().copyFrom(exchange.getIn());
                            //Added the header and attachment
                            exchange.getOut().setHeader("test", "testValue");
                            exchange.getOut().addAttachment("test1.xml", new DataHandler(new FileDataSource("pom.xml")));
                        }
                    })
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            exchange.getOut().copyFrom(exchange.getIn());
                            assertNotNull("The test attachment should not be null", exchange.getOut().getAttachmentObject("test1.xml"));
                            assertNotNull("The test attachment should not be null", exchange.getOut().getAttachment("test1.xml"));
                            assertNotNull("The test header should not be null", exchange.getOut().getHeader("test"));
                            exchange.getOut().removeAttachment("test1.xml");
                            exchange.getOut().removeHeader("test");
                        }
                    });
            }
        };
    }

}
