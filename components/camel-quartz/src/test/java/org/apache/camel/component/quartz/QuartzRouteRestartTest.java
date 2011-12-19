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
package org.apache.camel.component.quartz;

import java.util.Calendar;
import java.util.Date;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @version 
 */
public class QuartzRouteRestartTest extends CamelTestSupport {

    @Test
    @Ignore("CAMEL-4794")
    public void testQuartzCronRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.setResultWaitTime(20000);
        mock.expectedMinimumMessageCount(3);
        mock.message(1).arrives().between(9, 11).seconds().afterPrevious();
        mock.message(2).arrives().between(4, 6).seconds().afterPrevious();

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: e1
                from("quartz://groupName/timerName?cron=0/5+*+*+*+*+?").routeId("trigger")
                    .setBody(bean(CurrentTime.class))
                    // .to("log:QUARTZ")
                    .to("seda:control");

                from("seda:control").routeId("control")
                    // .to("log:CONTROL")
                    .to("mock:result")
                    .process(new Processor() {
                        private boolean DONE = false;
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            if (!DONE) {
                                DONE = true;
                                exchange.getContext().stopRoute("trigger");
                                Thread.sleep(7000);
                                exchange.getContext().startRoute("trigger");
                            }
                        }
                    });
                // END SNIPPET: e1
            }
        };
    }
   
    public static class CurrentTime {
        public Date get() {
            return Calendar.getInstance().getTime();
        }
    }
}