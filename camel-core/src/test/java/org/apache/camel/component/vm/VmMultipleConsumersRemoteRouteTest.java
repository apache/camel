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
package org.apache.camel.component.vm;

import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;

public class VmMultipleConsumersRemoteRouteTest extends TestCase {

    public void testVmMultipleConsumersRemoteRoute() throws Exception {
        CamelContext camelContext = new DefaultCamelContext();
        ProducerTemplate producerTemplate = camelContext.createProducerTemplate();

        RouteBuilder builder = new RouteBuilder(camelContext) {
            @Override
            public void configure() throws Exception {
                from("vm:producer?multipleConsumers=true").routeId("route1").to("mock:route1");
            }

        };
        RouteBuilder builder2 = new RouteBuilder(camelContext) {
            @Override
            public void configure() throws Exception {
                from("vm:producer?multipleConsumers=true").routeId("route2").to("mock:route2");
            }
        };
        camelContext.addRoutes(builder);
        camelContext.addRoutes(builder2);

        camelContext.start();

        // this test actually removes the route... so in effect we have one route consuming
        // a "multipleConsumer" seda queue
        camelContext.stopRoute("route2");
        camelContext.removeRoute("route2");

        MockEndpoint mock1 = (MockEndpoint) camelContext.getEndpoint("mock:route1");
        MockEndpoint mock2 = (MockEndpoint) camelContext.getEndpoint("mock:route2");
        mock1.expectedMessageCount(100);
        mock2.expectedMessageCount(0);

        for (int i = 0; i < 100; i++) {
            producerTemplate.sendBody("vm:producer?multipleConsumers=true", i);
        }

        MockEndpoint.assertIsSatisfied(20, TimeUnit.SECONDS, mock1, mock2);

        camelContext.stop();
    }
}
