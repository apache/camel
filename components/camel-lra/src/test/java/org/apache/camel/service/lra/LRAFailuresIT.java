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
package org.apache.camel.service.lra;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.TestSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;

public class LRAFailuresIT extends AbstractLRATestSupport {

    private AtomicInteger maxFailures;

    @Test
    public void testCompensationAfterFailures() throws Exception {
        maxFailures = new AtomicInteger(1);

        MockEndpoint compensate = getMockEndpoint("mock:compensate");
        compensate.expectedMessageCount(1);

        TestSupport.sendBody(template, "direct:saga-compensate", "hello");

        await().atMost(60, TimeUnit.SECONDS)
                .until(() -> compensate.getReceivedCounter() >= 1);
        compensate.assertIsSatisfied();
    }

    @Test
    public void testCompletionAfterFailures() throws Exception {
        maxFailures = new AtomicInteger(1);

        MockEndpoint complete = getMockEndpoint("mock:complete");
        complete.expectedMessageCount(1);

        MockEndpoint end = getMockEndpoint("mock:end");
        end.expectedBodiesReceived("hello");

        TestSupport.sendBody(template, "direct:saga-complete", "hello");

        // The Narayana LRA coordinator retries failed completion callbacks via its
        // periodic recovery manager. The container is configured to shorten the
        // recovery period to 2s (via JAVA_TOOL_OPTIONS), but this does not always
        // take effect (e.g., if the JVM ignores JAVA_TOOL_OPTIONS). In that case,
        // the default 120s recovery period applies, so we need a timeout that covers
        // the worst case: default period (120s) + backoff (10s) + margin.
        await().atMost(180, TimeUnit.SECONDS)
                .until(() -> complete.getReceivedCounter() >= 1
                        && end.getReceivedCounter() >= 1);
        complete.assertIsSatisfied();
        end.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {
            @Override
            public void configure() {

                from("direct:saga-compensate")
                        .saga()
                        .compensation("direct:compensate")
                        .process(x -> {
                            throw new RuntimeCamelException("fail");
                        });

                from("direct:saga-complete")
                        .saga()
                        .completion("direct:complete")
                        .to("mock:end");

                from("direct:compensate")
                        .process(x -> {
                            int current = maxFailures.decrementAndGet();
                            if (current >= 0) {
                                throw new RuntimeCamelException("compensation failure");
                            }
                        })
                        .to("mock:compensate");

                from("direct:complete")
                        .process(x -> {
                            int current = maxFailures.decrementAndGet();
                            if (current >= 0) {
                                throw new RuntimeCamelException("completion failure");
                            }
                        })
                        .to("mock:complete");

            }
        };
    }

}
