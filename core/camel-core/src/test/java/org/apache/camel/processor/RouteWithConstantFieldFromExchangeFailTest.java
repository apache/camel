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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RouteWithConstantFieldFromExchangeFailTest extends ContextTestSupport {
    private Exception exception;

    @Test
    public void testFail() {
        assertNotNull(exception, "Should have thrown an exception");
        IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertEquals("Constant field with name: XXX not found on Exchange.class", iae.getMessage());
    }

    @Override
    @BeforeEach
    public void setUp() {
        try {
            super.setUp();
        } catch (Exception e) {
            exception = e;
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:bar").setHeader("Exchange.XXX", constant("bar")).to("mock:bar");
            }
        };
    }
}
