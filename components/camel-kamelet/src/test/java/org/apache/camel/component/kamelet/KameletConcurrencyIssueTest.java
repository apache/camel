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
package org.apache.camel.component.kamelet;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.LoggingLevel;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Manual test")
public class KameletConcurrencyIssueTest extends CamelTestSupport {

    @Test
    public void testConcurrency() throws Exception {
        // check there are no exception throw during creating kamelets
        Thread.sleep(120000);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                List<Integer> splitSpam = new ArrayList<>();
                for (int i = 0; i < 500; i++) {
                    splitSpam.add(i);
                }

                from("timer://spamroute?fixedRate=true&period=5")
                        .routeId("BreakingRoute")
                        .process(exchange -> {
                            exchange.getIn().setBody(splitSpam);
                            exchange.setVariable("numberMs", System.currentTimeMillis() % 1000);
                        })
                        .split(body())
                        .parallelProcessing()
                        .process(exchange -> {
                            exchange.setVariable("number", exchange.getIn().getBody(Integer.class));
                        })
                        .toD("kamelet:spamRoute?number=${variable.number}&differentValue=${variable.numberMs}")
                        .end()
                        .log(LoggingLevel.INFO, "Ending");

                routeTemplate("spamRoute")
                        .templateParameter("number", "0")
                        .templateParameter("differentValue", "0")
                        .from("kamelet:source")
                        .routeId("spamRoute")
                        .toD("kamelet:spamRoute2?numberAgain={{number}}&differentValueAgain={{differentValue}}&time="
                             + System.currentTimeMillis() % 1000)
                        .toD("kamelet:spamRoute2?numberAgain={{number}}&differentValueAgain=5&time="
                             + System.currentTimeMillis() % 1000)
                        .toD("kamelet:spamRoute2?numberAgain={{number}}&differentValueAgain=10&time="
                             + System.currentTimeMillis() % 1000);

                routeTemplate("spamRoute2")
                        .templateParameter("numberAgain")
                        .templateParameter("differentValueAgain")
                        .templateParameter("time")
                        .from("kamelet:source")
                        .routeId("spamRoute2")
                        .log("Spam route {{numberAgain}} {{differentValueAgain}} {{time}}")
                        .toD("log:valuespam");
            }
        };
    }
}
