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
package org.apache.camel.test.junit5;

import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CamelTestSupportTest extends CamelTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        replaceRouteFromWith("routeId", "direct:start");
        super.setUp();
    }

    @Test
    public void replacesFromEndpoint() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void exceptionThrownWhenEndpointNotFoundAndNoCreate() {
        assertThrows(NoSuchEndpointException.class, () -> {
            getMockEndpoint("mock:bogus", false);
        });
    }

    @Test
    public void exceptionThrownWhenEndpointNotAMockEndpoint() {
        assertThrows(NoSuchEndpointException.class, () -> {
            getMockEndpoint("direct:something", false);
        });
    }

    @Test
    public void autoCreateNonExisting() {
        MockEndpoint mock = getMockEndpoint("mock:bogus2", true);
        assertNotNull(mock);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:something").id("routeId").to("mock:result");
            }
        };
    }
}
