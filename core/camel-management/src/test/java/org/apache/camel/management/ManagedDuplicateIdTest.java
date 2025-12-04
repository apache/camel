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

package org.apache.camel.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@DisabledOnOs(OS.AIX)
public class ManagedDuplicateIdTest extends ManagementTestSupport {

    @Test
    public void testDuplicateId() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:foo")
                        .routeId("foo")
                        .to("log:foo")
                        .split(body())
                        .to("log:line")
                        .id("clash")
                        .end()
                        .to("mock:foo");

                from("direct:bar")
                        .routeId("bar")
                        .to("log:bar")
                        .split(body())
                        .to("log:line")
                        .id("clash")
                        .end()
                        .to("mock:bar");
            }
        });
        try {
            context.start();
            fail("Should fail");
        } catch (Exception e) {
            assertEquals(
                    "Failed to start route: foo because: Duplicate id detected: clash. Please correct ids to be unique among all your routes.",
                    e.getMessage());
        }
    }

    @Test
    public void testDuplicateIdSingleRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:foo")
                        .routeId("foo")
                        .to("log:line")
                        .id("clash")
                        .to("log:foo")
                        .id("cheese")
                        .split(body())
                        .id("mysplit")
                        .to("log:line")
                        .id("clash")
                        .end()
                        .to("mock:foo");
            }
        });
        try {
            context.start();
            fail("Should fail");
        } catch (Exception e) {
            assertEquals(
                    "Failed to start route: foo because: Duplicate id detected: clash. Please correct ids to be unique among all your routes.",
                    e.getMessage());
        }
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
