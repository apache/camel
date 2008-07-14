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
package org.apache.camel.component.xquery;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.DeadLetterChannelBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Concurrency test of XQuery.
 */
public class XQueryConcurrencyTest extends ContextTestSupport {

    public void testConcurrency() throws Exception {
        int total = 100;

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(total);

        // setup a task executor to be able send the messages in parallel
        final CountDownLatch latch = new CountDownLatch(5);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.afterPropertiesSet();
        for (int i = 0; i < 5; i++) {
            final int threadCount = i;
            executor.execute(new Runnable() {
                public void run() {
                    // requestbody is InOut pattern and thus we expect a reply (JMSReply)
                    int start = threadCount * 20;
                    for (int i = 0; i < 20; i++) {
                        Object response = template.sendBody("seda:in",
                            "<person id='" + (start + i) + "'>James</person>");
                    }
                    latch.countDown();
                }
            });
        }
        // wait for test completion, timeout after 10 sec to let other unit test run to not wait forever
        latch.await(10000L, TimeUnit.MILLISECONDS);
        assertEquals("Latch should be zero", 0, latch.getCount());

        mock.assertIsSatisfied();
        mock.assertNoDuplicates(body());
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // only retry at max 2 times to cather
                // if set to 0 we can get interal Saxon errors - SENR0001
                errorHandler(new DeadLetterChannelBuilder().maximumRedeliveries(2));

                from("seda:in")
                    .thread(10)
                    .transform().xquery("/person/@id", String.class)
                    .to("mock:result");
            }
        };
    }
}
