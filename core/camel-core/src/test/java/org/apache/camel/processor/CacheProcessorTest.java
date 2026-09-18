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

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests for the Cache EIP processor.
 */
class CacheProcessorTest extends ContextTestSupport {

    @Test
    void testCacheHitSkipsBlock() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        MockEndpoint service = getMockEndpoint("mock:service");

        // First call — cache miss, service is called
        mock.expectedMessageCount(1);
        service.expectedMessageCount(1);
        template.sendBodyAndHeader("direct:cached", "request1", "productId", "A");
        MockEndpoint.assertIsSatisfied(context);
        assertThat(mock.getReceivedExchanges().get(0).getMessage().getBody(String.class))
                .isEqualTo("response-A");

        // Second call — cache hit, service is NOT called again
        mock.reset();
        service.reset();
        mock.expectedMessageCount(1);
        service.expectedMessageCount(0);
        template.sendBodyAndHeader("direct:cached", "request2", "productId", "A");
        MockEndpoint.assertIsSatisfied(context);
        assertThat(mock.getReceivedExchanges().get(0).getMessage().getBody(String.class))
                .isEqualTo("response-A");
    }

    @Test
    void testCacheMissExecutesBlock() throws Exception {
        MockEndpoint service = getMockEndpoint("mock:service");

        // Different keys — both are cache misses
        service.expectedMessageCount(2);
        template.sendBodyAndHeader("direct:cached", "req1", "productId", "A");
        template.sendBodyAndHeader("direct:cached", "req2", "productId", "B");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testCacheWithTtlExpiry() throws Exception {
        MockEndpoint service = getMockEndpoint("mock:ttl-service");

        // First call — cache miss
        service.expectedMessageCount(1);
        template.sendBodyAndHeader("direct:cached-ttl", "req1", "productId", "A");
        MockEndpoint.assertIsSatisfied(context);

        // Wait for TTL to expire (200ms TTL)
        await().atMost(2, TimeUnit.SECONDS).pollDelay(300, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    // After expiry — cache miss again, service called again
                    service.reset();
                    service.expectedMessageCount(1);
                    template.sendBodyAndHeader("direct:cached-ttl", "req2", "productId", "A");
                    MockEndpoint.assertIsSatisfied(context);
                });
    }

    @Test
    void testCacheNullKeyBypassesCache() throws Exception {
        MockEndpoint service = getMockEndpoint("mock:service");

        // Null key — always executes block (no caching)
        service.expectedMessageCount(2);
        template.sendBody("direct:cached", "req1"); // no productId header → null key
        template.sendBody("direct:cached", "req2"); // still no key → executes again
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testCacheDoesNotCacheFailedExchange() throws Exception {
        MockEndpoint service = getMockEndpoint("mock:failing-service");

        // First call — service throws exception, result NOT cached
        service.expectedMessageCount(1);
        try {
            template.sendBodyAndHeader("direct:cached-failing", "req1", "productId", "A");
        } catch (Exception e) {
            // expected
        }
        MockEndpoint.assertIsSatisfied(context);

        // Second call — cache miss (because failure was not cached), service called again
        service.reset();
        service.expectedMessageCount(1);
        try {
            template.sendBodyAndHeader("direct:cached-failing", "req2", "productId", "A");
        } catch (Exception e) {
            // expected
        }
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testCacheNullBodyNotCachedByDefault() throws Exception {
        MockEndpoint service = getMockEndpoint("mock:null-service");

        // First call — null body result, not cached (cacheNull=false by default)
        service.expectedMessageCount(1);
        template.sendBodyAndHeader("direct:cached-null", "req1", "productId", "A");
        MockEndpoint.assertIsSatisfied(context);

        // Second call — cache miss (null was not cached), service called again
        service.reset();
        service.expectedMessageCount(1);
        template.sendBodyAndHeader("direct:cached-null", "req2", "productId", "A");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testCacheNullBodyCachedWhenCacheNullTrue() throws Exception {
        MockEndpoint service = getMockEndpoint("mock:cache-null-service");

        // First call — null body result IS cached when cacheNull(true)
        service.expectedMessageCount(1);
        template.sendBodyAndHeader("direct:cached-null-true", "req1", "productId", "A");
        MockEndpoint.assertIsSatisfied(context);

        // Second call — cache hit (null WAS cached), service is NOT called again
        service.reset();
        service.expectedMessageCount(0);
        template.sendBodyAndHeader("direct:cached-null-true", "req2", "productId", "A");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testCacheExpressionClauseForm() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:clause-result");
        MockEndpoint service = getMockEndpoint("mock:clause-service");

        // First call — cache miss
        service.expectedMessageCount(1);
        mock.expectedMessageCount(1);
        template.sendBodyAndHeader("direct:cached-clause", "req1", "productId", "X");
        MockEndpoint.assertIsSatisfied(context);

        // Second call — cache hit
        service.reset();
        mock.reset();
        service.expectedMessageCount(0);
        mock.expectedMessageCount(1);
        template.sendBodyAndHeader("direct:cached-clause", "req2", "productId", "X");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Basic cache route
                from("direct:cached")
                        .cache(simple("${header.productId}"))
                            .to("mock:service")
                            .setBody(simple("response-${header.productId}"))
                        .end()
                        .to("mock:result");

                // Cache with short TTL
                from("direct:cached-ttl")
                        .cache(simple("${header.productId}"))
                            .ttl(200)
                            .to("mock:ttl-service")
                            .setBody(simple("response-${header.productId}"))
                        .end()
                        .to("mock:ttl-result");

                // Cache with a failing service
                from("direct:cached-failing")
                        .cache(simple("${header.productId}"))
                            .to("mock:failing-service")
                            .throwException(new IllegalStateException("service error"))
                        .end()
                        .to("mock:failing-result");

                // Cache with null body
                from("direct:cached-null")
                        .cache(simple("${header.productId}"))
                            .to("mock:null-service")
                            .setBody(constant(null))
                        .end()
                        .to("mock:null-result");

                // Cache with null body and cacheNull=true
                from("direct:cached-null-true")
                        .cache(simple("${header.productId}")).cacheNull(true)
                            .to("mock:cache-null-service")
                            .setBody(constant(null))
                        .end()
                        .to("mock:cache-null-result");

                // Expression clause form
                from("direct:cached-clause")
                        .cache().simple("${header.productId}")
                            .to("mock:clause-service")
                            .setBody(simple("clause-response-${header.productId}"))
                        .end()
                        .to("mock:clause-result");
            }
        };
    }
}
