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
package org.apache.camel.component.sjms.it;

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.body;

/**
 * Integration test that verifies the ability of SJMS to correctly process synchronous InOut exchanges from both the
 * Producer and Consumer perspective using a temporary destination.
 */
public class SyncJmsInOutTempDestIT extends JmsTestSupport {

    @Test
    public void testSynchronous() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(100);
        mock.expectsNoDuplicates(body());

        StopWatch watch = new StopWatch();

        for (int i = 0; i < 100; i++) {
            template.sendBody("seda:start.SyncJmsInOutTempDestIT", "" + i);
        }

        // just in case we run on slow boxes
        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);

        log.info("Took {} ms. to process 100 messages request/reply over JMS", watch.taken());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:start.SyncJmsInOutTempDestIT")
                        .to("sjms:in.foo.tempQ.SyncJmsInOutTempDestIT?exchangePattern=InOut")
                        .to("mock:result");

                from("sjms:in.foo.tempQ.SyncJmsInOutTempDestIT?exchangePattern=InOut")
                        .log("Using ${threadName} to process ${body}")
                        .transform(body().prepend("Bye "));
            }
        };
    }
}
