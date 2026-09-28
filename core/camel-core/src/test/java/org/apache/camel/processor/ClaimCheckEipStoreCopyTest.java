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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ClaimCheckOperation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The copy of the exchange that Set and Push store does not carry the claim check repository, and making it must not
 * copy the repository (which would copy all its claim checks on every Set or Push).
 */
class ClaimCheckEipStoreCopyTest extends ContextTestSupport {

    private final CountingClaimCheckRepository repository = new CountingClaimCheckRepository();
    private final AtomicInteger safeCopiesBeforeMock = new AtomicInteger(-1);
    private final AtomicReference<Object> repositoryAtEnd = new AtomicReference<>();

    @Test
    void testSetAndPushDoNotCopyTheRepository() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
        assertEquals(0, safeCopiesBeforeMock.get(), "Set and Push should not copy the claim check repository");
        assertSame(repository, repositoryAtEnd.get(), "The exchange should keep its claim check repository");
        assertTrue(repository.contains("a") && repository.contains("b"), "The repository should keep the claim checks");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .process(e -> e.setProperty(ExchangePropertyKey.CLAIM_CHECK_REPOSITORY, repository))
                        .claimCheck(ClaimCheckOperation.Set, "a")
                        .claimCheck(ClaimCheckOperation.Set, "b")
                        .claimCheck(ClaimCheckOperation.Push)
                        .claimCheck(ClaimCheckOperation.Pop)
                        .process(e -> {
                            // capture before the mock endpoint, which copies the exchange
                            safeCopiesBeforeMock.set(repository.safeCopies.get());
                            repositoryAtEnd.set(e.getProperty(ExchangePropertyKey.CLAIM_CHECK_REPOSITORY));
                        })
                        .to("mock:result");
            }
        };
    }

    private static final class CountingClaimCheckRepository extends DefaultClaimCheckRepository {

        private final AtomicInteger safeCopies = new AtomicInteger();

        @Override
        public DefaultClaimCheckRepository safeCopy() {
            safeCopies.incrementAndGet();
            return super.safeCopy();
        }
    }
}
