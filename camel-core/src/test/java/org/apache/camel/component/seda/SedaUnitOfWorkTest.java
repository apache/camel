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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Synchronization;

/**
 * Unit test to verify unit of work with seda. That the UnitOfWork is able to route using seda
 * but keeping the same UoW.
 *
 * @version 
 */
public class SedaUnitOfWorkTest extends ContextTestSupport {

    private volatile Object foo;
    private volatile Object kaboom;
    private volatile String sync;
    private volatile String lastOne;

    public void testSedaUOW() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(2).create();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "bar");

        assertMockEndpointsSatisfied();
        notify.matchesMockWaitTime();

        assertEquals("onCompleteA", sync);
        assertEquals("onCompleteA", lastOne);
        assertEquals("Should have propagated the header inside the Synchronization.onComplete() callback", "bar", foo);
    }

    public void testSedaUOWWithException() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(2).create();

        template.sendBodyAndHeader("direct:start", "Hello World", "kaboom", "yes");

        notify.matchesMockWaitTime();

        assertEquals("onFailureA", sync);
        assertEquals("onFailureA", lastOne);
        assertEquals("Should have propagated the header inside the Synchronization.onFailure() callback", "yes", kaboom);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setTracing(true);

                from("direct:start")
                        .process(new MyUOWProcessor(SedaUnitOfWorkTest.this, "A"))
                        .to("seda:foo");

                from("seda:foo")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                assertEquals(null, sync);
                            }
                        })
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                lastOne = "processor";
                            }
                        })
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {    
                                if ("yes".equals(exchange.getIn().getHeader("kaboom"))) {
                                    throw new IllegalStateException("kaboom done!");
                                }
                                lastOne = "processor2";
                            }
                        })
                        .to("mock:result");
            }
        };
    }

    private static final class MyUOWProcessor implements Processor {

        private SedaUnitOfWorkTest test;
        private String id;

        private MyUOWProcessor(SedaUnitOfWorkTest test, String id) {
            this.test = test;
            this.id = id;
        }

        public void process(Exchange exchange) throws Exception {
            exchange.getUnitOfWork().addSynchronization(new Synchronization() {
                public void onComplete(Exchange exchange) {
                    test.sync = "onComplete" + id;
                    test.lastOne = test.sync;
                    test.foo = exchange.getIn().getHeader("foo");
                }

                public void onFailure(Exchange exchange) {
                    test.sync = "onFailure" + id;
                    test.lastOne = test.sync;
                    test.kaboom = exchange.getIn().getHeader("kaboom");
                }
            });
        }
    }

}
