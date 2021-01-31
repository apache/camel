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

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

public class TransactedStackSizeTest extends TransactionClientDataSourceSupport {

    private static final boolean PRINT_STACK_TRACE = true;

    @Test
    public void testStackSize() throws Exception {
        getMockEndpoint("mock:line").expectedMessageCount(10);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("seda:start", "A,B,C,D,E,F,G,H,I,J");

        assertMockEndpointsSatisfied();

        int[] sizes = new int[11];
        for (int i = 0; i < 10; i++) {
            int size = getMockEndpoint("mock:line").getReceivedExchanges().get(i).getMessage().getHeader("stackSize",
                    int.class);
            sizes[i] = size;
            log.info("#{} size {}", i, size);
        }
        int size = getMockEndpoint("mock:result").getReceivedExchanges().get(0).getMessage().getHeader("stackSize", int.class);
        sizes[10] = size;
        log.info("#{} size {}", 10, size);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:start")
                    .transacted()
                    .split(body())
                        .setHeader("stackSize", TransactedStackSizeTest::currentStackSize)
                        .to("mock:line")
                    .end()
                    .setHeader("stackSize", TransactedStackSizeTest::currentStackSize)
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
