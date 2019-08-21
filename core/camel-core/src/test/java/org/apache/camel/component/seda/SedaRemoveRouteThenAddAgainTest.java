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
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class SedaRemoveRouteThenAddAgainTest extends ContextTestSupport {

    @Test
    public void testRemoveRouteAndThenAddAgain() throws Exception {
        MockEndpoint out = getMockEndpoint("mock:out");
        out.expectedMessageCount(1);
        out.expectedBodiesReceived("before removing the route");

        template.sendBody("seda:in", "before removing the route");

        out.assertIsSatisfied();

        // now stop & remove the route
        context.getRouteController().stopRoute("sedaToMock");
        context.removeRoute("sedaToMock");

        // and then add it back again
        context.addRoutes(createRouteBuilder());

        // the mock endpoint was removed, so need to grab it again
        out = getMockEndpoint("mock:out");
        out.expectedMessageCount(1);
        out.expectedBodiesReceived("after removing the route");

        template.sendBody("seda:in", "after removing the route");

        out.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:in").routeId("sedaToMock").to("mock:out");
            }
        };
    }

}
