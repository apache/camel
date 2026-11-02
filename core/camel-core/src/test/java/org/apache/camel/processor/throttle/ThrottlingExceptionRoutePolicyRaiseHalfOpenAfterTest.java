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
package org.apache.camel.processor.throttle;

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.throttling.ThrottlingExceptionRoutePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;

/**
 * Raising halfOpenAfter while the circuit is open delays the half open check, it must not leave the route suspended.
 */
class ThrottlingExceptionRoutePolicyRaiseHalfOpenAfterTest extends ContextTestSupport {

    private final ThrottlingExceptionRoutePolicy policy = new ThrottlingExceptionRoutePolicy(1, 60000, 2000, null);

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        context.getShutdownStrategy().setTimeout(1);
    }

    @Test
    void testRaisedHalfOpenAfterHalfOpensTheCircuit() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.whenExchangeReceived(1, e -> e.setException(new ThrottlingException("boom")));
        result.expectedBodiesReceived("fail", "ok");

        template.sendBody("seda:start", "fail");
        ServiceSupport consumer = (ServiceSupport) context.getRoute("start").getConsumer();
        await().atMost(10, TimeUnit.SECONDS).until(consumer::isSuspended);

        // as over JMX: the half open timer is due after the old value, before the new one
        policy.setHalfOpenAfter(3000);

        await().atMost(10, TimeUnit.SECONDS).until(consumer::isStarted);
        template.sendBody("seda:start", "ok");
        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:start").routeId("start").routePolicy(policy).to("mock:result");
            }
        };
    }
}
