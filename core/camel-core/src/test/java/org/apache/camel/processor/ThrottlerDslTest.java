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
package org.apache.camel.processor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class ThrottlerDslTest extends ContextTestSupport {
    private static final int INTERVAL = 500;
    protected int messageCount = 9;

    protected boolean canTest() {
        // skip test on windows as it does not run well there
        return !isPlatform("windows");
    }

    @Test
    public void testDsl() throws Exception {
        if (!canTest()) {
            return;
        }

        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(messageCount);

        ExecutorService executor = Executors.newFixedThreadPool(messageCount);

        long start = System.currentTimeMillis();
        for (int i = 0; i < messageCount; i++) {
            executor.execute(() -> template.sendBodyAndHeader("direct:start", "payload", "ThrottleCount", 1));
        }

        // let's wait for the exchanges to arrive
        resultEndpoint.assertIsSatisfied();

        // now assert that they have actually been throttled
        long minimumTime = (messageCount - 1) * INTERVAL;
        // add a little slack
        long delta = System.currentTimeMillis() - start + 200;
        assertTrue("Should take at least " + minimumTime + "ms, was: " + delta, delta >= minimumTime);
        executor.shutdownNow();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").throttle().message(m -> m.getHeader("ThrottleCount", Integer.class)).timePeriodMillis(INTERVAL).to("log:result", "mock:result");
            }
        };
    }
}
