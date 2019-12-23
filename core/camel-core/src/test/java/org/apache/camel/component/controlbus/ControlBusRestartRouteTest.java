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
package org.apache.camel.component.controlbus;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.RoutePolicySupport;
import org.junit.Test;

public class ControlBusRestartRouteTest extends ContextTestSupport {

    private MyRoutePolicy myRoutePolicy = new MyRoutePolicy();

    @Test
    public void testControlBusRestart() throws Exception {
        assertEquals(1, myRoutePolicy.getStart());
        assertEquals(0, myRoutePolicy.getStop());

        assertEquals("Started", context.getRouteController().getRouteStatus("foo").name());

        template.sendBody("controlbus:route?routeId=foo&action=restart&restartDelay=0", null);

        assertEquals("Started", context.getRouteController().getRouteStatus("foo").name());

        assertEquals(2, myRoutePolicy.getStart());
        assertEquals(1, myRoutePolicy.getStop());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo").routeId("foo").routePolicy(myRoutePolicy).to("mock:foo");
            }
        };
    }

    private final class MyRoutePolicy extends RoutePolicySupport {

        private int start;
        private int stop;

        @Override
        public void onStart(Route route) {
            start++;
        }

        @Override
        public void onStop(Route route) {
            stop++;
        }

        public int getStart() {
            return start;
        }

        public int getStop() {
            return stop;
        }
    }
}
