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

import java.util.concurrent.CountDownLatch;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.ThrottlingInflightRoutePolicy;

/**
 * @version 
 */
public class ThrottlingInflightRoutePolicyContextScopeTest extends ContextTestSupport {

    private final CountDownLatch latch = new CountDownLatch(1);

    public void testThrottlingRoutePolicy() throws Exception {
        // trigger one in flight from the start
        template.sendBody("seda:bar", "Hello World");

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("A");
        result.setMinimumResultWaitTime(1000);

        // only 1 message will get completed as the throttler will suspend the consumer
        // when A is done
        template.sendBody("direct:start", "A");

        // need a little slack to ensure the seda consumer will be suspended in between
        Thread.sleep(2000);
        template.sendBody("direct:start", "B");

        result.assertIsSatisfied();

        result.reset();
        result.expectedBodiesReceived("B");

        // trigger seda:bar to complete now, which should signal
        // to the throttler to resume the seda:foo consumer, so B can get done
        latch.countDown();

        result.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                ThrottlingInflightRoutePolicy policy = new ThrottlingInflightRoutePolicy();
                policy.setMaxInflightExchanges(1);
                policy.setScope(ThrottlingInflightRoutePolicy.ThrottlingScope.Context);

                from("seda:bar")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            latch.await();
                        }
                    }).to("mock:bar");

                from("direct:start")
                    .to("seda:foo");

                from("seda:foo")
                    .routePolicy(policy)
                    .to("log:foo")
                    .to("mock:result");
            }
        };
    }
}
