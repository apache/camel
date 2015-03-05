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
package org.apache.camel.component.disruptor;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version
 */
public class DisruptorRemoveRouteThenAddAgainTest extends CamelTestSupport {
    @Test
    public void testRemoveRouteAndThenAddAgain() throws Exception {
        MockEndpoint out = getMockEndpoint("mock:out");
        out.expectedMessageCount(1);
        out.expectedBodiesReceived("before removing the route");

        template.sendBody("disruptor:in", "before removing the route");

        out.assertIsSatisfied();

        // now stop & remove the route
        context.stopRoute("disruptorToMock");
        context.removeRoute("disruptorToMock");
        
        // and then add it back again
        context.addRoutes(createRouteBuilder());
        
        // Here we need stop the template first
        template.stop();

        // get mock endpoint again as we removed the route which removes the endpoint
        out = getMockEndpoint("mock:out");
        out.expectedMessageCount(1);
        out.expectedBodiesReceived("after removing the route");

        // Restart the template again
        template.start();
        template.sendBody("disruptor:in", "after removing the route");

        out.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("disruptor:in").routeId("disruptorToMock").to("mock:out");
            }
        };
    }

}
