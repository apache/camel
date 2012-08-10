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
package org.apache.camel.component.directvm;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.ServiceHelper;
import org.junit.After;
import org.junit.Before;

/**
 *
 */
public class DirectVmConsumerExpressionTest extends ContextTestSupport {

    private CamelContext context2;
    private CamelContext context3;
    private CamelContext context4;

    @Override
    @Before
    protected void setUp() throws Exception {
        super.setUp();

        context2 = new DefaultCamelContext();
        context3 = new DefaultCamelContext();
        context4 = new DefaultCamelContext();

        ServiceHelper.startServices(context2);
        ServiceHelper.startServices(context3);
        ServiceHelper.startServices(context4);

        // add routes after CamelContext has been started
        RouteBuilder routeBuilder = createRouteBuilderCamelContext2();
        if (routeBuilder != null) {
            context2.addRoutes(routeBuilder);
        }

        routeBuilder = createRouteBuilderCamelContext3();
        if (routeBuilder != null) {
            context3.addRoutes(routeBuilder);
        }
        
        routeBuilder = createRouteBuilderCamelContext4();
        if (routeBuilder != null) {
            context4.addRoutes(routeBuilder);
        }
    }

    @Override
    @After
    protected void tearDown() throws Exception {
        ServiceHelper.stopServices(context2, context3, context4);
        super.tearDown();
    }

    public void testSelectEndpoint() throws Exception {
        MockEndpoint result2 = context2.getEndpoint("mock:result2", MockEndpoint.class);
        result2.expectedBodiesReceived("Hello World");

        MockEndpoint result3 = context3.getEndpoint("mock:result3", MockEndpoint.class);
        result3.expectedBodiesReceived("Hello World");
        
        MockEndpoint result4 = context4.getEndpoint("mock:result4", MockEndpoint.class);
        result4.expectedMessageCount(0);

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context2);
        MockEndpoint.assertIsSatisfied(context3);
        MockEndpoint.assertIsSatisfied(context4);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .recipientList(new DirectVmConsumerExpression("direct-vm://parent/**/context*"));
            }
        };
    }

    private RouteBuilder createRouteBuilderCamelContext2() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct-vm:parent/child/context2")
                    .to("mock:result2");
            }
        };
    }

    private RouteBuilder createRouteBuilderCamelContext3() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct-vm:parent/child/grandchild/context3")
                    .to("mock:result3");
            }
        };
    }

    private RouteBuilder createRouteBuilderCamelContext4() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct-vm:parent/child/ctx4")
                    .to("mock:result4");
            }
        };
    }
}
