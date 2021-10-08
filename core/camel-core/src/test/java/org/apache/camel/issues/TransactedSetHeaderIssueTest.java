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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Policy;
import org.junit.jupiter.api.Test;

public class TransactedSetHeaderIssueTest extends ContextTestSupport {

    private Policy policy = new Policy() {
        @Override
        public void beforeWrap(Route route, NamedNode definition) {
            // noop
        }

        @Override
        public Processor wrap(Route route, Processor processor) {
            return processor;
        }
    };

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testSetHeaderOk() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.getRegistry().bind("myPolicy", policy);

                from("direct:start")
                        .transacted("myPolicy")
                        .setHeader("foo", constant(123))
                        .to("log:foo")
                        .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedHeaderReceived("foo", 123);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSetHeaderIssue() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.getRegistry().bind("myPolicy", policy);

                from("direct:start")
                        .setHeader("foo", constant(123))
                        // transacted should be first but camel will automatic "correct" this
                        .transacted("myPolicy")
                        .to("log:foo")
                        .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedHeaderReceived("foo", 123);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

}
