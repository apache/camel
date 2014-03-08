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
package org.apache.camel.routepolicy.quartz;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Test CronScheduledRoutePolicy also works if the route has been configured
 * with noAutoStartup
 */
public class RouteAutoStopFalseCronScheduledPolicyTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testCronPolicy() throws Exception {
        // send a message on the seda queue so we have a message to start with
        template.sendBody("seda:foo", "Hello World");

        getMockEndpoint("mock:foo").expectedMessageCount(1);

        final CronScheduledRoutePolicy policy = new CronScheduledRoutePolicy();
        policy.setRouteStartTime("*/5 * * * * ?");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo").routeId("foo").noAutoStartup()
                        .routePolicy(policy)
                        .to("mock:foo");
            }
        });
        context.start();

        assertMockEndpointsSatisfied();
    }

}
