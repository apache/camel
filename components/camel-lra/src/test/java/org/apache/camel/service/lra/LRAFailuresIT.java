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
package org.apache.camel.service.lra;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Ignore;
import org.junit.Test;

public class LRAFailuresIT extends AbstractLRATestSupport {

    private AtomicInteger maxFailures;

    @Test
    @Ignore("https://issues.jboss.org/browse/JBTM-2978")
    public void testCompensationAfterFailures() throws Exception {
        maxFailures = new AtomicInteger(1);

        MockEndpoint compensate = getMockEndpoint("mock:compensate");
        compensate.expectedMessageCount(1);
        compensate.setResultWaitTime(300000);

        sendBody("direct:saga-compensate", "hello");

        compensate.assertIsSatisfied();
    }

    @Test
    @Ignore("https://issues.jboss.org/browse/JBTM-2977")
    public void testCompletionAfterFailures() throws Exception {
        maxFailures = new AtomicInteger(1);

        MockEndpoint complete = getMockEndpoint("mock:complete");
        complete.expectedMessageCount(1);
        complete.setResultWaitTime(300000);

        MockEndpoint end = getMockEndpoint("mock:end");
        end.expectedBodiesReceived("hello");

        sendBody("direct:saga-complete", "hello");

        complete.assertIsSatisfied();
        end.assertIsSatisfied();
    }


    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("direct:saga-compensate")
                        .saga()
                        .compensation("direct:compensate")
                        .process(x -> {
                            throw new RuntimeException("fail");
                        });

                from("direct:saga-complete")
                        .saga()
                        .completion("direct:complete")
                        .to("mock:end");

                from("direct:compensate")
                        .process(x -> {
                            int current = maxFailures.decrementAndGet();
                            if (current >= 0) {
                                throw new RuntimeException("compensation failure");
                            }
                        })
                        .to("mock:compensate");

                from("direct:complete")
                        .process(x -> {
                            int current = maxFailures.decrementAndGet();
                            if (current >= 0) {
                                throw new RuntimeException("completion failure");
                            }
                        })
                        .to("mock:complete");

            }
        };
    }

}
