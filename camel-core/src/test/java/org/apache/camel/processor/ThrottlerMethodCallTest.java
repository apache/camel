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
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.util.StopWatch;

/**
 * @version
 */
public class ThrottlerMethodCallTest extends ContextTestSupport {
    private static final int INTERVAL = 100;
    protected int messageCount = 10;

    protected boolean canTest() {
        // skip test on windows as it does not run well there
        return !isPlatform("windows");
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myBean", this);
        return jndi;
    }

    public long getMessagesPerInterval() {
        return 3;
    }

    public void testConfigurationWithMethodCallExpression() throws Exception {
        if (!canTest()) {
            return;
        }

        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(messageCount);

        ExecutorService executor = Executors.newFixedThreadPool(messageCount);

        StopWatch watch = new StopWatch();
        for (int i = 0; i < messageCount; i++) {
            executor.execute(new Runnable() {
                public void run() {
                    template.sendBody("direct:expressionMethod", "<message>payload</message>");
                }
            });
        }

        // let's wait for the exchanges to arrive
        resultEndpoint.assertIsSatisfied();

        // should take a little time
        assertTrue(watch.taken() > 100);

        executor.shutdownNow();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:expressionMethod")
                    .throttle(method("myBean", "getMessagesPerInterval")).timePeriodMillis(INTERVAL)
                        .to("log:result", "mock:result");
            }
        };
    }
}