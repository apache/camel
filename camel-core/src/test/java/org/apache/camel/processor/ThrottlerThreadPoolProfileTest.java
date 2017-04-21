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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ThreadPoolProfileBuilder;
import org.apache.camel.spi.ThreadPoolProfile;

public class ThrottlerThreadPoolProfileTest extends ContextTestSupport {

    protected boolean canTest() {
        // skip test on windows as it does not run well there
        return !isPlatform("windows");
    }

    public void testThreadPool() throws Exception {
        if (!canTest()) {
            return;
        }

        getMockEndpoint("mock:result").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello");
        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();

    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // create thread pool profile and register to camel
                ThreadPoolProfile profile = new ThreadPoolProfileBuilder("myPool").poolSize(2).maxPoolSize(5).maxQueueSize(10).build();
                context.getExecutorServiceManager().registerThreadPoolProfile(profile);

                from("direct:start")
                    .throttle(constant(2)).executorServiceRef("myPool")
                        .to("mock:result");
            }
        };
    }
}