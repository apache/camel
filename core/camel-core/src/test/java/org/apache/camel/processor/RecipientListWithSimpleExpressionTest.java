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

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Isolated("This test creates a larger thread pool, which may be too much on slower hosts")
public class RecipientListWithSimpleExpressionTest extends ContextTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(RecipientListWithSimpleExpressionTest.class);
    private final ScheduledExecutorService executors = Executors.newScheduledThreadPool(10);
    private final Phaser phaser = new Phaser(50);

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").recipientList(simple("mock:${in.header.queue}"));
            }
        };
    }

    @BeforeEach
    void sendMessages() {
        // it may take a little while for the context to start on slower hosts
        Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> context.getUptimeMillis() > 1000);

        // use concurrent producers to send a lot of messages
        for (int i = 0; i < 50; i++) {
            final Runnable runOverRunnable = new Runnable() {
                int i;

                @Override
                public void run() {
                    template.sendBodyAndHeader("direct:start", "Hello " + i, "queue", i);
                    i++;
                    if (i == 10) {
                        i = 0;
                    }
                }
            };
            executors.scheduleAtFixedRate(runOverRunnable, 0, 50, TimeUnit.MILLISECONDS);
            phaser.arrive();
        }
    }


    @Test
    public void testRecipientList() throws InterruptedException, TimeoutException {
        for (int i = 0; i < 10; i++) {
            getMockEndpoint("mock:" + i).expectedMessageCount(50);
        }

        phaser.awaitAdvanceInterruptibly(0, 5000, TimeUnit.SECONDS);
        assertMockEndpointsSatisfied();
        executors.shutdownNow();
    }
}
