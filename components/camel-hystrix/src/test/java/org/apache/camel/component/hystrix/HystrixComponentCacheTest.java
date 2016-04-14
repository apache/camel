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
package org.apache.camel.component.hystrix;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class HystrixComponentCacheTest extends HystrixComponentBase {

    @Test
    public void invokesCachedEndpoint() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        errorEndpoint.expectedMessageCount(0);

        template.sendBodyAndHeader("body", "key", "cachedKey");
        template.sendBodyAndHeader("body", "key", "cachedKey");

        assertMockEndpointsSatisfied();

        resultEndpoint.expectedMessageCount(2);
        template.sendBodyAndHeader("body", "key", "cachedKey");
        template.sendBodyAndHeader("body", "key", "differentCachedKey");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void invokesCachedEndpointFromDifferentThread() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        errorEndpoint.expectedMessageCount(0);

        template.sendBodyAndHeader("body", "key", "cachedKey");

        final CountDownLatch latch = new CountDownLatch(1);
        new Thread(new Runnable() {
            @Override
            public void run() {
                template.sendBodyAndHeader("body", "key", "cachedKey");
                latch.countDown();
            }
        }).start();

        latch.await(2, TimeUnit.SECONDS);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            public void configure() {

                from("direct:fallback")
                        .to("mock:error");

                from("direct:run")
                        .to("mock:result");

                from("direct:start")
                        .to("hystrix:testKey?runEndpoint=direct:run&fallbackEndpoint=direct:fallback&cacheKey=header.key&initializeRequestContext=true");
            }
        };
    }
}

