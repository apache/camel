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
package org.apache.camel.example.micrometer;

import java.util.Random;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class ScheduledRouteBuilder extends RouteBuilder {

    static final String TIMER_ROUTE_ID = "timer:foo";
    private Random random = new Random(System.currentTimeMillis());

    @Override
    public void configure() throws Exception {
        from("timer:foo?period=1s&fixedRate=true")
                .routeId(TIMER_ROUTE_ID)
                .setHeader("random").exchange(exchange -> random.nextInt(100))
                .log(LoggingLevel.INFO, "Delay is ${header.random}")
                .to("direct:bar");

        from("direct:bar")
                .routeId("direct:bar")
                .to("micrometer:summary:summary?value=${header.random}")
                .to("micrometer:timer:timer?action=start")
                .delay().exchange(exchange -> random.nextInt(100))
                .to("micrometer:timer:timer?action=stop");
    }
}
