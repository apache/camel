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
package org.apache.camel.issues;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;

import static org.apache.camel.builder.PredicateBuilder.not;

/**
 * @version 
 */
public class InterceptCustomPredicateAsFilterTest extends ContextTestSupport {

    private MyFiler filter = new MyFiler();

    private static class MyFiler implements Predicate {

        private List<String> bodies = new ArrayList<String>();

        public boolean matches(Exchange exchange) {
            String body = exchange.getIn().getBody(String.class);
            bodies.add(body);

            return !"secret".equals(body);
        }

        public List<String> getBodies() {
            return bodies;
        }
    }

    public void testInterceptCustomPredicateAsFilter() throws Exception {
        getMockEndpoint("mock:good").expectedBodiesReceived("Hello World", "Bye World");
        getMockEndpoint("mock:secret").expectedBodiesReceived("secret");

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "secret");
        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();

        assertEquals("Hello World", filter.getBodies().get(0));
        assertEquals("secret", filter.getBodies().get(1));
        assertEquals("Bye World", filter.getBodies().get(2));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // secret messages should be filtered out asap
                intercept().when(not(filter)).to("mock:secret").stop();

                from("direct:start").to("mock:good");
            }
        };
    }
}