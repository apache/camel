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
package org.apache.camel.component.browse;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class BrowseEndpointTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testBrowseEndpointDefaultConstructor() throws Exception {
        final BrowseEndpoint be = new BrowseEndpoint();
        be.setCamelContext(context);
        be.setEndpointUriIfNotSpecified("browse://foo");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to(be);
            }
        });
        context.start();

        template.sendBody("direct:start", "Hello World");

        assertEquals(1, be.getExchanges().size());
    }

    @Test
    public void testBrowseEndpointUriConstructor() throws Exception {
        final BrowseEndpoint be = new BrowseEndpoint("browse://foo", context.getComponent("browse"));

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to(be);
            }
        });
        context.start();

        template.sendBody("direct:start", "Hello World");

        assertEquals(1, be.getExchanges().size());
    }

}
