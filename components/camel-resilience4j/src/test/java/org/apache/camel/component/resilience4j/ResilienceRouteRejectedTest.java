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
package org.apache.camel.component.resilience4j;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResilienceRouteRejectedTest extends CamelTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Test
    public void testResilience() throws Exception {
        Exchange out = test("direct:start", "myResilience");
        assertFalse(out.isFailed());
    }

    @Test
    public void testResilienceWithTimeOut() throws Exception {
        Exchange out = test("direct:start.with.timeout.enabled", "myResilienceWithTimeout");
        assertFalse(out.isFailed());
    }

    @Test
    public void testResilienceWithThrowException() throws Exception {
        Exchange out = test("direct:start-throw-exception", "myResilienceWithThrowException");
        assertTrue(out.isFailed());
        Exception e = out.getException();
        CallNotPermittedException ce = assertInstanceOf(CallNotPermittedException.class, e);
        assertEquals("myResilienceWithThrowException", ce.getCausingCircuitBreakerName());
        assertEquals("CircuitBreaker 'myResilienceWithThrowException' is FORCED_OPEN and does not permit further calls",
                ce.getMessage());
        assertEquals("FORCED_OPEN", out.getProperty(ExchangePropertyKey.CIRCUIT_BREAKER_RESPONSE_STATE));
    }

    private Exchange test(String endPointUri, String circuitBreakerName) throws Exception {
        // look inside jmx
        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();

        // context name
        String name = context.getManagementName();

        ObjectName on = ObjectName
                .getInstance("org.apache.camel:context=" + name + ",type=processors,name=\"" + circuitBreakerName + "\"");

        // force it into open state
        mbeanServer.invoke(on, "transitionToForcedOpenState", null, null);
        String state = (String) mbeanServer.getAttribute(on, "CircuitBreakerState");
        assertEquals("FORCED_OPEN", state);

        // send message which should get rejected, so the message is not changed
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        Exchange answer = template.send(endPointUri, e -> e.getMessage().setBody("Hello World"));
        if (answer.isFailed()) {
            return answer;
        }

        MockEndpoint.assertIsSatisfied(context);
        return answer;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").circuitBreaker().id("myResilience").to("direct:foo").to("log:foo").end().to("log:result")
                        .to("mock:result");

                from("direct:start.with.timeout.enabled").circuitBreaker().resilience4jConfiguration()
                        .timeoutEnabled(true).timeoutDuration(2000).end()
                        .id("myResilienceWithTimeout").to("direct:foo").to("log:foo").end().to("log:result")
                        .to("mock:result");

                from("direct:start-throw-exception")
                        .circuitBreaker().resilience4jConfiguration().throwExceptionWhenHalfOpenOrOpenState(true).end()
                        .id("myResilienceWithThrowException").to("direct:foo").to("log:foo").end().to("log:result")
                        .to("mock:result");

                from("direct:foo").transform().constant("Bye World");
            }
        };
    }

}
