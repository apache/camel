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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Camel does not recover from a {@link VirtualMachineError} on its own: no clause matches it through its cause, an
 * explicit clause does not inherit the redelivery or the dead letter channel of the error handler, and a dead letter
 * route, the failover of a load balancer, the error thresholds of a split and an onExceptionOccurred processor do not
 * turn it into a success (CAMEL-27515).
 */
public class VirtualMachineErrorRecoveryTest extends ContextTestSupport {

    private final AtomicInteger calls = new AtomicInteger();

    @Test
    public void onExceptionDoesNotMatchThroughTheCauseOfTheError() throws Exception {
        getMockEndpoint("mock:handled").expectedMessageCount(0);

        Exchange out = template.send("direct:cause", e -> e.getMessage().setBody("Hello"));

        assertFatal(InternalError.class, out);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void explicitClauseDoesNotInheritRedeliveryNorTheDeadLetterChannel() throws Exception {
        getMockEndpoint("mock:dead").expectedMessageCount(0);

        Exchange out = template.send("direct:explicit", e -> e.getMessage().setBody("Hello"));

        assertFatal(StackOverflowError.class, out);
        assertEquals(1, calls.get(), "The exchange was redelivered");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void errorOfTheDeadLetterRouteFailsTheExchange() {
        Exchange out = template.send("direct:dead-letter-route", e -> e.getMessage().setBody("Hello"));

        assertFatal(StackOverflowError.class, out);
    }

    @Test
    public void errorOfTheDeadLetterRouteIsNotHandledByThePolicyOfTheOriginalFailure() {
        Exchange out = template.send("direct:dead-letter-route-handled", e -> e.getMessage().setBody("Hello"));

        assertFatal(StackOverflowError.class, out);
    }

    @Test
    public void failoverDoesNotTryTheNextEndpoint() throws Exception {
        getMockEndpoint("mock:second").expectedMessageCount(0);

        Exchange out = template.send("direct:failover", e -> e.getMessage().setBody("Hello"));

        assertFatal(StackOverflowError.class, out);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void splitThresholdsDoNotSwallowTheError() throws Exception {
        getMockEndpoint("mock:after-split").expectedMessageCount(0);

        Exchange out = template.send("direct:split", e -> e.getMessage().setBody(List.of("a", "b", "c")));

        assertFatal(StackOverflowError.class, out);
        assertEquals(List.of("a", "b", "c"), out.getMessage().getBody(), "The split did not keep the original message");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void onExceptionOccurredDoesNotHideTheError() throws Exception {
        getMockEndpoint("mock:dead").expectedMessageCount(0);

        Exchange out = template.send("direct:occurred", e -> e.getMessage().setBody("Hello"));

        assertFatal(StackOverflowError.class, out);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void errorOfOnPrepareFailureFailsTheExchange() throws Exception {
        getMockEndpoint("mock:dead-prepare").expectedMessageCount(0);

        Exchange out = template.send("direct:prepare-throws", e -> e.getMessage().setBody("Hello"));

        assertFatal(StackOverflowError.class, out);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void errorOfOnExceptionOccurredFailsTheExchange() throws Exception {
        getMockEndpoint("mock:dead-occurred").expectedMessageCount(0);

        Exchange out = template.send("direct:occurred-throws", e -> e.getMessage().setBody("Hello"));

        assertFatal(StackOverflowError.class, out);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void errorOfOnExceptionOccurredReplacesTheErrorOfTheRoute() {
        Exchange out = template.send("direct:occurred-throws-other", e -> e.getMessage().setBody("Hello"));

        assertFatal(InternalError.class, out);
    }

    @Test
    public void parallelMulticastFailsWithTheError() throws Exception {
        // the outputs already submitted still run
        getMockEndpoint("mock:parallel-second").expectedMessageCount(1);

        Exchange out = template.send("direct:multicast-parallel", e -> e.getMessage().setBody("Hello"));

        assertFatal(StackOverflowError.class, out);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void explicitHandledClauseWithoutOutputsHandlesTheError() {
        Exchange out = template.send("direct:handled-no-outputs", e -> e.getMessage().setBody("Hello"));

        assertNull(out.getException(), "The explicit clause did not handle the error");
    }

    @Test
    public void defaultSplitStopsOnTheError() throws Exception {
        getMockEndpoint("mock:split-item").expectedBodiesReceived("a");
        getMockEndpoint("mock:split-caught").expectedMessageCount(0);
        getMockEndpoint("mock:split-after").expectedMessageCount(0);

        Exchange out = template.send("direct:split-default", e -> e.getMessage().setBody(List.of("a", "b")));

        assertFatal(StackOverflowError.class, out);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void multicastStopsOnTheError() throws Exception {
        getMockEndpoint("mock:multicast-second").expectedMessageCount(0);

        Exchange out = template.send("direct:multicast", e -> e.getMessage().setBody("Hello"));

        assertFatal(StackOverflowError.class, out);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void parallelSplitKeepsTheError() throws Exception {
        // the other item fails later with an ordinary exception, which does not replace the error
        getMockEndpoint("mock:parallel-split-caught").expectedMessageCount(0);
        getMockEndpoint("mock:parallel-split-after").expectedMessageCount(0);

        Exchange out = template.send("direct:split-parallel", e -> e.getMessage().setBody(List.of("a", "b")));

        assertFatal(StackOverflowError.class, out);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void parallelSplitWithAggregationStrategyFailsWithTheError() {
        // the aggregation strategy ignores the failures of the items, as for any failure
        Exchange out = template.send("direct:split-parallel-strategy", e -> e.getMessage().setBody(List.of("a", "b")));

        assertFatal(StackOverflowError.class, out);
    }

    @Test
    public void parallelSplitWithFailingAggregationStrategyFailsWithTheError() {
        Exchange out = template.send("direct:split-parallel-strategy-fails", e -> e.getMessage().setBody(List.of("a")));

        assertFatal(StackOverflowError.class, out);
    }

    @Test
    public void onExceptionOccurredThatDropsTheErrorDoesNotHideIt() throws Exception {
        getMockEndpoint("mock:occurred-drops-caught").expectedMessageCount(0);

        Exchange out = template.send("direct:occurred-drops", e -> e.getMessage().setBody("Hello"));

        assertFatal(StackOverflowError.class, out);
        assertMockEndpointsSatisfied();
    }

    private static void assertFatal(Class<? extends VirtualMachineError> type, Exchange out) {
        assertInstanceOf(type, ObjectHelper.getVirtualMachineError(out.getException()), "The exchange did not fail with it");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:cause")
                        .onException(Exception.class).handled(true).to("mock:handled").end()
                        .process(e -> {
                            throw new InternalError(new IllegalStateException("the cause is an exception"));
                        });

                from("direct:explicit")
                        .errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(3).redeliveryDelay(0))
                        .onException(VirtualMachineError.class).handled(false).end()
                        .process(e -> {
                            calls.incrementAndGet();
                            throw new StackOverflowError("simulated");
                        });

                from("direct:dead-letter-route")
                        .errorHandler(deadLetterChannel("direct:dead"))
                        .process(e -> {
                            throw new IllegalStateException("an ordinary failure");
                        });
                from("direct:dead-letter-route-handled")
                        .errorHandler(deadLetterChannel("direct:dead"))
                        .onException(IllegalStateException.class).handled(true).end()
                        .process(e -> {
                            throw new IllegalStateException("an ordinary failure, handled");
                        });
                from("direct:dead").errorHandler(noErrorHandler())
                        .process(e -> {
                            throw new StackOverflowError("simulated in the dead letter route");
                        });

                from("direct:failover")
                        .loadBalance().failover().to("direct:first", "mock:second");
                from("direct:first").errorHandler(noErrorHandler())
                        .process(e -> {
                            throw new StackOverflowError("simulated");
                        });

                from("direct:split")
                        .split(body()).maxFailedRecords(2)
                        .process(e -> {
                            if ("b".equals(e.getMessage().getBody())) {
                                throw new StackOverflowError("simulated");
                            }
                        })
                        .end()
                        .to("mock:after-split");

                from("direct:prepare-throws")
                        .errorHandler(deadLetterChannel("mock:dead-prepare").onPrepareFailure(e -> {
                            throw new StackOverflowError("simulated in onPrepareFailure");
                        }))
                        .process(e -> {
                            throw new IllegalStateException("an ordinary failure");
                        });

                from("direct:occurred-throws")
                        .errorHandler(deadLetterChannel("mock:dead-occurred").onExceptionOccurred(e -> {
                            throw new StackOverflowError("simulated in onExceptionOccurred");
                        }))
                        .process(e -> {
                            throw new IllegalStateException("an ordinary failure");
                        });

                from("direct:occurred-throws-other")
                        .errorHandler(defaultErrorHandler().onExceptionOccurred(e -> {
                            throw new InternalError("simulated in onExceptionOccurred");
                        }))
                        .process(e -> {
                            throw new StackOverflowError("simulated in the route");
                        });

                from("direct:multicast-parallel")
                        .multicast().parallelProcessing().to("direct:first", "mock:parallel-second");

                from("direct:handled-no-outputs")
                        .onException(StackOverflowError.class).handled(true).end()
                        .process(e -> {
                            throw new StackOverflowError("simulated");
                        });

                from("direct:split-default")
                        .doTry()
                        .split(body())
                        .to("mock:split-item")
                        .process(e -> {
                            if ("a".equals(e.getMessage().getBody())) {
                                throw new StackOverflowError("simulated on a");
                            }
                            throw new IllegalArgumentException("an ordinary failure on b");
                        })
                        .end().endDoTry()
                        .doCatch(Exception.class)
                        .to("mock:split-caught")
                        .end()
                        .to("mock:split-after");

                from("direct:multicast")
                        .multicast().to("direct:first", "mock:multicast-second");

                from("direct:split-parallel")
                        .doTry()
                        .split(body()).parallelProcessing()
                        .process(e -> {
                            if ("a".equals(e.getMessage().getBody())) {
                                throw new StackOverflowError("simulated on a");
                            }
                            throw new IllegalArgumentException("an ordinary failure on b");
                        })
                        .end().endDoTry()
                        .doCatch(Exception.class)
                        .to("mock:parallel-split-caught")
                        .end()
                        .to("mock:parallel-split-after");

                from("direct:split-parallel-strategy")
                        .split(body(), AggregationStrategies.groupedBody()).parallelProcessing()
                        .process(e -> {
                            if ("b".equals(e.getMessage().getBody())) {
                                throw new StackOverflowError("simulated on b");
                            }
                        })
                        .end();

                from("direct:split-parallel-strategy-fails")
                        .split(body(), (oldExchange, newExchange) -> {
                            throw new IllegalStateException("an ordinary failure of the aggregation strategy");
                        }).parallelProcessing()
                        .process(e -> {
                            throw new StackOverflowError("simulated");
                        })
                        .end();

                from("direct:occurred-drops")
                        .doTry()
                        .to("direct:occurred-drops-inner")
                        .doCatch(Exception.class)
                        .to("mock:occurred-drops-caught")
                        .end();
                from("direct:occurred-drops-inner")
                        .errorHandler(defaultErrorHandler().onExceptionOccurred(e -> {
                            // drops the error, and fails with an ordinary exception instead
                            e.removeProperty(Exchange.EXCEPTION_CAUGHT);
                            e.setException(new IllegalArgumentException("replaced"));
                        }))
                        .process(e -> {
                            throw new StackOverflowError("simulated");
                        });

                from("direct:occurred")
                        .errorHandler(deadLetterChannel("mock:dead")
                                .onExceptionOccurred(e -> e.setException(new IllegalStateException("replaced"))))
                        .process(e -> {
                            throw new StackOverflowError("simulated");
                        });
            }
        };
    }
}
