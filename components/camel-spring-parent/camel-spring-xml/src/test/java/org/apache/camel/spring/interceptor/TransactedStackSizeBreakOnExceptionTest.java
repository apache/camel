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
package org.apache.camel.spring.interceptor;

import java.util.StringJoiner;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TransactedStackSizeBreakOnExceptionTest extends TransactionClientDataSourceSupport {

    private static final boolean PRINT_STACK_TRACE = false;
    private int total = 100;
    private int failAt = 85;

    @Test
    public void testStackSize() throws Exception {
        getMockEndpoint("mock:line").expectedMinimumMessageCount(failAt);
        getMockEndpoint("mock:line").assertNoDuplicates(body());
        getMockEndpoint("mock:result").expectedMessageCount(0);

        StringJoiner sb = new StringJoiner(",");
        for (int i = 0; i < total; i++) {
            sb.add(Integer.toString(i));
        }

        template.sendBody("seda:start", sb.toString());

        assertMockEndpointsSatisfied();

        int[] sizes = new int[failAt];
        for (int i = 0; i < failAt; i++) {
            int size = getMockEndpoint("mock:line").getReceivedExchanges().get(i).getMessage().getHeader("stackSize",
                    int.class);
            sizes[i] = size;
            Assertions.assertTrue(size < 110, "Stackframe should be < 110");
            log.debug("#{} size {}", i, size);
        }

        int prev = sizes[0];
        // last may be shorter, so use total - 1
        for (int i = 1; i < failAt; i++) {
            int size = sizes[i];
            Assertions.assertEquals(prev, size, "Stackframe should be same size");
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:start")
                    .transacted()
                    .setHeader("stackSize", TransactedStackSizeBreakOnExceptionTest::currentStackSize)
                    .log("BEGIN: ${body} stack-size ${header.stackSize}")
                    .split(body()).stopOnException()
                        .setHeader("stackSize", TransactedStackSizeBreakOnExceptionTest::currentStackSize)
                        .log("LINE: ${body} stack-size ${header.stackSize}")
                        .to("mock:line")
                        .filter(header(Exchange.SPLIT_INDEX).isEqualTo(failAt))
                            .throwException(new IllegalStateException("Forced"))
                        .end()
                    .end()
                    .setHeader("stackSize", TransactedStackSizeBreakOnExceptionTest::currentStackSize)
                    .log("RESULT: ${body} stack-size ${header.stackSize}")
                    .to("mock:result");
            }
        };
    }

    public static int currentStackSize() {
        int depth = Thread.currentThread().getStackTrace().length;
        if (PRINT_STACK_TRACE) {
            new Throwable("Printing Stacktrace depth: " + depth).printStackTrace(System.err);
        }
        return depth;
    }

}
