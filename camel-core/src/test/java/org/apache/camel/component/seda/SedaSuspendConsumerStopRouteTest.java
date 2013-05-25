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

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

public class SedaSuspendConsumerStopRouteTest extends ContextTestSupport {

    public void testSedaSuspendConsumerStopRoute() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("seda:start", "Hello World");

        assertMockEndpointsSatisfied();

        SedaEndpoint seda = context.getEndpoint("seda:start", SedaEndpoint.class);
        seda.getConsumers().iterator().next().suspend();

        boolean stopped = context.stopRoute("foo", 2, TimeUnit.SECONDS, true);
        assertTrue("Route should be stopped", stopped);

        assertTrue("Route should be stopped", context.getRouteStatus("foo").isStopped());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:start").routeId("foo")
                    .to("mock:result");
            }
        };
    }
}
