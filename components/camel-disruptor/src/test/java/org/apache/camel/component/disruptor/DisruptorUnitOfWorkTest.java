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
package org.apache.camel.component.disruptor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Unit test to verify unit of work with disruptor. That the UnitOfWork is able to route using disruptor but keeping the
 * same UoW.
 */
public class DisruptorUnitOfWorkTest extends CamelTestSupport {

    private static volatile String sync;

    private static volatile String lastOne;

    @Test
    public void testDisruptorUOW() throws Exception {
        final NotifyBuilder notify = new NotifyBuilder(context).whenDone(2).create();

        final MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);


        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
        notify.matchesMockWaitTime();

        assertEquals("onCompleteA", sync);
        assertEquals("onCompleteA", lastOne);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setTracing(true);

                from("direct:start").process(new MyUOWProcessor("A")).to("disruptor:foo");

                from("disruptor:foo").process(new Processor() {
                    @Override
                    public void process(final Exchange exchange) throws Exception {
                        assertEquals(null, sync);
                    }
                }).process(new Processor() {
                    @Override
                    public void process(final Exchange exchange) throws Exception {
                        lastOne = "processor";
                    }
                }).to("mock:result");
            }
        };
    }

    private static final class MyUOWProcessor implements Processor {

        private final String id;

        private MyUOWProcessor(final String id) {
            this.id = id;
        }

        @Override
        public void process(final Exchange exchange) throws Exception {
            exchange.getUnitOfWork().addSynchronization(new Synchronization() {
                @Override
                public void onComplete(final Exchange exchange) {
                    sync = "onComplete" + id;
                    lastOne = sync;
                }

                @Override
                public void onFailure(final Exchange exchange) {
                    sync = "onFailure" + id;
                    lastOne = sync;
                }
            });
        }
    }

}
