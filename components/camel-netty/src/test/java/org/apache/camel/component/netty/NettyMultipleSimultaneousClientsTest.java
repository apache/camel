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
package org.apache.camel.component.netty;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.junit.Ignore;
import org.junit.Test;

/**
 *
 */
@Ignore("TODO: investigate for Camel 3.0")
public class NettyMultipleSimultaneousClientsTest extends BaseNettyTest {

    private String uri = "netty:tcp://localhost:{{port}}?sync=true&reuseAddress=true&synchronous=false";
    private int clientCount = 20;
    private CountDownLatch startLatch = new CountDownLatch(1);
    private CountDownLatch finishLatch = new CountDownLatch(clientCount);

    @Test
    public void testSimultaneousClients() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(clientCount);
        Future<?>[] replies = new Future[clientCount];

        for (int i = 0; i < clientCount; i++) {
            replies[i] = executorService.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    // wait until we're allowed to start
                    startLatch.await();

                    Object reply = template.requestBody(uri, "World");

                    // signal that we're done now
                    finishLatch.countDown();

                    return reply;
                }
            });
        }

        Object[] expectedReplies = new Object[clientCount];
        Arrays.fill(expectedReplies, "Bye World");

        getMockEndpoint("mock:result").expectedMessageCount(clientCount);
        getMockEndpoint("mock:result").expectedBodiesReceived(expectedReplies);

        // fire the simultaneous client calls
        startLatch.countDown();

        // and wait long enough until they're all done
        assertTrue("Waiting on the latch ended up with a timeout!", finishLatch.await(5, TimeUnit.SECONDS));

        executorService.shutdown();

        // assert on what we expect to receive
        for (int i = 0; i < clientCount; i++) {
            assertEquals("Bye World", replies[i].get());
        }
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(uri)
                    .log("${body}")
                    .transform(body().prepend("Bye "))
                    .to("mock:result");
            }
        };
    }
}
