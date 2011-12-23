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
import org.junit.Test;

/**
 * @version 
 */
public class QuartzRouteRestartTest extends CamelTestSupport {

    @Test
    public void testQuartzCronRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.setResultWaitTime(15000);
        mock.expectedMinimumMessageCount(3);
        mock.message(0).arrives().between(7, 9).seconds().beforeNext();
        mock.message(2).arrives().between(3, 5).seconds().afterPrevious();

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: e1
                from("quartz://groupName/timerName?cron=0/4+*+*+*+*+?").routeId("trigger")
                    .setBody(bean(CurrentTime.class))
                    // .to("log:QUARTZ")
                    .to("seda:control");

                from("seda:control").routeId("control")
                    // .to("log:CONTROL")
                    .to("mock:result")
                    .process(new Processor() {
                        private boolean done;
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            if (!done) {
                                done = true;
                                exchange.getContext().stopRoute("trigger");
                                Thread.sleep(5000);
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
