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
package org.apache.camel.processor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class ThrottlerAsyncDelayedTest extends ContextTestSupport {
    private static final int INTERVAL = 500;
    protected int messageCount = 9;

    public void testSendLotsOfMessages() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(messageCount);

        for (int i = 0; i < messageCount; i++) {
            template.sendBody("seda:a", "<message>" + i + "</message>");
        }

        resultEndpoint.assertIsSatisfied();
    }

    public void testSendLotsOfMessagesSimultaneously() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(messageCount);

        ExecutorService executor = Executors.newFixedThreadPool(messageCount);
        for (int i = 0; i < messageCount; i++) {
            executor.execute(new Runnable() {
                public void run() {
                    template.sendBody("direct:a", "<message>payload</message>");
                }
            });
        }

        // let's wait for the exchanges to arrive
        resultEndpoint.assertIsSatisfied();

        context.stop();
        
        executor.shutdownNow();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: ex
                from("seda:a").throttle(3).timePeriodMillis(INTERVAL).asyncDelayed().to("log:result", "mock:result");
                // END SNIPPET: ex

                from("direct:a").throttle(3).timePeriodMillis(INTERVAL).asyncDelayed().to("log:result", "mock:result");
            }
        };
    }
}