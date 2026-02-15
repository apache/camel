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
package org.apache.camel.parser.java;

import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class MyRouteDuplicateIdTest extends CamelTestSupport {

    MyRouteDuplicateIdTest() {
        testConfiguration().withAutoStartContext(false);
    }

    @Test
    void testFoo() {
        assumeFalse(context.isStarted(), "This test cannot run with the context already started");
        assertThrows(FailedToStartRouteException.class, () -> context.start());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:foo").routeId("foo")
                        .to("mock:foo");

                from("direct:bar").routeId("bar")
                        .to("mock:bar");

                // duplicate route id on purpose
                from("direct:baz").routeId("foo")
                        .to("mock:baz");
            }
        };
    }
}
