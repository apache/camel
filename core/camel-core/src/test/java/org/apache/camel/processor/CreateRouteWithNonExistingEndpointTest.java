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
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class CreateRouteWithNonExistingEndpointTest extends ContextTestSupport {

    @Test
    public void testCreateRouteWithBadEndpoint() throws Exception {
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        try {
            super.setUp();
            fail("Should have failed to create this route!");
        } catch (Exception e) {
            log.debug("Caught expected exception: {}", e.getMessage(), e);
            NoSuchEndpointException nse = assertIsInstanceOf(NoSuchEndpointException.class, e.getCause());
            assertEquals("thisUriDoesNotExist", nse.getUri(), "uri");
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").to("thisUriDoesNotExist");
            }
        };
    }

}
