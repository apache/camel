/**
 * Copyright 2005-2015 Red Hat, Inc.
 * <p>
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.foo;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class FooBarWineTest extends CamelTestSupport {

    @Test
    public void testFooBar() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:bar");

        mock.expectedMinimumMessageCount(2);
        mock.setAssertPeriod(500);
    	
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testFooWine() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:wine");

        mock.expectedMinimumMessageCount(2);
        mock.setAssertPeriod(500);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("foo:hello?period=100")
                    .to("bar:Beer?amount=5")
                    .to("mock:bar");

                from("foo:hello2?period=50")
                    .to("wine:wine?amount=2")
                    .to("mock:wine");
            }
        };
    }
}
