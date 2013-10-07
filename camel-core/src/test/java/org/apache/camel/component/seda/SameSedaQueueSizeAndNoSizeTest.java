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
package org.apache.camel.component.seda;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;

/**
 *
 */
public class SameSedaQueueSizeAndNoSizeTest extends ContextTestSupport {

    public void testSameQueue() throws Exception {
        for (int i = 0; i < 100; i++) {
            template.sendBody("seda:foo", "" + i);
        }

        try {
            template.sendBody("seda:foo", "Should be full now");
            fail("Should fail");
        } catch (CamelExecutionException e) {
            IllegalStateException ise = assertIsInstanceOf(IllegalStateException.class, e.getCause());
            if (!isJavaVendor("ibm")) {
                assertEquals("Queue full", ise.getMessage());
            }
        }
    }

    public void testSameQueueDifferentSize() throws Exception {
        try {
            template.sendBody("seda:foo?size=200", "Should fail");
            fail("Should fail");
        } catch (ResolveEndpointFailedException e) {
            IllegalArgumentException ise = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Cannot use existing queue seda://foo as the existing queue size 100 does not match given queue size 200", ise.getMessage());
        }
    }

    public void testSameQueueDifferentSizeBar() throws Exception {
        try {
            template.sendBody("seda:bar?size=200", "Should fail");
            fail("Should fail");
        } catch (ResolveEndpointFailedException e) {
            IllegalArgumentException ise = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Cannot use existing queue seda://bar as the existing queue size " + Integer.MAX_VALUE + " does not match given queue size 200", ise.getMessage());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo?size=100").routeId("foo").noAutoStartup()
                    .to("mock:foo");

                from("seda:bar").routeId("bar").noAutoStartup()
                    .to("mock:bar");
            }
        };
    }
}
