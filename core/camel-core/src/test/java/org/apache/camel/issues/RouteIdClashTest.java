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
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class RouteIdClashTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testClash() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:in1").routeId("myroute").to("mock:test1");
                from("direct:in2").routeId("myroute2").to("mock:test2");
                from("direct:in3").routeId("myroute").to("mock:test3");
            }
        });
        FailedToStartRouteException e = assertThrows(FailedToStartRouteException.class, () -> context.start());
        Assertions.assertEquals("myroute", e.getRouteId());
        Assertions.assertEquals(
                "Failed to start route: myroute because: Duplicate id detected: myroute. Please correct ids to be unique among all your routes.",
                e.getMessage());
    }
}
