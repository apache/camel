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
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ThreadsCorePoolTest extends ContextTestSupport {

    private String beforeThreadName;
    private volatile String afterThreadName;

    @Test
    public void testThreadsCorePool() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        assertNotNull(beforeThreadName, "The main thread name should be already properly set!");
        assertNotNull(afterThreadName, "The camel thread name should be already properly set!");
        assertFalse(beforeThreadName.equalsIgnoreCase(afterThreadName), "Should use different threads");
    }

    @Test
    public void testThreadsCorePoolBuilder() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:foo", "Hello World");

        assertMockEndpointsSatisfied();

        assertNotNull(beforeThreadName, "The main thread name should be already properly set!");
        assertNotNull(afterThreadName, "The camel thread name should be already properly set!");
        assertFalse(beforeThreadName.equalsIgnoreCase(afterThreadName), "Should use different threads");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setTracing(true);

                from("direct:start").to("log:before").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        beforeThreadName = Thread.currentThread().getName();
                    }
                })
                        // will use a a custom thread pool with 5 in core and 5 as
                        // max
                        .threads(5).process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                afterThreadName = Thread.currentThread().getName();
                            }
                        }).to("log:after").to("mock:result");

                from("direct:foo").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        beforeThreadName = Thread.currentThread().getName();
                    }
                })
                        // using the builder style
                        .threads().poolSize(5).process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                afterThreadName = Thread.currentThread().getName();
                            }
                        }).to("mock:result");
            }
        };
    }
}
