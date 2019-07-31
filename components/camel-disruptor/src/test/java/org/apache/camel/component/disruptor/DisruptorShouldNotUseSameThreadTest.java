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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Unit test to verify continuing using NOT same thread on the consumer side.
 */
public class DisruptorShouldNotUseSameThreadTest extends CamelTestSupport {

    private static long id;

    @Test
    public void testNotUseSameThread() throws Exception {
        final MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                final ThreadLocal<String> local = new ThreadLocal<>();

                from("direct:start").process(new Processor() {
                    @Override
                    public void process(final Exchange exchange) throws Exception {
                        local.set("Hello");
                        id = Thread.currentThread().getId();
                    }
                }).to("disruptor:foo");

                from("disruptor:foo").process(new Processor() {
                    @Override
                    public void process(final Exchange exchange) throws Exception {
                        assertEquals(null, local.get());
                        assertNotSame("Thread ids should not be same", id, Thread.currentThread().getId());
                    }
                }).to("mock:result");
            }
        };
    }

}
