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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class HystrixComponentRequestContextTest extends HystrixComponentBase {

    @Test
    public void invokesCachedEndpointWithCustomRequestContext() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        errorEndpoint.expectedMessageCount(0);

        HystrixRequestContext customContext = HystrixRequestContext.initializeContext();
        final Map headers = new HashMap<>();
        headers.put("key", "cachedKey");
        headers.put(HystrixConstants.CAMEL_HYSTRIX_REQUEST_CONTEXT, customContext);

        template.sendBodyAndHeaders("body", headers);

        final CountDownLatch latch = new CountDownLatch(1);
        new Thread(new Runnable() {
            @Override
            public void run() {
                template.sendBodyAndHeaders("body", headers);
                latch.countDown();
            }
        }).start();

        latch.await(2, TimeUnit.SECONDS);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void invokesCachedEndpointTwiceWhenCacheIsCleared() throws Exception {
        resultEndpoint.expectedMessageCount(2);
        errorEndpoint.expectedMessageCount(0);

        HystrixRequestContext customContext = HystrixRequestContext.initializeContext();
        final Map headers = new HashMap<>();
        headers.put("key", "cachedKey");
        headers.put(HystrixConstants.CAMEL_HYSTRIX_REQUEST_CONTEXT, customContext);

        template.sendBodyAndHeaders("body", headers);

        headers.put(HystrixConstants.CAMEL_HYSTRIX_CLEAR_CACHE_FIRST, true);

        template.sendBodyAndHeaders("body", headers);
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
                        .to("hystrix:testKey?runEndpoint=direct:run&fallbackEndpoint=direct:fallback&cacheKey=header.key");
            }
        };
    }
}

