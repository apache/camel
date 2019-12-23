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
package org.apache.camel.main;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Assert;
import org.junit.Test;

public class MainRouteOrderedTest extends Assert {

    @Test
    public void testOrdered() throws Exception {
        Main main = new Main();
        main.addRoutesBuilder(new BarRouteBuilder());
        main.addRoutesBuilder(new FooRouteBuilder());
        main.start();

        CamelContext camelContext = main.getCamelContext();

        // the routes should be foo and then bar
        assertEquals(2, camelContext.getRoutes().size());
        assertEquals("foo", camelContext.getRoutes().get(0).getId());
        assertEquals("bar", camelContext.getRoutes().get(1).getId());

        main.stop();
    }

    public static class FooRouteBuilder extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            from("direct:foo").routeId("foo")
                    .to("mock:foo");
        }

        @Override
        public int getOrder() {
            return 1; // lower is first
        }
    }

    public static class BarRouteBuilder extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            from("direct:bar").routeId("bar")
                    .to("mock:bar");
        }

        @Override
        public int getOrder() {
            return 2;
        }
    }
}
