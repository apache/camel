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
package org.apache.camel.impl;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class RouteNoOutputTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        try {
            super.setUp();
            fail("Should have thrown exception");
        } catch (FailedToCreateRouteException e) {
            assertEquals("route1", e.getRouteId());
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Route route1 has no output processors. You need to add outputs to the route such as to(\"log:foo\").", e.getCause().getMessage());
        }
    }

    public void testDummy() {
        // noop
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start");
            }
        };
    }
}
