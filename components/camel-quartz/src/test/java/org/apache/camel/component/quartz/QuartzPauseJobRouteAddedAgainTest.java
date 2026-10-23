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
package org.apache.camel.component.quartz;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

/**
 * A route that keeps its trigger when it stops (deleteJob=false, also with pauseJob=true) pauses the trigger, and it
 * must fire again when the route is added again (such as a reload), as it does when the same route is started again.
 */
class QuartzPauseJobRouteAddedAgainTest extends BaseQuartzTest {

    @Test
    void pauseJobTriggerFiresAgainWhenTheRouteIsAddedAgain() throws Exception {
        assertFiresAgainWhenAddedAgain("paused", "pauseJob=true&deleteJob=false");
    }

    @Test
    void keptTriggerFiresAgainWhenTheRouteIsAddedAgain() throws Exception {
        assertFiresAgainWhenAddedAgain("kept", "deleteJob=false");
    }

    @Test
    void triggerFiresAgainWhenTheRouteIsStartedAgain() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:paused");
        mock.expectedMinimumMessageCount(1);
        mock.assertIsSatisfied();

        context.getRouteController().stopRoute("paused");
        context.getRouteController().startRoute("paused");

        mock.reset();
        mock.expectedMinimumMessageCount(1);
        mock.assertIsSatisfied();
    }

    private void assertFiresAgainWhenAddedAgain(String routeId, String options) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:" + routeId);
        mock.expectedMinimumMessageCount(1);
        mock.assertIsSatisfied();

        context.getRouteController().stopRoute(routeId);
        context.removeRoute(routeId);
        context.addRoutes(route(routeId, options));

        // removing the route removed its mock endpoint too, the route added again has a new one
        MockEndpoint again = getMockEndpoint("mock:" + routeId);
        again.expectedMinimumMessageCount(1);
        again.assertIsSatisfied();
    }

    private static RouteBuilder route(String routeId, String options) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("quartz://myGroup/" + routeId + "?trigger.repeatInterval=100&" + options)
                        .routeId(routeId)
                        .to("mock:" + routeId);
            }
        };
    }

    @Override
    protected RoutesBuilder[] createRouteBuilders() {
        return new RoutesBuilder[] { route("paused", "pauseJob=true&deleteJob=false"), route("kept", "deleteJob=false") };
    }
}
