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
package org.apache.camel.component.disruptor.vm;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 *
 */
public class SameDisruptorVmQueueSizeAndNoSizeTest extends CamelTestSupport {

    @Test
    public void testSameQueue() throws Exception {
        for (int i = 0; i < 128; i++) {
            template.sendBody("disruptor-vm:foo?blockWhenFull=false", "" + i);
        }

        try {
            template.sendBody("disruptor-vm:foo?blockWhenFull=false", "Should be full now");
            fail("Should fail");
        } catch (CamelExecutionException e) {
            IllegalStateException ise = assertIsInstanceOf(IllegalStateException.class, e.getCause());
            assertEquals("Disruptors ringbuffer was full", ise.getMessage());
        }
    }

    @Test
    public void testSameQueueDifferentSize() throws Exception {
        try {
            template.sendBody("disruptor-vm:foo?size=256", "Should fail");
            fail("Should fail");
        } catch (ResolveEndpointFailedException e) {
            IllegalArgumentException ise = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals(
                    "Cannot use existing queue disruptor-vm://foo as the existing queue size 128 does not match given queue size 256",
                    ise.getMessage());
        }
    }

    @Test
    public void testSameQueueDifferentSizeBar() throws Exception {
        try {
            template.sendBody("disruptor-vm:bar?size=256", "Should fail");
            fail("Should fail");
        } catch (ResolveEndpointFailedException e) {
            IllegalArgumentException ise = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Cannot use existing queue disruptor-vm://bar as the existing queue size " + 1024
                    + " does not match given queue size 256", ise.getMessage());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("disruptor-vm:foo?size=128&blockWhenFull=false").routeId("foo").noAutoStartup()
                        .to("mock:foo");

                from("disruptor-vm:bar").routeId("bar").noAutoStartup()
                        .to("mock:bar");
            }
        };
    }
}
