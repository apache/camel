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
package org.apache.camel.impl;

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.seda.SedaEndpoint;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.RouteDefinition;

/**
 * This test stops a route, mutates it then restarts it
 *
 * @version $Revision: 1.1 $
 */
public class StartAndStopRoutesTest extends ContextTestSupport {
    protected SedaEndpoint endpointA;
    protected SedaEndpoint endpointB;
    protected SedaEndpoint endpointC;
    protected Object expectedBody = "<hello>world!</hello>";

    public void testStartRouteThenStopMutateAndStartRouteAgain() throws Exception {
        List<RouteDefinition> routes = context.getRouteDefinitions();
        assertCollectionSize("Route", routes, 1);
        RouteDefinition route = routes.get(0);

        endpointA = getMandatoryEndpoint("seda:test.a", SedaEndpoint.class);
        endpointB = getMandatoryEndpoint("seda:test.b", SedaEndpoint.class);
        endpointC = getMandatoryEndpoint("seda:test.C", SedaEndpoint.class);

        assertProducerAndConsumerCounts("endpointA", endpointA, 0, 1);
        assertProducerAndConsumerCounts("endpointB", endpointB, 1, 0);
        assertProducerAndConsumerCounts("endpointC", endpointC, 0, 0);

        context.stopRoute(route);

        assertProducerAndConsumerCounts("endpointA", endpointA, 0, 0);
        assertProducerAndConsumerCounts("endpointB", endpointB, 0, 0);
        assertProducerAndConsumerCounts("endpointC", endpointC, 0, 0);


        // lets mutate the route...
        FromDefinition fromType = assertOneElement(route.getInputs());
        fromType.setUri("seda:test.C");
        context.startRoute(route);

        assertProducerAndConsumerCounts("endpointA", endpointA, 0, 0);
        assertProducerAndConsumerCounts("endpointB", endpointB, 1, 0);
        assertProducerAndConsumerCounts("endpointC", endpointC, 0, 1);


        // now lets check it works
        MockEndpoint results = getMockEndpoint("mock:results");
        results.expectedBodiesReceived(expectedBody);

        template.sendBody(endpointC, expectedBody);

        assertMockEndpointsSatisfied();
    }

    protected void assertProducerAndConsumerCounts(String name, SedaEndpoint endpoint, int producerCount, int consumerCount) {
        assertCollectionSize("Producers for " + name, endpoint.getProducers(), producerCount);
        assertCollectionSize("Consumers for " + name, endpoint.getConsumers(), consumerCount);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:test.a").
                        to("seda:test.b").
                        to("mock:results");
            }
        };

    }


}
