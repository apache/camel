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
package org.apache.camel.builder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EventObject;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Producer;
import org.apache.camel.component.direct.DirectEndpoint;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.management.EventNotifierSupport;
import org.apache.camel.management.event.ExchangeCompletedEvent;
import org.apache.camel.management.event.ExchangeCreatedEvent;
import org.apache.camel.management.event.ExchangeFailedEvent;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;

/**
 * A builder to build an expression based on {@link org.apache.camel.spi.EventNotifier} notifications
 * about {@link Exchange} being routed.
 * <p/>
 * This builder can be used for testing purposes where you want to know when a test is supposed to be done.
 * The idea is that you can build an expression that explains when the test is done. For example when Camel
 * have finished routing 5 messages. You can then in your test await for this condition to occur.
 *
 * @version 
 */
public class NotifyBuilder {

    private final CamelContext context;

    // notifier to hook into Camel to listen for events
    private final EventNotifier eventNotifier;

    // the predicates build with this builder
    private final List<EventPredicateHolder> predicates = new ArrayList<EventPredicateHolder>();

    // latch to be used to signal predicates matches
    private CountDownLatch latch = new CountDownLatch(1);

    // the current state while building an event predicate where we use a stack and the operation
    private final Stack<EventPredicate> stack = new Stack<EventPredicate>();
    private EventOperation operation;
    private boolean created;

    // computed value whether all the predicates matched
    private boolean matches;

