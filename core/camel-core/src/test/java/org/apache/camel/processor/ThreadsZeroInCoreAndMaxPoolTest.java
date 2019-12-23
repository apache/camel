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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class ThreadsZeroInCoreAndMaxPoolTest extends ContextTestSupport {

    // ignore the test
    public void xtestThreadsCoreAndMaxPool() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testThreadsCoreBeZero() throws Exception {
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:start")
                        // will use a a custom thread pool with -1 in core and 2
                        // max
                        .threads(-1, 2).to("mock:result");
                }
            });
            fail("Expect FailedToCreateRouteException exception here");
        } catch (Exception ex) {
            assertTrue(ex.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testThreadsCoreAndMaxPoolBuilder() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("direct:foo")
                    // only change thread name and max, but rely on default
                    // settings
                    .threads().maxPoolSize(20).threadName("myPool").to("mock:result");
            }
        };
    }
}
