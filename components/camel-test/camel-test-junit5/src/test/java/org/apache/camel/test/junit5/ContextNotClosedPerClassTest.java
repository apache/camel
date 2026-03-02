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
package org.apache.camel.test.junit5;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces a bug where the CamelContext is never stopped when using {@code @TestInstance(Lifecycle.PER_CLASS)}.
 * <p>
 * {@link ContextManagerExtension#afterAll} only calls {@link CamelContextManager#stop()}, but
 * {@link LegacyCamelContextManager#stop()} is a NO-OP. The actual shutdown logic lives in
 * {@link LegacyCamelContextManager#close()}, which is never invoked.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContextNotClosedPerClassTest extends CamelTestSupport {

    // @Order(0) ensures this runs AFTER ContextManagerExtension(@Order(1))'s afterAll,
    // because "after" callbacks execute in reverse registration order.
    @RegisterExtension
    @Order(0)
    AfterAllCallback contextStopVerifier = extensionContext -> {
        assertTrue(context.isStopped(),
                "CamelContext should be stopped after all tests completed, but status is: " + context.getStatus());
    };

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").to("mock:result");
            }
        };
    }

    @Test
    void testRouteWorks() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello");
        getMockEndpoint("mock:result").assertIsSatisfied();
    }
}
