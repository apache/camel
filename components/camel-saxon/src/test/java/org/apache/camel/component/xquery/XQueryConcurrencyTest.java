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
package org.apache.camel.component.xquery;

import java.util.Random;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Concurrency test of XQuery using transform.xquery DSL.
 */
public class XQueryConcurrencyTest extends CamelTestSupport {

    private String uri = "seda:in?concurrentConsumers=5";

    @Test
    public void testConcurrency() throws Exception {
        int total = 1000;

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(total);

        // setup a task executor to be able send the messages in parallel
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.afterPropertiesSet();
        for (int i = 0; i < 5; i++) {
            final int threadCount = i;
            executor.execute(new Runnable() {
                public void run() {
                    int start = threadCount * 200;
                    for (int i = 0; i < 200; i++) {
                        try {
                            // do some random sleep to simulate spread in user activity
                            Thread.sleep(new Random().nextInt(10));
                        } catch (InterruptedException e) {
                            // ignore
                        }
                        template.sendBody(uri, "<person><id>" + (start + i + 1) + "</id><name>James</name></person>");
                    }
                }
            });
        }

        mock.assertNoDuplicates(body());

        assertMockEndpointsSatisfied();
        executor.shutdown();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // no retry as we want every failure to submerge
                errorHandler(noErrorHandler());

                from(uri)
                    .transform().xquery("/person/id", String.class)
                    .to("mock:result");
            }
        };
    }
}
