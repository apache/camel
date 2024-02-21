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
package org.apache.camel.component.microprofile.faulttolerance;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CircuitBreakerConstants;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class FaultToleranceRouteRejectedTest extends CamelTestSupport {

    private static int counter;

    @Test
    public void testFaultTolerance() throws Exception {
        counter = 0;

        // send message which should get rejected, so the message is not changed
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Again");
        getMockEndpoint("mock:result").expectedPropertyReceived(CircuitBreakerConstants.RESPONSE_SHORT_CIRCUITED, true);
        getMockEndpoint("mock:result").expectedPropertyReceived(CircuitBreakerConstants.RESPONSE_REJECTED, true);

        // should fail first time and trigger from closed to open state
        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should throw exception");
        } catch (Exception e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Forced", e.getCause().getMessage());
        }
        // should be okay 2nd time, where the CB is open, so we skip and continue routing
        template.sendBody("direct:start", "Hello Again");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .circuitBreaker().id("myFaultTolerance")
                        .faultToleranceConfiguration().failureRatio(100).successThreshold(1).requestVolumeThreshold(1).end()
                        .process(e -> {
                            if (counter++ < 1) {
                                throw new IllegalArgumentException("Forced");
                            }
                        })
                        .to("direct:foo").to("log:foo")
                        .end()
                        .to("log:result").to("mock:result");

                from("direct:foo").transform().constant("Bye World");
            }
        };
    }

}
