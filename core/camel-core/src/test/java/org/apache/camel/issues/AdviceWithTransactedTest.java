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
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.reifier.RouteReifier;
import org.apache.camel.spi.Policy;
import org.junit.Test;

public class AdviceWithTransactedTest extends ContextTestSupport {

    @Test
    public void testAdviceTransacted() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:advice").expectedMessageCount(1);

        RouteReifier.adviceWith(context.getRouteDefinitions().get(0), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveAddFirst().to("mock:advice");
            }
        });

        template.sendBody("direct:advice", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:advice")
                    // use policy instead of transacted (but its similar)
                    .policy(new MyDummyPolicy()).log("Advice ${body}").to("mock:result");
            }
        };
    }

    private static final class MyDummyPolicy implements Policy {

        @Override
        public void beforeWrap(Route route, NamedNode definition) {
            // noop
        }

        @Override
        public Processor wrap(Route route, Processor processor) {
            return processor;
        }
    }

}
