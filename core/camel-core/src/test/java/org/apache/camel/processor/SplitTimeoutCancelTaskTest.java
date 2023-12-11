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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ThreadPoolProfile;
import org.junit.jupiter.api.Test;

public class SplitTimeoutCancelTaskTest extends ContextTestSupport {

    String payload1 = "<items><item><id>1</id><name>one</name></item><item><id>2</id><name>two</name></item></items>";
    String payload2 = "<items><item><id>3</id><name>three</name></item><item><id>4</id><name>four</name></item></items>";

    @Test
    public void testSplitterTimeoutShouldNotExhaustThreadPool() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:split");
        mockEndpoint.expectedMessageCount(4);

        template.sendBody("direct:start", payload1);
        template.sendBody("direct:start", payload2);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                ThreadPoolProfile myThreadPoolProfile = new ThreadPoolProfile("testProfile");
                myThreadPoolProfile.setMaxPoolSize(20);
                myThreadPoolProfile.setPoolSize(10);
                myThreadPoolProfile.setMaxQueueSize(1);

                getContext().getExecutorServiceManager().setDefaultThreadPoolProfile(myThreadPoolProfile);

                from("direct:start")
                        .split()
                        .xpath("//items/item")
                        .parallelProcessing(true)
                        .streaming(true)
                        .stopOnException(true)
                        .timeout("30000")
                        .executorService("testProfile")
                        .to("mock:split");
            }
        };
    }
}
