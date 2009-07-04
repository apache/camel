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
package org.apache.camel.processor;

import java.util.List;

import org.apache.camel.Channel;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.LoadBalanceDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.SendDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.processor.loadbalancer.RandomLoadBalancer;

/**
 * A crude unit test to navigate the route and build a Java DSL from the route definition
 */
public class RandomLoadBalanceJavaDSLBuilderTest extends RandomLoadBalanceTest {

    public void testNavigateRouteAsJavaDSLWithNavigate() throws Exception {
        // this one navigate using the runtime route using the Navigate<Processor>

        StringBuilder sb = new StringBuilder();

        Route route = context.getRoutes().get(0);

        // the start of the route
        sb.append("from(\"" + route.getEndpoint().getEndpointUri() + "\")");

        // navigate the route and add Java DSL to the sb
        Navigate<Processor> nav = route.navigate();
        navigateRoute(nav, sb);

        // output the Java DSL
        assertEquals("from(\"direct:start\").loadBalance().random().to(\"mock:x\").to(\"mock:y\").to(\"mock:z\")", sb.toString());
    }

    public void testNavigateRouteAsJavaDSL() throws Exception {
        // this one navigate using the route definiton

        StringBuilder sb = new StringBuilder();

        RouteDefinition route = context.getRouteDefinitions().get(0);

        // the start of the route
        sb.append("from(\"" + route.getInputs().get(0).getEndpoint().getEndpointUri() + "\")");

        // navigate the route and add Java DSL to the sb
        navigateDefinition(route, sb);

        // output the Java DSL
        assertEquals("from(\"direct:start\").loadBalance().random().to(\"mock:x\").to(\"mock:y\").to(\"mock:z\")", sb.toString());
    }

    private void navigateRoute(Navigate<Processor> nav, StringBuilder sb) {
        if (!nav.hasNext()) {
            return;
        }

        for (Processor child : nav.next()) {

            if (child instanceof Channel) {
                Channel channel = (Channel) child;
                ProcessorDefinition def = channel.getProcessorDefinition();
                navigateDefinition(def, sb);
            }
        }
    }

    private void navigateDefinition(ProcessorDefinition def, StringBuilder sb) {

        if (def instanceof LoadBalanceDefinition) {
            sb.append(".loadBalance()");

            LoadBalanceDefinition lbd = (LoadBalanceDefinition) def;
            LoadBalancer balancer = lbd.getLoadBalancerType().getLoadBalancer(null);
            if (balancer instanceof RandomLoadBalancer) {
                sb.append(".random()");
            }
        }

        if (def instanceof SendDefinition) {
            SendDefinition send = (SendDefinition) def;
            sb.append(".to(\"" + send.getEndpoint().getEndpointUri() + "\")");
        }

        List<ProcessorDefinition> children = def.getOutputs();
        if (children == null || children.isEmpty()) {
            return;
        }

        for (ProcessorDefinition child : children) {
            navigateDefinition(child, sb);
        }
    }

}