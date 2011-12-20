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
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Synchronization;

/**
 * Unit test to verify unit of work with multicast.
 *
 * @version 
 */
public class MulticastUnitOfWorkTest extends ContextTestSupport {

    private static String sync;
    private static String lastOne;

    public void testMulticastUOW() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // will run B and then A, where A will be the last one
        assertEquals("onCompleteA", sync);
        assertEquals("onCompleteA", lastOne);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setTracing(true);

                from("direct:start")
                        .process(new MyUOWProcessor("A"))
                        .multicast().to("direct:foo", "direct:bar");

                from("direct:foo")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                assertNull("First exchange is not complete yet", sync);
                            }
                        })
                        .process(new MyUOWProcessor("B"))
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                lastOne = "processor";
                            }
                        })
                        .to("mock:result");

                from("direct:bar").to("mock:result");
            }
        };
    }

    private static final class MyUOWProcessor implements Processor {

        private String id;

        private MyUOWProcessor(String id) {
            this.id = id;
        }

        public void process(Exchange exchange) throws Exception {
            exchange.getUnitOfWork().addSynchronization(new Synchronization() {
                public void onComplete(Exchange exchange) {
                    sync = "onComplete" + id;
                    lastOne = sync;
                }

                public void onFailure(Exchange exchange) {
                    sync = "onFailure" + id;
                    lastOne = sync;
                }
            });
        }
    }

}