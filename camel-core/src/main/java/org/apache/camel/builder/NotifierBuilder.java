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
import java.util.EventObject;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Producer;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.management.EventNotifierSupport;
import org.apache.camel.management.event.ExchangeCompletedEvent;
import org.apache.camel.management.event.ExchangeCreatedEvent;
import org.apache.camel.management.event.ExchangeFailureEvent;
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
 * @version $Revision$
 */
public class NotifierBuilder {

    // notifier to hook into Camel to listen for events
    private final EventNotifier eventNotifier = new ExchangeNotifier();

    // the predicates build with this builder
    private final List<EventPredicateHolder> predicates = new ArrayList<EventPredicateHolder>();

    // latch to be used to signal predicates matches
    private final CountDownLatch latch = new CountDownLatch(1);

    // the current state while building an event predicate where we use a stack and the operation
    private final Stack<EventPredicate> stack = new Stack<EventPredicate>();
    private EventOperation operation;

    // computed value whether all the predicates matched
    private boolean matches;

    /**
     * Creates a new builder.
     *
     * @param context the Camel context
     */
    public NotifierBuilder(CamelContext context) {
        try {
            ServiceHelper.startService(eventNotifier);
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
        context.getManagementStrategy().addEventNotifier(eventNotifier);
    }

    /**
     * Optionally a <tt>from</tt> endpoint which means that this expression should only be based
     * on {@link Exchange} which is originated from the particular endpoint(s).
     *
     * @param endpointUri uri of endpoint or pattern (see the EndpointHelper javadoc)
     * @return the builder
     * @see org.apache.camel.util.EndpointHelper#matchEndpoint(String, String)
     */
    public NotifierBuilder from(final String endpointUri) {
        stack.push(new EventPredicateSupport() {

            @Override
            public boolean onExchangeCreated(Exchange exchange) {
                return EndpointHelper.matchEndpoint(exchange.getFromEndpoint().getEndpointUri(), endpointUri);
            }

            @Override
            public boolean onExchangeCompleted(Exchange exchange) {
                return EndpointHelper.matchEndpoint(exchange.getFromEndpoint().getEndpointUri(), endpointUri);
            }

            @Override
            public boolean onExchangeFailure(Exchange exchange) {
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
     * Sets a condition when <tt>number</tt> of {@link Exchange} has been received.
     * <p/>
     * The number matching is <i>at least</i> based which means that if more messages received
     * it will match also.
     *
     * @param number at least number of messages
     * @return the builder
     */
    public NotifierBuilder whenReceived(final int number) {
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
    public NotifierBuilder whenDone(final int number) {
        stack.add(new EventPredicateSupport() {
            private int current;

            @Override
            public boolean onExchangeCompleted(Exchange exchange) {
                current++;
                return true;
            }

            @Override
            public boolean onExchangeFailure(Exchange exchange) {
                current++;
                return true;
            }

            public boolean matches() {
                return current >= number;
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
    public NotifierBuilder whenCompleted(final int number) {
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
    public NotifierBuilder whenFailed(final int number) {
        stack.add(new EventPredicateSupport() {
            private int current;

            @Override
            public boolean onExchangeFailure(Exchange exchange) {
                current++;
                return true;
            }

            public boolean matches() {
                return current >= number;
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
    public NotifierBuilder whenExactlyDone(final int number) {
        stack.add(new EventPredicateSupport() {
            private int current;

            @Override
            public boolean onExchangeCompleted(Exchange exchange) {
                current++;
                return true;
            }

            @Override
            public boolean onExchangeFailure(Exchange exchange) {
                current++;
                return true;
            }

            public boolean matches() {
                return current == number;
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
    public NotifierBuilder whenExactlyCompleted(final int number) {
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
    public NotifierBuilder whenExactlyFailed(final int number) {
        stack.add(new EventPredicateSupport() {
            private int current;

            @Override
            public boolean onExchangeFailure(Exchange exchange) {
                current++;
                return true;
            }

            public boolean matches() {
                return current == number;
            }

            @Override
            public String toString() {
                return "whenExactlyFailed(" + number + ")";
            }
        });
        return this;
    }

    /**
     * Sets a condition that <b>any</b> received {@link Exchange} should match the {@link Predicate}
     *
     * @param predicate the predicate
     * @return the builder
     */
    public NotifierBuilder whenAnyReceivedMatches(final Predicate predicate) {
        stack.push(new EventPredicateSupport() {
            private boolean matches;

            @Override
            public boolean onExchangeCreated(Exchange exchange) {
                if (!matches) {
                    matches = predicate.matches(exchange);
                }
                return true;
            }

            public boolean matches() {
                return matches;
            }

            @Override
            public String toString() {
                return "whenAnyReceivedMatches(" + predicate + ")";
            }
        });
        return this;
    }

    /**
     * Sets a condition that <b>all</b> received {@link Exchange} should match the {@link Predicate}
     *
     * @param predicate the predicate
     * @return the builder
     */
    public NotifierBuilder whenAllReceivedMatches(final Predicate predicate) {
        stack.push(new EventPredicateSupport() {
            private boolean matches = true;

            @Override
            public boolean onExchangeCreated(Exchange exchange) {
                if (matches) {
                    matches = predicate.matches(exchange);
                }
                return true;
            }

            public boolean matches() {
                return matches;
            }

            @Override
            public String toString() {
                return "whenAllReceivedMatches(" + predicate + ")";
            }
        });
        return this;
    }

    /**
     * Sets a condition when the provided mock is satisfied.
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
    public NotifierBuilder whenSatisfied(final MockEndpoint mock) {
        stack.push(new EventPredicateSupport() {

            private Producer producer;

            @Override
            public boolean onExchangeFailure(Exchange exchange) {
                return sendToMock(exchange);
            }

            @Override
            public boolean onExchangeCompleted(Exchange exchange) {
                return sendToMock(exchange);
            }

            private boolean sendToMock(Exchange exchange) {
                // send the exchange when its completed to the mock
                try {
                    if (producer == null) {
                        producer = mock.createProducer();
                    }
                    producer.process(exchange);
                } catch (Exception e) {
                    throw ObjectHelper.wrapRuntimeCamelException(e);
                }
                return true;
            }

            public boolean matches() {
                try {
                    return mock.await(0, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    throw ObjectHelper.wrapRuntimeCamelException(e);
                }
            }

            @Override
            public String toString() {
                return "whenSatisfied(" + mock + ")";
            }
        });
        return this;
    }


    /**
     * Prepares to append an additional expression using the <i>and</i> operator.
     *
     * @return the builder
     */
    public NotifierBuilder and() {
        doCreate(EventOperation.and);
        return this;
    }

    /**
     * Prepares to append an additional expression using the <i>or</i> operator.
     *
     * @return the builder
     */
    public NotifierBuilder or() {
        doCreate(EventOperation.or);
        return this;
    }

    /**
     * Prepares to append an additional expression using the <i>not</i> operator.
     *
     * @return the builder
     */
    public NotifierBuilder not() {
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
    public NotifierBuilder create() {
        doCreate(EventOperation.and);
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
        try {
            latch.await(timeout, timeUnit);
        } catch (InterruptedException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
        return matches();
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
            } else if (event instanceof ExchangeFailureEvent) {
                onExchangeFailure((ExchangeFailureEvent) event);
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

        private void onExchangeFailure(ExchangeFailureEvent event) {
            for (EventPredicateHolder predicate : predicates) {
                predicate.getPredicate().onExchangeFailure(event.getExchange());
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
        boolean onExchangeFailure(Exchange exchange);
    }

    private abstract class EventPredicateSupport implements EventPredicate {

        public boolean onExchangeCreated(Exchange exchange) {
            return onExchange(exchange);
        }

        public boolean onExchangeCompleted(Exchange exchange) {
            return onExchange(exchange);
        }

        public boolean onExchangeFailure(Exchange exchange) {
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

        public boolean onExchangeFailure(Exchange exchange) {
            for (EventPredicate predicate : predicates) {
                if (!predicate.onExchangeFailure(exchange)) {
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