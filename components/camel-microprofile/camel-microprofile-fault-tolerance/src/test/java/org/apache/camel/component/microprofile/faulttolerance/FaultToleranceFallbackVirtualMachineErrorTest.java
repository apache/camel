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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A VirtualMachineError (such as a StackOverflowError) inside a circuit breaker fails the exchange: the fallback does
 * not recover from it, and the rest of the route does not run (CAMEL-27515).
 */
public class FaultToleranceFallbackVirtualMachineErrorTest extends CamelTestSupport {

    @Test
    public void fallbackDoesNotRecoverFromAVirtualMachineError() throws Exception {
        getMockEndpoint("mock:fallback").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:start", "Hello World"));

        assertInstanceOf(StackOverflowError.class, ObjectHelper.getVirtualMachineError(e));
        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .circuitBreaker()
                        .process(e -> {
                            throw new StackOverflowError("simulated");
                        })
                        .onFallback()
                        .to("mock:fallback")
                        .end()
                        .to("mock:result");
            }
        };
    }
}
