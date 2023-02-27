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
package org.apache.camel.component.scheduler;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Manual test")
public class SchedulerBlockingManualTest extends ContextTestSupport {

    @Test
    public void testScheduler() throws Exception {
        Thread.sleep(60000);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                SchedulerComponent comp = getContext().getComponent("scheduler", SchedulerComponent.class);
                comp.setPoolSize(4);

                from("scheduler://trigger?delay=2000&repeatCount=3").routeId("scheduler")
                        .threads(10)
                        .log("1")
                        .to(ExchangePattern.InOut, "seda:route1")
                        .log("1.1");

                from("seda:route1?concurrentConsumers=2").routeId("first route")
                        .log("2")
                        .delay(5000)
                        .log("2.1")
                        .to(ExchangePattern.InOut, "seda:route2")
                        .log("2.2");

                from("seda:route2").routeId("second route")
                        .log("3")
                        .delay(3000)
                        .log("3.1");
            }
        };
    }
}
