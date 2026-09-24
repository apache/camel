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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ThreadPoolBuilder;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Tests that a parallel EIP completes, with the rejection as exception, when the thread pool rejects the task of a
 * sub-exchange (and not only when it rejects the task of the EIP itself, see {@link SplitParallelThreadPoolAbortTest}).
 * <p/>
 * Each thread pool has a single thread and no queue. The EIP task itself runs on that thread, so the submission of the
 * first sub-exchange task is rejected.
 */
@Isolated
@Timeout(30)
class MulticastParallelSubTaskRejectedTest extends ContextTestSupport {

    @Test
    void testSplitSingleElement() throws Exception {
        assertRejected("direct:split", List.of("a"));
    }

    @Test
    void testMulticast() throws Exception {
        assertRejected("direct:multicast", "Hello World");
    }

    @Test
    void testRecipientList() throws Exception {
        assertRejected("direct:recipients", "mock:z");
    }

    private void assertRejected(String uri, Object body) throws Exception {
        Future<Exchange> future = template.asyncSend(uri, e -> e.getIn().setBody(body));

        // the exchange must complete and not hang forever
        Exchange out = future.get(5, TimeUnit.SECONDS);
        assertInstanceOf(RejectedExecutionException.class, out.getException());

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(0, context.getInflightRepository().size()));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:split")
                        .split(body()).executorService(newSingleThreadPool("split"))
                        .to("mock:x");

                from("direct:multicast")
                        .multicast().executorService(newSingleThreadPool("multicast"))
                        .to("mock:y");

                from("direct:recipients")
                        .recipientList(body()).executorService(newSingleThreadPool("recipients"));
            }

            private ExecutorService newSingleThreadPool(String name) throws Exception {
                return new ThreadPoolBuilder(getContext())
                        .poolSize(1)
                        .maxPoolSize(1)
                        .maxQueueSize(0)
                        .rejectedPolicy(ThreadPoolRejectedPolicy.Abort)
                        .build(name);
            }
        };
    }
}
