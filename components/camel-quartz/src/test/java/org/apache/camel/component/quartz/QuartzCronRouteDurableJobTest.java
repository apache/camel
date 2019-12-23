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

import org.apache.camel.builder.RouteBuilder;

/**
 * This test the CronTrigger as a timer endpoint in a route.
 */
public class QuartzCronRouteDurableJobTest extends QuartzCronRouteTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // triggers every 1th second at precise 00,01,02,03..59
                // notice we must use + as space when configured using URI parameter
                from("quartz://myGroup/myTimerName?durableJob=true&recoverableJob=true&cron=0/1+*+*+*+*+?").to("mock:result");
            }
        };
    }
}
