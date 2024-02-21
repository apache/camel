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
package org.apache.camel.component.jmx;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class CamelJmxConsumerObserveAttributeTest extends CamelTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Test
    public void testJmxConsumer() throws Exception {
        getMockEndpoint("mock:result").expectedMinimumMessageCount(1);
        getMockEndpoint("mock:result").message(0).body().contains("<newValue>true</newValue>");
        getMockEndpoint("mock:result").message(0).body().contains("<attributeName>Tracing</attributeName>");

        // change the attribute so JMX triggers but should be filtered
        ManagedRouteMBean mr
                = context.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class).getManagedRoute("foo");
        mr.setStatisticsEnabled(true);

        // change the attribute so JMX triggers
        mr = context.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class).getManagedRoute("foo");
        mr.setTracing(true);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String id = getContext().getName();

                fromF("jmx:platform?objectDomain=org.apache.camel&key.context=%s&key.type=routes&key.name=\"foo\"&observedAttribute=Tracing",
                        id).routeId("jmxRoute")
                        .to("log:jmx")
                        .to("mock:result");

                from("direct:foo").routeId("foo").to("log:foo", "mock:foo");
            }
        };
    }
}
