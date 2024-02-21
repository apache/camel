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
package org.apache.camel.component.seda;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 *
 */
public class SameSedaQueueSizeAndNoSizeTest extends ContextTestSupport {

    @Test
    public void testSameQueue() {
        for (int i = 0; i < 100; i++) {
            template.sendBody("seda:foo", "" + i);
        }

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("seda:foo", "Should be full now"), "Should fail");
        IllegalStateException ise = assertIsInstanceOf(IllegalStateException.class, e.getCause());
        if (!isJavaVendor("ibm")) {
            assertEquals("Queue full", ise.getMessage());
        }
    }

    @Test
    public void testSameQueueDifferentSize() {
        ResolveEndpointFailedException e = assertThrows(ResolveEndpointFailedException.class,
                () -> template.sendBody("seda:foo?size=200", "Should fail"), "Should fail");
        IllegalArgumentException ise = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals(
                "Cannot use existing queue seda://foo as the existing queue size 100 does not match given queue size 200",
                ise.getMessage());
    }

    @Test
    public void testSameQueueDifferentSizeBar() {
        ResolveEndpointFailedException e = assertThrows(ResolveEndpointFailedException.class,
                () -> template.sendBody("seda:bar?size=200", "Should fail"), "Should fail");
        IllegalArgumentException ise = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals("Cannot use existing queue seda://bar as the existing queue size " + SedaConstants.QUEUE_SIZE
                     + " does not match given queue size 200",
                ise.getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo?size=100").routeId("foo").noAutoStartup().to("mock:foo");

                from("seda:bar").routeId("bar").noAutoStartup().to("mock:bar");
            }
        };
    }
}
