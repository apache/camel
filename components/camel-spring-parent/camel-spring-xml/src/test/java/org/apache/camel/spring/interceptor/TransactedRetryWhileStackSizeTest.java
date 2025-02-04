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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TransactedRetryWhileStackSizeTest extends TransactionClientDataSourceSupport {

    private static final Logger LOG = LoggerFactory.getLogger(TransactedRetryWhileStackSizeTest.class);
    private static final boolean PRINT_STACK_TRACE = false;
    private static final int MAX_DEPTH = 100;

    @Test
    public void testStackSize() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(1);
        getMockEndpoint("mock:error").message(0).body().isInstanceOf(MyCoolDude.class);

        MyCoolDude dude = new MyCoolDude();
        template.sendBody("seda:start", dude);

        assertMockEndpointsSatisfied();

        assertEquals(1000 + 1, dude.getCounter());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException().retryWhile(simple("${body.areWeCool} == 'no'")).redeliveryDelay(0)
                        .handled(true).to("mock:error");

                from("seda:start")
                        .transacted()
                        .recipientList(constant("foo:unknown"));
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

    public static class MyCoolDude {

        private int counter;

        public String areWeCool() {
            int size = currentStackSize();
            if (size > MAX_DEPTH) {
                LOG.error("Stacktrace max depth: {}", size);
                return "no";
            }
            if (counter++ < 1000) {
                return "no";
            } else {
                return "yes";
            }
        }

        public int getCounter() {
            return counter;
        }
    }

}