    /**
     * Creates a new builder.
     *
     * @param context the Camel context
     */
    public NotifyBuilder(CamelContext context) {
        this.context = context;
        eventNotifier = new ExchangeNotifier();
        try {
            ServiceHelper.startService(eventNotifier);
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
        context.getManagementStrategy().addEventNotifier(eventNotifier);
        // we only want to match from routes, so skip for example events
        // which is triggered by producer templates etc.
        this.fromRoutesOnly();
    }

    /**
     * Optionally a <tt>from</tt> endpoint which means that this expression should only be based
     * on {@link Exchange} which is originated from the particular endpoint(s).
     *
     * @param endpointUri uri of endpoint or pattern (see the EndpointHelper javadoc)
     * @return the builder
     * @see org.apache.camel.util.EndpointHelper#matchEndpoint(String, String)
     */
    public NotifyBuilder from(final String endpointUri) {
        stack.push(new EventPredicateSupport() {

            @Override
            public boolean onExchange(Exchange exchange) {
                // filter non matching exchanges
                return EndpointHelper.matchEndpoint(exchange.getFromEndpoint().getEndpointUri(), endpointUri);
            }

            public boolean matches() {
                return true;
            }

            @Override
            public String toString() {
                return "from(" + endpointUri + ")";
            }
        });
        return this;
    }

    /**
     * Optionally a <tt>from</tt> route which means that this expression should only be based
     * on {@link Exchange} which is originated from the particular route(s).
     *
     * @param routeId id of route or pattern (see the EndpointHelper javadoc)
     * @return the builder
     * @see org.apache.camel.util.EndpointHelper#matchEndpoint(String, String)
     */
    public NotifyBuilder fromRoute(final String routeId) {
        stack.push(new EventPredicateSupport() {

            @Override
            public boolean onExchange(Exchange exchange) {
                String id = EndpointHelper.getRouteIdFromEndpoint(exchange.getFromEndpoint());
                // filter non matching exchanges
                return EndpointHelper.matchPattern(id, routeId);
            }

            public boolean matches() {
                return true;
            }

            @Override
            public String toString() {
                return "fromRoute(" + routeId + ")";
            }
        });
        return this;
    }

    private NotifyBuilder fromRoutesOnly() {
        stack.push(new EventPredicateSupport() {

            @Override
            public boolean onExchange(Exchange exchange) {
                // always accept direct endpoints as they are a special case as it will create the UoW beforehand
                // and just continue to route that on the consumer side, which causes the EventNotifer not to
                // emit events when the consumer received the exchange, as its alreay done. For example by
                // ProducerTemplate which creates the UoW before producing messages.
                if (exchange.getFromEndpoint() != null && exchange.getFromEndpoint() instanceof DirectEndpoint) {
                    return true;
                }
                return EndpointHelper.matchPattern(exchange.getFromRouteId(), "*");
            }

            public boolean matches() {
                return true;
            }

            @Override
            public String toString() {
                // we dont want any to string output as this is an internal predicate to match only from routes
                return "";
            }
        });
        return this;
    }

    /**
     * Optionally a filter to only allow matching {@link Exchange} to be used for matching.
     *
     * @param predicate the predicate to use for the filter
     * @return the builder
     */
    public NotifyBuilder filter(final Predicate predicate) {
        stack.push(new EventPredicateSupport() {

            @Override
            public boolean onExchange(Exchange exchange) {
                // filter non matching exchanges
                return predicate.matches(exchange);
            }

            public boolean matches() {
                return true;
            }

            @Override
            public String toString() {
                return "filter(" + predicate + ")";
            }
        });
        return this;
    }

    /**
     * Optionally a filter to only allow matching {@link Exchange} to be used for matching.
     *
     * @return the builder
     */
    public ExpressionClauseSupport<NotifyBuilder> filter() {
        final ExpressionClauseSupport<NotifyBuilder> clause = new ExpressionClauseSupport<NotifyBuilder>(this);
        stack.push(new EventPredicateSupport() {

            @Override
            public boolean onExchange(Exchange exchange) {
                // filter non matching exchanges
                Expression exp = clause.createExpression(exchange.getContext());
                return exp.evaluate(exchange, Boolean.class);
            }

            public boolean matches() {
                return true;
            }

            @Override
            public String toString() {
                return "filter(" + clause + ")";
            }
        });
        return clause;
    }

    /**
     * Sets a condition when <tt>number</tt> of {@link Exchange} has been received.
     * <p/>
     * The number matching is <i>at least</i> based which means that if more messages received
     * it will match also.
     *
     * @param number at least number of messages
     * @return the builder
     */
    public NotifyBuilder whenReceived(final int number) {
        stack.push(new EventPredicateSupport() {
            private int current;

            @Override
            public boolean onExchangeCreated(Exchange exchange) {
                current++;
                return true;
            }

            public boolean matches() {
                return current >= number;
            }

            @Override
            public void reset() {
                current = 0;
            }

            @Override
            public String toString() {
                return "whenReceived(" + number + ")";
            }
        });
        return this;
    }

    /**
     * Sets a condition when <tt>number</tt> of {@link Exchange} is done being processed.
     * <p/>
     * The number matching is <i>at least</i> based which means that if more messages received
     * it will match also.
     * <p/>
     * The difference between <i>done</i> and <i>completed</i> is that done can also include failed
     * messages, where as completed is only successful processed messages.
     *
     * @param number at least number of messages
     * @return the builder
     */
    public NotifyBuilder whenDone(final int number) {
        stack.add(new EventPredicateSupport() {
            private int current;

            @Override
            public boolean onExchangeCompleted(Exchange exchange) {
                current++;
                return true;
            }

            @Override
            public boolean onExchangeFailed(Exchange exchange) {
                current++;
                return true;
            }

            public boolean matches() {
                return current >= number;
            }

            @Override
            public void reset() {
                current = 0;
            }

            @Override
            public String toString() {
                return "whenDone(" + number + ")";
            }
        });
        return this;
    }

    /**
     * Sets a condition when <tt>number</tt> of {@link Exchange} has been completed.
     * <p/>
     * The number matching is <i>at least</i> based which means that if more messages received
     * it will match also.
     * <p/>
     * The difference between <i>done</i> and <i>completed</i> is that done can also include failed
     * messages, where as completed is only successful processed messages.
     *
     * @param number at least number of messages
     * @return the builder
     */
    public NotifyBuilder whenCompleted(final int number) {
        stack.add(new EventPredicateSupport() {
            private int current;

            @Override
            public boolean onExchangeCompleted(Exchange exchange) {
                current++;
                return true;
            }

            public boolean matches() {
                return current >= number;
            }

            @Override
            public void reset() {
                current = 0;
            }

            @Override
            public String toString() {
                return "whenCompleted(" + number + ")";
            }
        });
        return this;
    }

    /**
     * Sets a condition when <tt>number</tt> of {@link Exchange} has failed.
     * <p/>
     * The number matching is <i>at least</i> based which means that if more messages received
     * it will match also.
     *
     * @param number at least number of messages
     * @return the builder
     */
    public NotifyBuilder whenFailed(final int number) {
        stack.add(new EventPredicateSupport() {
            private int current;

            @Override
            public boolean onExchangeFailed(Exchange exchange) {
                current++;
                return true;
            }

            public boolean matches() {
                return current >= number;
            }

            @Override
            public void reset() {
                current = 0;
            }

            @Override
            public String toString() {
                return "whenFailed(" + number + ")";
            }
        });
        return this;
    }

    /**
     * Sets a condition when <tt>number</tt> of {@link Exchange} is done being processed.
     * <p/>
     * messages, where as completed is only successful processed messages.
     *
     * @param number exactly number of messages
     * @return the builder
     */
    public NotifyBuilder whenExactlyDone(final int number) {
        stack.add(new EventPredicateSupport() {
            private int current;

            @Override
            public boolean onExchangeCompleted(Exchange exchange) {
                current++;
                return true;
            }

            @Override
            public boolean onExchangeFailed(Exchange exchange) {
                current++;
                return true;
            }

            public boolean matches() {
                return current == number;
            }

            @Override
            public void reset() {
                current = 0;
            }

            @Override
            public String toString() {
                return "whenExactlyDone(" + number + ")";
            }
        });
        return this;
    }

    /**
     * Sets a condition when <tt>number</tt> of {@link Exchange} has been completed.
     * <p/>
     * The difference between <i>done</i> and <i>completed</i> is that done can also include failed
     * messages, where as completed is only successful processed messages.
     *
     * @param number exactly number of messages
     * @return the builder
     */
    public NotifyBuilder whenExactlyCompleted(final int number) {
        stack.add(new EventPredicateSupport() {
            private int current;

            @Override
            public boolean onExchangeCompleted(Exchange exchange) {
                current++;
                return true;
            }

            public boolean matches() {
                return current == number;
            }

            @Override
            public void reset() {
                current = 0;
            }

            @Override
            public String toString() {
                return "whenExactlyCompleted(" + number + ")";
            }
        });
        return this;
    }

    /**
     * Sets a condition when <tt>number</tt> of {@link Exchange} has failed.
     *
     * @param number exactly number of messages
     * @return the builder
     */
    public NotifyBuilder whenExactlyFailed(final int number) {
        stack.add(new EventPredicateSupport() {
            private int current;

            @Override
            public boolean onExchangeFailed(Exchange exchange) {
                current++;
                return true;
            }

            public boolean matches() {
                return current == number;
            }

            @Override
            public void reset() {
                current = 0;
            }

            @Override
            public String toString() {
                return "whenExactlyFailed(" + number + ")";
            }
        });
        return this;
    }

    /**
     * Sets a condition that <b>any received</b> {@link Exchange} should match the {@link Predicate}
     *
     * @param predicate the predicate
     * @return the builder
     */
    public NotifyBuilder whenAnyReceivedMatches(final Predicate predicate) {
        return doWhenAnyMatches(predicate, true);
    }

    /**
     * Sets a condition that <b>any done</b> {@link Exchange} should match the {@link Predicate}
     *
     * @param predicate the predicate
     * @return the builder
     */
    public NotifyBuilder whenAnyDoneMatches(final Predicate predicate) {
        return doWhenAnyMatches(predicate, false);
    }

    private NotifyBuilder doWhenAnyMatches(final Predicate predicate, final boolean received) {
        stack.push(new EventPredicateSupport() {
            private boolean matches;

            @Override
            public boolean onExchangeCompleted(Exchange exchange) {
                if (!received && !matches) {
                    matches = predicate.matches(exchange);
                }
                return true;
            }

            @Override
            public boolean onExchangeFailed(Exchange exchange) {
                if (!received && !matches) {
                    matches = predicate.matches(exchange);
                }
                return true;
            }

            @Override
            public boolean onExchangeCreated(Exchange exchange) {
                if (received && !matches) {
                    matches = predicate.matches(exchange);
                }
                return true;
            }

            public boolean matches() {
                return matches;
            }

            @Override
            public void reset() {
                matches = false;
            }

            @Override
            public String toString() {
                if (received) {
                    return "whenAnyReceivedMatches(" + predicate + ")";
                } else {
                    return "whenAnyDoneMatches(" + predicate + ")";
                }
            }
        });
        return this;
    }

    /**
     * Sets a condition that <b>all received</b> {@link Exchange} should match the {@link Predicate}
     *
     * @param predicate the predicate
     * @return the builder
     */
    public NotifyBuilder whenAllReceivedMatches(final Predicate predicate) {
        return doWhenAllMatches(predicate, true);
    }

    /**
     * Sets a condition that <b>all done</b> {@link Exchange} should match the {@link Predicate}
     *
     * @param predicate the predicate
     * @return the builder
     */
    public NotifyBuilder whenAllDoneMatches(final Predicate predicate) {
        return doWhenAllMatches(predicate, false);
    }

    private NotifyBuilder doWhenAllMatches(final Predicate predicate, final boolean received) {
        stack.push(new EventPredicateSupport() {
            private boolean matches = true;

            @Override
            public boolean onExchangeCompleted(Exchange exchange) {
                if (!received && matches) {
                    matches = predicate.matches(exchange);
                }
                return true;
            }

            @Override
            public boolean onExchangeFailed(Exchange exchange) {
                if (!received && matches) {
                    matches = predicate.matches(exchange);
                }
                return true;
            }

            @Override
            public boolean onExchangeCreated(Exchange exchange) {
                if (received && matches) {
                    matches = predicate.matches(exchange);
                }
                return true;
            }

            public boolean matches() {
                return matches;
            }

            @Override
            public void reset() {
                matches = true;
            }

            @Override
            public String toString() {
                if (received) {
                    return "whenAllReceivedMatches(" + predicate + ")";
                } else {
                    return "whenAllDoneMatches(" + predicate + ")";
                }
            }
        });
        return this;
    }

    /**
     * Sets a condition when the provided mock is satisfied based on {@link Exchange}
     * being sent to it when they are <b>done</b>.
     * <p/>
     * The idea is that you can use Mock for setting fine grained expectations
     * and then use that together with this builder. The mock provided does <b>NOT</b>
     * have to already exist in the route. You can just create a new pseudo mock
     * and this builder will send the done {@link Exchange} to it. So its like
     * adding the mock to the end of your route(s).
     *
     * @param mock the mock
     * @return the builder
     */
    public NotifyBuilder whenDoneSatisfied(final MockEndpoint mock) {
        return doWhenSatisfied(mock, false);
    }

    /**
     * Sets a condition when the provided mock is satisfied based on {@link Exchange}
     * being sent to it when they are <b>received</b>.
     * <p/>
     * The idea is that you can use Mock for setting fine grained expectations
     * and then use that together with this builder. The mock provided does <b>NOT</b>
     * have to already exist in the route. You can just create a new pseudo mock
     * and this builder will send the done {@link Exchange} to it. So its like
     * adding the mock to the end of your route(s).
     *
     * @param mock the mock
     * @return the builder
     */
    public NotifyBuilder whenReceivedSatisfied(final MockEndpoint mock) {
        return doWhenSatisfied(mock, true);
    }

    private NotifyBuilder doWhenSatisfied(final MockEndpoint mock, final boolean received) {
        stack.push(new EventPredicateSupport() {
            private Producer producer;

            @Override
            public boolean onExchangeCreated(Exchange exchange) {
                if (received) {
                    sendToMock(exchange);
                }
                return true;
            }

            @Override
            public boolean onExchangeFailed(Exchange exchange) {
                if (!received) {
                    sendToMock(exchange);
                }
                return true;
            }

            @Override
            public boolean onExchangeCompleted(Exchange exchange) {
                if (!received) {
                    sendToMock(exchange);
                }
                return true;
            }

            private void sendToMock(Exchange exchange) {
                // send the exchange when its completed to the mock
                try {
                    if (producer == null) {
                        producer = mock.createProducer();
                    }
                    producer.process(exchange);
                } catch (Exception e) {
                    throw ObjectHelper.wrapRuntimeCamelException(e);
                }
            }

            public boolean matches() {
                try {
                    return mock.await(0, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    throw ObjectHelper.wrapRuntimeCamelException(e);
                }
            }

            @Override
            public void reset() {
                mock.reset();
            }

            @Override
            public String toString() {
                if (received) {
                    return "whenReceivedSatisfied(" + mock + ")";
                } else {
                    return "whenDoneSatisfied(" + mock + ")";
                }
            }
        });
        return this;
    }

    /**
     * Sets a condition when the provided mock is <b>not</b> satisfied based on {@link Exchange}
     * being sent to it when they are <b>received</b>.
     * <p/>
     * The idea is that you can use Mock for setting fine grained expectations
     * and then use that together with this builder. The mock provided does <b>NOT</b>
     * have to already exist in the route. You can just create a new pseudo mock
     * and this builder will send the done {@link Exchange} to it. So its like
     * adding the mock to the end of your route(s).
     *
     * @param mock the mock
     * @return the builder
     */
    public NotifyBuilder whenReceivedNotSatisfied(final MockEndpoint mock) {
        return doWhenNotSatisfied(mock, true);
    }

    /**
     * Sets a condition when the provided mock is <b>not</b> satisfied based on {@link Exchange}
     * being sent to it when they are <b>done</b>.
     * <p/>
     * The idea is that you can use Mock for setting fine grained expectations
     * and then use that together with this builder. The mock provided does <b>NOT</b>
     * have to already exist in the route. You can just create a new pseudo mock
     * and this builder will send the done {@link Exchange} to it. So its like
     * adding the mock to the end of your route(s).
     *
     * @param mock the mock
     * @return the builder
     */
    public NotifyBuilder whenDoneNotSatisfied(final MockEndpoint mock) {
        return doWhenNotSatisfied(mock, false);
    }

    private NotifyBuilder doWhenNotSatisfied(final MockEndpoint mock, final boolean received) {
        stack.push(new EventPredicateSupport() {

            private Producer producer;

            @Override
            public boolean onExchangeCreated(Exchange exchange) {
                if (received) {
                    sendToMock(exchange);
                }
                return true;
            }

            @Override
            public boolean onExchangeFailed(Exchange exchange) {
                if (!received) {
                    sendToMock(exchange);
                }
                return true;
            }

            @Override
            public boolean onExchangeCompleted(Exchange exchange) {
                if (!received) {
                    sendToMock(exchange);
                }
                return true;
            }

            private void sendToMock(Exchange exchange) {
                // send the exchange when its completed to the mock
                try {
                    if (producer == null) {
                        producer = mock.createProducer();
                    }
                    producer.process(exchange);
                } catch (Exception e) {
                    throw ObjectHelper.wrapRuntimeCamelException(e);
                }
            }

            public boolean matches() {
                try {
                    return !mock.await(0, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    throw ObjectHelper.wrapRuntimeCamelException(e);
                }
            }

            @Override
            public void reset() {
                mock.reset();
            }

            @Override
            public String toString() {
                if (received) {
                    return "whenReceivedNotSatisfied(" + mock + ")";
                } else {
                    return "whenDoneNotSatisfied(" + mock + ")";
                }
            }
        });
        return this;
    }

    /**
     * Sets a condition that the bodies is expected to be <b>received</b> in the order as well.
     * <p/>
     * This condition will discard any additional messages. If you need a more strict condition
     * then use {@link #whenExactBodiesReceived(Object...)}
     *
     * @param bodies the expected bodies
     * @return the builder
     * @see #whenExactBodiesReceived(Object...)
     */
    public NotifyBuilder whenBodiesReceived(Object... bodies) {
        List<Object> bodyList = new ArrayList<Object>();
        bodyList.addAll(Arrays.asList(bodies));
        return doWhenBodies(bodyList, true, false);
    }

    /**
     * Sets a condition that the bodies is expected to be <b>done</b> in the order as well.
     * <p/>
     * This condition will discard any additional messages. If you need a more strict condition
     * then use {@link #whenExactBodiesDone(Object...)}
     *
     * @param bodies the expected bodies
     * @return the builder
     * @see #whenExactBodiesDone(Object...)
     */
    public NotifyBuilder whenBodiesDone(Object... bodies) {
        List<Object> bodyList = new ArrayList<Object>();
        bodyList.addAll(Arrays.asList(bodies));
        return doWhenBodies(bodyList, false, false);
    }

    /**
     * Sets a condition that the bodies is expected to be <b>received</b> in the order as well.
     * <p/>
     * This condition is strict which means that it only expect that exact number of bodies
     *
     * @param bodies the expected bodies
     * @return the builder
     * @see #whenBodiesReceived(Object...)
     */
    public NotifyBuilder whenExactBodiesReceived(Object... bodies) {
        List<Object> bodyList = new ArrayList<Object>();
        bodyList.addAll(Arrays.asList(bodies));
        return doWhenBodies(bodyList, true, true);
    }

    /**
     * Sets a condition that the bodies is expected to be <b>done</b> in the order as well.
     * <p/>
     * This condition is strict which means that it only expect that exact number of bodies
     *
     * @param bodies the expected bodies
     * @return the builder
     * @see #whenExactBodiesDone(Object...)
     */
    public NotifyBuilder whenExactBodiesDone(Object... bodies) {
        List<Object> bodyList = new ArrayList<Object>();
        bodyList.addAll(Arrays.asList(bodies));
        return doWhenBodies(bodyList, false, true);
    }

    private NotifyBuilder doWhenBodies(final List bodies, final boolean received, final boolean exact) {
        stack.push(new EventPredicateSupport() {
            private boolean matches;
            private int current;

            @Override
            public boolean onExchangeCreated(Exchange exchange) {
                if (received) {
                    matchBody(exchange);
                }
                return true;
            }

            @Override
            public boolean onExchangeFailed(Exchange exchange) {
                if (!received) {
                    matchBody(exchange);
                }
                return true;
            }

            @Override
            public boolean onExchangeCompleted(Exchange exchange) {
                if (!received) {
                    matchBody(exchange);
                }
                return true;
            }

            private void matchBody(Exchange exchange) {
                current++;

                if (current > bodies.size()) {
                    // out of bounds
                    return;
                }

                Object actual = exchange.getIn().getBody();
                Object expected = bodies.get(current - 1);
                matches = ObjectHelper.equal(expected, actual);
            }

            public boolean matches() {
                if (exact) {
                    return matches && current == bodies.size();
                } else {
                    return matches && current >= bodies.size();
                }
            }

            @Override
            public void reset() {
                matches = false;
                current = 0;
            }

            @Override
            public String toString() {
                if (received) {
                    return "" + (exact ? "whenExactBodiesReceived(" : "whenBodiesReceived(") + bodies + ")";
                } else {
                    return "" + (exact ? "whenExactBodiesDone(" : "whenBodiesDone(") + bodies + ")";
                }
            }
        });
        return this;
    }

    /**
     * Prepares to append an additional expression using the <i>and</i> operator.
     *
     * @return the builder
     */
    public NotifyBuilder and() {
        doCreate(EventOperation.and);
        return this;
    }

    /**
     * Prepares to append an additional expression using the <i>or</i> operator.
     *
     * @return the builder
     */
    public NotifyBuilder or() {
        doCreate(EventOperation.or);
        return this;
    }

    /**
     * Prepares to append an additional expression using the <i>not</i> operator.
     *
     * @return the builder
     */
    public NotifyBuilder not() {
        doCreate(EventOperation.not);
        return this;
    }

    /**
     * Creates the expression this builder should use for matching.
     * <p/>
     * You must call this method when you are finished building the expressions.
     *
     * @return the created builder ready for matching
     */
    public NotifyBuilder create() {
        doCreate(EventOperation.and);
        created = true;
        return this;
    }

    /**
     * Does all the expression match?
     * <p/>
     * This operation will return immediately which means it can be used for testing at this very moment.
     *
     * @return <tt>true</tt> if matching, <tt>false</tt> otherwise
     */
    public boolean matches() {
        if (!created) {
            throw new IllegalStateException("NotifyBuilder has not been created. Invoke the create() method before matching.");
        }
        return matches;
    }

    /**
     * Does all the expression match?
     * <p/>
     * This operation will wait until the match is <tt>true</tt> or otherwise a timeout occur
     * which means <tt>false</tt> will be returned.
     *
     * @param timeout  the timeout value
     * @param timeUnit the time unit
     * @return <tt>true</tt> if matching, <tt>false</tt> otherwise due to timeout
     */
    public boolean matches(long timeout, TimeUnit timeUnit) {
        if (!created) {
            throw new IllegalStateException("NotifyBuilder has not been created. Invoke the create() method before matching.");
        }
        try {
            latch.await(timeout, timeUnit);
        } catch (InterruptedException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
        return matches();
    }

    /**
     * Does all the expression match?
     * <p/>
     * This operation will wait until the match is <tt>true</tt> or otherwise a timeout occur
     * which means <tt>false</tt> will be returned.
     * <p/>
     * The timeout value is by default 10 seconds. But it will use the highest <i>maximum result wait time</i>
     * from the configured mocks, if such a value has been configured.
     * <p/>
     * This method is convenient to use in unit tests to have it adhere and wait
     * as long as the mock endpoints.
     *
     * @return <tt>true</tt> if matching, <tt>false</tt> otherwise due to timeout
     */
    public boolean matchesMockWaitTime() {
        if (!created) {
            throw new IllegalStateException("NotifyBuilder has not been created. Invoke the create() method before matching.");
        }
        long timeout = 0;
        for (Endpoint endpoint : context.getEndpoints()) {
            if (endpoint instanceof MockEndpoint) {
                long waitTime = ((MockEndpoint) endpoint).getResultWaitTime();
                if (waitTime > 0) {
                    timeout = Math.max(timeout, waitTime);
                }
            }
        }

        // use 10 sec as default
        if (timeout == 0) {
            timeout = 10000;
        }

        return matches(timeout, TimeUnit.MILLISECONDS);
    }

    /**
     * Resets the notifier.
     */
    public void reset() {
        for (EventPredicateHolder predicate : predicates) {
            predicate.reset();
        }
        latch = new CountDownLatch(1);
        matches = false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Iterator<EventPredicateHolder> it = predicates.iterator(); it.hasNext();) {
            if (sb.length() > 0) {
                sb.append(".");
            }
            sb.append(it.next().toString());
        }
        // a crude way of skipping the first invisible operation
        return ObjectHelper.after(sb.toString(), "().");
    }

    private void doCreate(EventOperation newOperation) {
        // init operation depending on the newOperation
        if (operation == null) {
            // if the first new operation is an or then this operation must be an or as well
            // otherwise it should be and based
            operation = newOperation == EventOperation.or ? EventOperation.or : EventOperation.and;
        }

        // we have some
        if (!stack.isEmpty()) {
            CompoundEventPredicate compound = new CompoundEventPredicate(stack);
            stack.clear();
            predicates.add(new EventPredicateHolder(operation, compound));
        }

        operation = newOperation;
    }

    /**
     * Notifier which hooks into Camel to listen for {@link Exchange} relevant events for this builder
     */
    private final class ExchangeNotifier extends EventNotifierSupport {

        public void notify(EventObject event) throws Exception {
            if (event instanceof ExchangeCreatedEvent) {
                onExchangeCreated((ExchangeCreatedEvent) event);
            } else if (event instanceof ExchangeCompletedEvent) {
                onExchangeCompleted((ExchangeCompletedEvent) event);
            } else if (event instanceof ExchangeFailedEvent) {
                onExchangeFailed((ExchangeFailedEvent) event);
            }

            // now compute whether we matched
            computeMatches();
        }

        public boolean isEnabled(EventObject event) {
            return true;
        }

        private void onExchangeCreated(ExchangeCreatedEvent event) {
            for (EventPredicateHolder predicate : predicates) {
                predicate.getPredicate().onExchangeCreated(event.getExchange());
            }
        }

        private void onExchangeCompleted(ExchangeCompletedEvent event) {
            for (EventPredicateHolder predicate : predicates) {
                predicate.getPredicate().onExchangeCompleted(event.getExchange());
            }
        }

        private void onExchangeFailed(ExchangeFailedEvent event) {
            for (EventPredicateHolder predicate : predicates) {
                predicate.getPredicate().onExchangeFailed(event.getExchange());
            }
        }

        private synchronized void computeMatches() {
            // use a temporary answer until we have computed the value to assign
            Boolean answer = null;

            for (EventPredicateHolder holder : predicates) {
                EventOperation operation = holder.getOperation();
                if (EventOperation.and == operation) {
                    if (holder.getPredicate().matches()) {
                        answer = true;
                    } else {
                        answer = false;
                        // and break out since its an AND so it must match
                        break;
                    }
                } else if (EventOperation.or == operation) {
                    if (holder.getPredicate().matches()) {
                        answer = true;
                    }
                } else if (EventOperation.not == operation) {
                    if (holder.getPredicate().matches()) {
                        answer = false;
                        // and break out since its a NOT so it must not match
                        break;
                    } else {
                        answer = true;
                    }
                }
            }

            // if we did compute a value then assign that
            if (answer != null) {
                matches = answer;
                if (matches) {
                    // signal completion
                    latch.countDown();
                }
            }
        }

        @Override
        protected void doStart() throws Exception {
            // we only care about Exchange events
            setIgnoreCamelContextEvents(true);
            setIgnoreRouteEvents(true);
            setIgnoreServiceEvents(true);
        }

        @Override
        protected void doStop() throws Exception {
        }
    }

    private enum EventOperation {
        and, or, not;
    }

    private interface EventPredicate {

        /**
         * Evaluates whether the predicate matched or not.
         *
         * @return <tt>true</tt> if matched, <tt>false</tt> otherwise
         */
        boolean matches();

        /**
         * Resets the predicate
         */
        void reset();

        /**
         * Callback for {@link Exchange} lifecycle
         *
         * @param exchange the exchange
         * @return <tt>true</tt> to allow continue evaluating, <tt>false</tt> to stop immediately
         */
        boolean onExchangeCreated(Exchange exchange);

        /**
         * Callback for {@link Exchange} lifecycle
         *
         * @param exchange the exchange
         * @return <tt>true</tt> to allow continue evaluating, <tt>false</tt> to stop immediately
         */
        boolean onExchangeCompleted(Exchange exchange);

        /**
         * Callback for {@link Exchange} lifecycle
         *
         * @param exchange the exchange
         * @return <tt>true</tt> to allow continue evaluating, <tt>false</tt> to stop immediately
         */
        boolean onExchangeFailed(Exchange exchange);
    }

    private abstract class EventPredicateSupport implements EventPredicate {

        public void reset() {
            // noop
        }

        public boolean onExchangeCreated(Exchange exchange) {
            return onExchange(exchange);
        }

        public boolean onExchangeCompleted(Exchange exchange) {
            return onExchange(exchange);
        }

        public boolean onExchangeFailed(Exchange exchange) {
            return onExchange(exchange);
        }

        public boolean onExchange(Exchange exchange) {
            return true;
        }
    }

    /**
     * To hold an operation and predicate
     */
    private final class EventPredicateHolder {
        private final EventOperation operation;
        private final EventPredicate predicate;

        private EventPredicateHolder(EventOperation operation, EventPredicate predicate) {
            this.operation = operation;
            this.predicate = predicate;
        }

        public EventOperation getOperation() {
            return operation;
        }

        public EventPredicate getPredicate() {
            return predicate;
        }

        public void reset() {
            predicate.reset();
        }

        @Override
        public String toString() {
            return operation.name() + "()." + predicate;
        }
    }

    /**
     * To hold multiple predicates which are part of same expression
     */
    private final class CompoundEventPredicate implements EventPredicate {

        private List<EventPredicate> predicates = new ArrayList<EventPredicate>();

        private CompoundEventPredicate(Stack<EventPredicate> predicates) {
            this.predicates.addAll(predicates);
        }

        public boolean matches() {
            for (EventPredicate predicate : predicates) {
                if (!predicate.matches()) {
                    return false;
                }
            }
            return true;
        }

        public void reset() {
            for (EventPredicate predicate : predicates) {
                predicate.reset();
            }
        }

        public boolean onExchangeCreated(Exchange exchange) {
            for (EventPredicate predicate : predicates) {
                if (!predicate.onExchangeCreated(exchange)) {
                    return false;
                }
            }
            return true;
        }

        public boolean onExchangeCompleted(Exchange exchange) {
            for (EventPredicate predicate : predicates) {
                if (!predicate.onExchangeCompleted(exchange)) {
                    return false;
                }
            }
            return true;
        }

        public boolean onExchangeFailed(Exchange exchange) {
            for (EventPredicate predicate : predicates) {
                if (!predicate.onExchangeFailed(exchange)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (Iterator<EventPredicate> it = predicates.iterator(); it.hasNext();) {
                if (sb.length() > 0) {
                    sb.append(".");
                }
                sb.append(it.next().toString());
            }
            return sb.toString();
        }
    }

}