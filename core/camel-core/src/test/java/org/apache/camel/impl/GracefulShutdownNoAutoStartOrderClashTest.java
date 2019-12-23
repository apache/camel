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
package org.apache.camel.impl;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class GracefulShutdownNoAutoStartOrderClashTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testStartupOrderClash() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").routeId("foo").startupOrder(5).to("mock:foo");
                from("direct:bar").routeId("bar").startupOrder(5).noAutoStartup().to("mock:bar");
            }
        });
        try {
            context.start();
            fail("Should have thrown an exception");
        } catch (Exception e) {
            assertEquals("Failed to start route bar because of startupOrder clash. Route foo already has startupOrder 5 configured"
                         + " which this route have as well. Please correct startupOrder to be unique among all your routes.", e.getMessage());
        }
    }

}
