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
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class RouteIdAnonymousAndFixedClashTest extends ContextTestSupport {

    @Test
    public void testClash() throws Exception {
        // should create the 2 routes
        assertEquals(2, context.getRoutes().size());

        assertNotNull("Should have route1 (fixed id", context.getRoute("route1"));
        assertNotNull("Should have route2 (auto assigned id)", context.getRoute("route2"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:in1").id("route1") // Note the name
                    .to("mock:test1");

                from("direct:in2").to("mock:test2");
            }
        };
    }
}
