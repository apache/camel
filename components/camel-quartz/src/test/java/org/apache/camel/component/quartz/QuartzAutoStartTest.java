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

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class QuartzAutoStartTest extends BaseQuartzTest {

    @Test
    public void testQuartzAutoStart() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:one");
        mock.expectedMessageCount(0);

        QuartzComponent quartz = context.getComponent("quartz", QuartzComponent.class);
        assertFalse(quartz.getScheduler().isStarted(), "Should not have started scheduler");

        Awaitility.await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> MockEndpoint.assertIsSatisfied(context));

        MockEndpoint.assertIsSatisfied(context);

        mock.reset();
        mock.expectedMinimumMessageCount(1);

        // start scheduler

        quartz.getScheduler().start();

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("quartz://myGroup/myTimerName?cron=0/1+*+*+*+*+?&autoStartScheduler=false").to("mock:one");
            }
        };
    }
}
