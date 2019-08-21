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
package org.apache.camel.component.vm;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Test;

public class VmMultipleConsumersMultipleContextTest extends ContextTestSupport {

    @Test
    public void testMultipleVMConsumersSameContext() throws Exception {
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

        MockEndpoint mock1 = (MockEndpoint)camelContext.getEndpoint("mock:route1");
        MockEndpoint mock2 = (MockEndpoint)camelContext.getEndpoint("mock:route2");
        mock1.expectedMessageCount(100);
        mock2.expectedMessageCount(100);

        for (int i = 0; i < 100; i++) {
            producerTemplate.sendBody("vm:producer?multipleConsumers=true", i);
        }

        MockEndpoint.assertIsSatisfied(mock1, mock2);

        camelContext.stop();
    }

    @Test
    public void testVmMultipleConsumersMultipleContext() throws Exception {

        // start context 1
        CamelContext consumerContext1 = new DefaultCamelContext();
        consumerContext1.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("vm:producer?multipleConsumers=true").routeId("route1").to("mock:route1");
            }
        });
        consumerContext1.start();
        MockEndpoint route1Mock = (MockEndpoint)consumerContext1.getEndpoint("mock:route1");
        route1Mock.expectedMessageCount(100);

        // start up context 2
        CamelContext consumerContext2 = new DefaultCamelContext();
        consumerContext2.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("vm:producer?multipleConsumers=true").routeId("route2").to("mock:route2");
            }
        });
        consumerContext2.start();
        MockEndpoint route2Mock = (MockEndpoint)consumerContext2.getEndpoint("mock:route2");
        route2Mock.expectedMessageCount(100);

        // use context part of contextTestSupport to send in messages
        for (int i = 0; i < 100; i++) {
            template.sendBody("vm:producer?multipleConsumers=true", i);
        }

        route1Mock.assertIsSatisfied();
        route2Mock.assertIsSatisfied();

        consumerContext1.stop();
        consumerContext2.stop();
    }

    private CamelContext buildConsumerContext(final String route) throws Exception {
        DefaultCamelContext rc = new DefaultCamelContext();
        rc.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("vm:producer?multipleConsumers=true").routeId(route).to("mock:" + route);
            }
        });
        rc.start();
        return rc;
    }

    @Test
    public void testVmMultipleConsumersDifferentEndpoints() throws Exception {
        // start context 1
        CamelContext consumerContext1 = new DefaultCamelContext();
        consumerContext1.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("vm:producer?multipleConsumers=true").routeId("route1").to("mock:route1");
            }
        });
        consumerContext1.start();
        MockEndpoint route1Mock = (MockEndpoint)consumerContext1.getEndpoint("mock:route1");
        route1Mock.expectedMessageCount(100);

        // start up context 2
        CamelContext consumerContext2 = new DefaultCamelContext();
        consumerContext2.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("vm:foo?multipleConsumers=true").routeId("route2").to("mock:route2");
            }
        });
        consumerContext2.start();
        MockEndpoint route2Mock = (MockEndpoint)consumerContext2.getEndpoint("mock:route2");
        route2Mock.expectedMessageCount(0);

        // use context part of contextTestSupport to send in messages
        for (int i = 0; i < 100; i++) {
            template.sendBody("vm:producer?multipleConsumers=true", i);
        }

        route1Mock.assertIsSatisfied();
        route2Mock.assertIsSatisfied();

        consumerContext1.stop();
        consumerContext2.stop();
    }

}
