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
import org.apache.camel.management.EventNotifierSupport;
import org.apache.camel.management.event.ExchangeCompletedEvent;
import org.apache.camel.management.event.ExchangeCreatedEvent;
import org.apache.camel.management.event.ExchangeFailureEvent;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;

/**
 * @version $Revision$
 */
public class ExchangeNotifierBuilder {

    // TODO work in progress

    private final List<EventPredicateHolder> predicates = new ArrayList<EventPredicateHolder>();
    private final EventNotifier notifier = new ExchangeNotifier();
    private final CountDownLatch latch = new CountDownLatch(1);
    private final Stack<EventPredicate> stack = new Stack<EventPredicate>();
    private boolean matches;
    private EventOperation operation;

    public ExchangeNotifierBuilder(CamelContext context) throws Exception {
        ServiceHelper.startService(notifier);
        context.getManagementStrategy().addEventNotifier(notifier);
    }

    public ExchangeNotifierBuilder from(final String endpointUri) {
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

    public ExchangeNotifierBuilder whenReceived(final int number) {
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

    public ExchangeNotifierBuilder whenDone(final int number) {
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

    public ExchangeNotifierBuilder whenCompleted(final int number) {
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

    public ExchangeNotifierBuilder whenFailed(final int number) {
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

    public ExchangeNotifierBuilder and() {
        doCreate(EventOperation.and);
        return this;
    }

    public ExchangeNotifierBuilder or() {
        doCreate(EventOperation.or);
        return this;
    }

    public ExchangeNotifierBuilder not() {
        doCreate(EventOperation.not);
        return this;
    }

    public ExchangeNotifierBuilder create() {
        doCreate(EventOperation.and);
        return this;
    }

    private void doCreate(EventOperation newOperation) {
        // init operation depending on the newOperation
        if (operation == null) {
            operation = newOperation == EventOperation.or ? EventOperation.or : EventOperation.and;
        }

        if (!stack.isEmpty()) {
            CompoundEventPredicate compound = new CompoundEventPredicate(stack);
            stack.clear();
            predicates.add(new EventPredicateHolder(operation, compound));
        }

        operation = newOperation;
    }

    public boolean matches() {
        return matches;
    }

    public boolean matches(long timeout, TimeUnit timeUnit) throws InterruptedException {
        latch.await(timeout, timeUnit);
        return matches();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Iterator<EventPredicateHolder> it = predicates.iterator(); it.hasNext(); ) {
            if (sb.length() > 0) {
                sb.append(".");
            }
            sb.append(it.next().toString());
        }
        // a crud way of skipping the first invisible operation
        return ObjectHelper.after(sb.toString(), "().");
    }

    private class ExchangeNotifier extends EventNotifierSupport {

        public void notify(EventObject event) throws Exception {
            if (event instanceof ExchangeCreatedEvent) {
                onExchangeCreated((ExchangeCreatedEvent) event);
            } else if (event instanceof ExchangeCompletedEvent) {
                onExchangeCompleted((ExchangeCompletedEvent) event);
            } else if (event instanceof ExchangeFailureEvent) {
                onExchangeFailure((ExchangeFailureEvent) event);
            }

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

            if (answer != null) {
                matches = answer;
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

    public interface EventPredicate {

        boolean matches();

        boolean onExchangeCreated(Exchange exchange);

        boolean onExchangeCompleted(Exchange exchange);

        boolean onExchangeFailure(Exchange exchange);
    }

    private abstract class EventPredicateSupport implements EventPredicate {

        public boolean onExchangeCreated(Exchange exchange) {
            return true;
        }

        public boolean onExchangeCompleted(Exchange exchange) {
            return true;
        }

        public boolean onExchangeFailure(Exchange exchange) {
            return true;
        }
    }

    private enum EventOperation {
        and, or, not;
    }

    private class EventPredicateHolder {
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

    private class CompoundEventPredicate implements EventPredicate {

        private Stack<EventPredicate> predicates = new Stack<EventPredicate>();

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
            for (Iterator<EventPredicate> it = predicates.iterator(); it.hasNext(); ) {
                if (sb.length() > 0) {
                    sb.append(".");
                }
                sb.append(it.next().toString());
            }
            return sb.toString();
        }
    }

}