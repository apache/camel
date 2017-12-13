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
package org.apache.camel.processor.idempotent;

import java.util.Map;
import java.util.function.Function;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.IdempotentRepository;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public abstract class AbstractIdempotentRepositoryTest<I extends IdempotentRepository<String>> extends ContextTestSupport {

    protected final I repo;

    protected AbstractIdempotentRepositoryTest(I repo) {
        this.repo = repo;
    }

    // Disable auto-starting test routebuilder so we can a route per test
    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    /**
     * Sends messages to the given endpoint for each of the specified bodies
     *
     * @param endpointUri the endpoint URI to send to
     * @param headers     A function to generate additional headers for a given body
     * @param bodies      the bodies to send, one per message
     */
    @SafeVarargs
    protected final <T> void sendBodies(String endpointUri, Function<T, Map<String, Object>> headers, T... bodies) {
        for (T body : bodies) {
            sendBody(endpointUri, body, headers.apply(body));
        }
    }

    @Test
    public void testDuplicateMessagesAreFiltered() throws Exception {
        // Given:
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").idempotentConsumer(body(), repo).to("mock:result");
            }
        });
        startCamelContext();
        getMockEndpoint("mock:result").expectedBodiesReceived("a", "b", "c");
        // When:
        sendBodies("direct:start", "a", "b", "a", "b", "c");
        // Then:
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUnidentifiedMessagesAreFailed() throws Exception {
        // Given:
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dlq"));
                from("direct:start").idempotentConsumer(body(), repo).to("mock:result");
            }
        });
        startCamelContext();
        getMockEndpoint("mock:result").expectedBodiesReceived("a", "b", "c");
        getMockEndpoint("mock:dlq").expectedBodiesReceived((String) null); // null idempotent key must fail
        // When:
        sendBodies("direct:start", "a", null, "b", "a", "b", "c");
        // Then:
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testFailedMessagesAreNotRemoved() throws Exception {
        // Given:
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dlq"));
                from("direct:start").idempotentConsumer(body(), repo)
                        .removeOnFailure(false)
                        .process().body(AbstractIdempotentRepositoryTest::hateMondays)
                        .to("mock:result");
            }
        });
        startCamelContext();
        getMockEndpoint("mock:result").expectedBodiesReceived("tue", "wed");
        getMockEndpoint("mock:dlq").expectedBodiesReceived("mon"); // "mon" is not redelivered so cannot fail twice
        // When:
        sendBodies("direct:start", "mon", "tue", "tue", "wed", "mon");
        // Then:
        assertMockEndpointsSatisfied();
        assertThat(repo.contains("mon"), is(true));
    }

    @Test
    public void testFailedMessagesAreRemoved() throws Exception {
        // Given:
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dlq"));
                from("direct:start").idempotentConsumer(body(), repo)
                        .removeOnFailure(true) // Default
                        .process().body(AbstractIdempotentRepositoryTest::hateMondays)
                        .to("mock:result");
            }
        });
        startCamelContext();
        getMockEndpoint("mock:result").expectedBodiesReceived("tue", "wed");
        getMockEndpoint("mock:dlq").expectedBodiesReceived("mon", "mon"); // "mon" is redelivered so fails twice
        // When:
        sendBodies("direct:start", "mon", "tue", "tue", "wed", "mon");
        // Then:
        assertMockEndpointsSatisfied();
        assertThat(repo.contains("mon"), is(false));
    }

    @Test
    public void testNonEagerCompletionIncludesSubsequentRouting() throws Exception {
        // Given:
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dlq"));
                from("direct:start").idempotentConsumer(body(), repo)
                        .completionEager(false) // Default
                        .to("mock:idem")
                        .end() // idempotent block ends here
                        .process().body(AbstractIdempotentRepositoryTest::hateMondays)
                        .to("mock:result");
            }
        });
        startCamelContext();
        getMockEndpoint("mock:result").expectedBodiesReceived("tue", "tue", "wed");
        getMockEndpoint("mock:dlq").expectedBodiesReceived("mon", "mon");
        // non-eager: "mon" fails AFTER the idem block, is removed, and therefore WILL be delivered again
        getMockEndpoint("mock:idem").expectedBodiesReceived("mon", "tue", "wed", "mon");
        // When:
        sendBodies("direct:start", "mon", "tue", "tue", "wed", "mon");
        // Then:
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testEagerCompletionIgnoresSubsequentRouting() throws Exception {
        // Given:
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dlq"));
                from("direct:start").idempotentConsumer(body(), repo)
                        .completionEager(true)
                        .to("mock:idem")
                        .end() // idempotent block ends here
                        .process().body(AbstractIdempotentRepositoryTest::hateMondays)
                        .to("mock:result");
            }
        });
        startCamelContext();
        getMockEndpoint("mock:result").expectedBodiesReceived("tue", "tue", "wed");
        getMockEndpoint("mock:dlq").expectedBodiesReceived("mon", "mon");
        // eager: "mon" fails AFTER it was confirmed by the idem block and therefore WON'T be delivered again
        getMockEndpoint("mock:idem").expectedBodiesReceived("mon", "tue", "wed");

        // When:
        sendBodies("direct:start", "mon", "tue", "tue", "wed", "mon");
        // Then:
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRepoCanBeCleared() throws Exception {
        // Given:
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").idempotentConsumer(body(), repo).to("mock:result");
            }
        });
        startCamelContext();
        getMockEndpoint("mock:result").expectedBodiesReceived("a", "b", "c");
        sendBodies("direct:start", "a", "b", "a", "b", "c");
        assertMockEndpointsSatisfied();
        resetMocks();
        // When:
        repo.clear();
        sendBodies("direct:start", "a", "b", "a", "b", "c");
        // Then:
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRepoCanRemoveAnEntry() throws Exception {
        // Given:
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").idempotentConsumer(body(), repo).to("mock:result");
            }
        });
        startCamelContext();
        getMockEndpoint("mock:result").expectedBodiesReceived("a", "b", "c");
        sendBodies("direct:start", "a", "b", "a", "b", "c");
        assertMockEndpointsSatisfied();
        resetMocks();
        // When:
        repo.remove("b");
        getMockEndpoint("mock:result").expectedBodiesReceived("b");
        sendBodies("direct:start", "a", "b", "a", "b", "c");
        // Then:
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRepoExceptionFailsTheExchange() throws Exception {
        // Given:
        IdempotentRepository<String> fragileRepo = spy(repo);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dlq"));
                from("direct:start").idempotentConsumer(body(), fragileRepo).to("mock:result");
            }
        });
        startCamelContext();
        when(fragileRepo.add("mon")).thenThrow(new RuntimeException());
        fragileRepo.remove("mon"); // Dubious but effective use of Mockito spy
        getMockEndpoint("mock:dlq").expectedBodiesReceived("mon");
        getMockEndpoint("mock:result").expectedBodiesReceived("thu", "wed", "tue");
        // When:
        sendBodies("direct:start", "thu", "wed", "tue", "mon", "thu");
        // Then:
        assertMockEndpointsSatisfied();
        assertThat(repo.contains("mon"), is(false));
    }

    @Test
    public void testNotSkippingDuplicates() throws Exception {
        // Given:
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").idempotentConsumer(body(), repo)
                        .skipDuplicate(false)
                        .filter(exchangeProperty(Exchange.DUPLICATE_MESSAGE))
                        .to("mock:dups")
                        .stop().end()
                        .to("mock:result");
            }
        });
        startCamelContext();
        getMockEndpoint("mock:result").expectedBodiesReceived("a", "b", "c");
        getMockEndpoint("mock:dups").expectedBodiesReceived("a", "b");
        // When:
        sendBodies("direct:start", "a", "b", "a", "b", "c");
        // Then:
        assertMockEndpointsSatisfied();
    }

    @Test // TODO This isn't a very good test of the _impact_ of eagerness
    public void testEagerlyAddedToRepo() throws Exception {
        // Given:
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").idempotentConsumer(body(), repo)
                        .eager(true) // default
                        .process().body(b -> assertThat(repo.contains((String) b), is(true)))
                        .to("mock:result");
            }
        });
        startCamelContext();
        getMockEndpoint("mock:result").expectedBodiesReceived("a", "b", "c");
        // When:
        sendBodies("direct:start", "a", "b", "a", "b", "c");
        // Then:
        assertMockEndpointsSatisfied();
    }

    @Test // TODO This isn't a very good test of the _impact_ of eagerness
    public void testNotEagerlyAddedAddedToRepo() throws Exception {
        // Given:
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").idempotentConsumer(body(), repo)
                        .eager(false)
                        .process().body(b -> assertThat(repo.contains((String) b), is(false)))
                        .to("mock:result");
            }
        });
        startCamelContext();
        getMockEndpoint("mock:result").expectedBodiesReceived("a", "b", "c");
        // When:
        sendBodies("direct:start", "a", "b", "a", "b", "c");
        // Then:
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testFailedMessagesAreRemovedNonEager() throws Exception {
        // Given:
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dlq"));
                from("direct:start").idempotentConsumer(body(), repo)
                        .removeOnFailure(true) // Default
                        .eager(false)
                        .process().body(AbstractIdempotentRepositoryTest::hateMondays)
                        .to("mock:result");
            }
        });
        startCamelContext();
        getMockEndpoint("mock:result").expectedBodiesReceived("tue", "wed");
        getMockEndpoint("mock:dlq").expectedBodiesReceived("mon", "mon"); // "mon" is redelivered so fails twice
        // When:
        sendBodies("direct:start", "mon", "tue", "tue", "wed", "mon");
        // Then:
        assertMockEndpointsSatisfied();
        assertThat(repo.contains("mon"), is(false));
    }

    @Test
    public void testThreads() throws Exception {
        // Given:
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").idempotentConsumer(body(), repo)
                        .threads()
                        .to("mock:result");
            }
        });
        startCamelContext();
        getMockEndpoint("mock:result").expectedBodiesReceived("a", "b", "c");
        // When:
        sendBodies("direct:start", "a", "b", "a", "b", "c");
        // Then:
        assertMockEndpointsSatisfied();
    }

    private static Object hateMondays(Object body) {
        if ("mon".equals(body.toString())) {
            throw new RuntimeException();
        }
        return body;
    }

}