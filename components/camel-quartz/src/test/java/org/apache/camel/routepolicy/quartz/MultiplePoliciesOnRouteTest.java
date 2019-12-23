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
package org.apache.camel.routepolicy.quartz;

import java.util.Date;

import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.quartz.QuartzComponent;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.throttling.ThrottlingInflightRoutePolicy;
import org.junit.Test;

public class MultiplePoliciesOnRouteTest extends CamelTestSupport {
    private String url = "seda:foo?concurrentConsumers=20";
    private int size = 100;

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        registry.bind("startPolicy", createRouteStartPolicy());
        registry.bind("throttlePolicy", createThrottlePolicy());
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private RoutePolicy createRouteStartPolicy() {
        SimpleScheduledRoutePolicy policy = new SimpleScheduledRoutePolicy();
        long startTime = System.currentTimeMillis() + 3000L;
        policy.setRouteStartDate(new Date(startTime));
        policy.setRouteStartRepeatCount(1);
        policy.setRouteStartRepeatInterval(3000);

        return policy;
    }

    private RoutePolicy createThrottlePolicy() {
        ThrottlingInflightRoutePolicy policy = new ThrottlingInflightRoutePolicy();
        policy.setMaxInflightExchanges(10);
        return policy;
    }

    @Test
    public void testMultiplePoliciesOnRoute() throws Exception {
        MockEndpoint success = context.getEndpoint("mock:success", MockEndpoint.class);
        success.expectedMinimumMessageCount(size - 10);

        context.getComponent("quartz", QuartzComponent.class).setPropertiesFile("org/apache/camel/routepolicy/quartz/myquartz.properties");
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from(url)
                    .routeId("test")
                    .routePolicyRef("startPolicy, throttlePolicy")
                    .to("log:foo?groupSize=10")
                    .to("mock:success");
            }
        });
        context.start();

        assertTrue(context.getRouteController().getRouteStatus("test") == ServiceStatus.Started);
        for (int i = 0; i < size; i++) {
            template.sendBody(url, "Message " + i);
            Thread.sleep(3);
        }

        context.getComponent("quartz", QuartzComponent.class).stop();
        success.assertIsSatisfied();
    }

}
