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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SedaSharedQueueEndpointRestartTest extends ContextTestSupport {

    @Test
    void testRestartedEndpointKeepsSharedQueueAlive() throws Exception {
        // seda:bar and seda:bar?blockWhenFull=true are distinct endpoint instances sharing one queue
        // key; a restarted endpoint must be re-added to the shared queue reference, otherwise stopping
        // the sibling endpoint later removes the queue while this endpoint is still active
        context.getRouteController().stopRoute("a");
        context.getRouteController().startRoute("a");

        context.getRouteController().stopRoute("b");

        assertDoesNotThrow(() -> template.sendBody("direct:a", "kept-alive"),
                "send after the sibling endpoint stopped should still find the shared queue");

        SedaEndpoint bar = getMandatoryEndpoint("seda:bar", SedaEndpoint.class);
        assertEquals(1, bar.getCurrentQueueSize());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a").routeId("a").to("seda:bar");
                from("direct:b").routeId("b").to("seda:bar?blockWhenFull=true");
            }
        };
    }
}
