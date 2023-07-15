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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated("This test creates a larger thread pool, which may be too much on slower hosts")
public class RecipientListWithSimpleExpressionTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testRecipientList() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").recipientList(simple("mock:${in.header.queue}"));
            }
        });
        context.start();
        template.start();

        for (int i = 0; i < 10; i++) {
            getMockEndpoint("mock:" + i).expectedMessageCount(50);
        }

        // use concurrent producers to send a lot of messages
        ExecutorService executors = Executors.newFixedThreadPool(10);
        for (int i = 0; i < 50; i++) {
            executors.execute(new Runnable() {
                public void run() {
                    for (int i = 0; i < 10; i++) {
                        try {
                            template.sendBodyAndHeader("direct:start", "Hello " + i, "queue", i);
                            Thread.sleep(5);
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                }
            });
        }

        assertMockEndpointsSatisfied();
        executors.shutdownNow();
    }
}
