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
package org.apache.camel.impl;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.processor.loadbalancer.CircuitBreakerLoadBalancer;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.support.RoutePolicySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * modeled after the {@link CircuitBreakerLoadBalancer} and {@link ThrottlingInflightRoutePolicy}
 * this {@link RoutePolicy} will stop consuming from an endpoint based on the type of exceptions that are
 * thrown and the threshold setting. 
 * 
 * the scenario: if a route cannot process data from an endpoint due to problems with resources used by the route
 * (ie database down) then it will stop consuming new messages from the endpoint by stopping the consumer. 
 * The implementation is comparable to the Circuit Breaker pattern. After a set amount of time, it will move 
 * to a half open state and attempt to determine if the consumer can be started.
 * There are two ways to determine if a route can be closed after being opened
 * (1) start the consumer and check the failure threshold
 * (2) call the {@link ThrottlingExceptionHalfOpenHandler} 
 * The second option allows a custom check to be performed without having to take on the possibiliy of 
 * multiple messages from the endpoint. The idea is that a handler could run a simple test (ie select 1 from dual)
 * to determine if the processes that cause the route to be open are now available  
 */
public class ThrottlingExceptionRoutePolicy extends RoutePolicySupport implements CamelContextAware {
    private static Logger log = LoggerFactory.getLogger(ThrottlingExceptionRoutePolicy.class);
    
    private static final int STATE_CLOSED = 0;
    private static final int STATE_HALF_OPEN = 1;
    private static final int STATE_OPEN = 2;
    
    private CamelContext camelContext;
    private final Lock lock = new ReentrantLock();
    
    // configuration
    private int failureThreshold;
    private long failureWindow;
    private long halfOpenAfter;
    private final List<Class<?>> throttledExceptions;
    
    // handler for half open circuit
    // can be used instead of resuming route
    // to check on resources
    private ThrottingExceptionHalfOpenHandler halfOpenHandler;

    // stateful information
    private Timer halfOpenTimer;
    private AtomicInteger failures = new AtomicInteger();
    private AtomicInteger state = new AtomicInteger(STATE_CLOSED);
    private long lastFailure;
    private long openedAt;
    
    public ThrottlingExceptionRoutePolicy(int threshold, long failureWindow, long halfOpenAfter, List<Class<?>> handledExceptions) {
        this.throttledExceptions = handledExceptions;
        this.failureWindow = failureWindow;
        this.halfOpenAfter = halfOpenAfter;
        this.failureThreshold = threshold;
    }
    
    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void onInit(Route route) {
        log.debug("initializing ThrottlingExceptionRoutePolicy route policy...");
        logState();
    }
    
    @Override
    public void onExchangeDone(Route route, Exchange exchange) {
        if (hasFailed(exchange)) {
            // record the failure
            failures.incrementAndGet();
            lastFailure = System.currentTimeMillis();
        } 
        
        // check for state change
        calculateState(route);
    }
    
    /**
     * uses similar approach as {@link CircuitBreakerLoadBalancer}
     * if the exchange has an exception that we are watching 
     * then we count that as a failure otherwise we ignore it
     * @param exchange
     * @return
     */
    private boolean hasFailed(Exchange exchange) {
        if (exchange == null) {
            return false;
        }

        boolean answer = false;

        if (exchange.getException() != null) {
            log.debug("exception occured on route: checking to see if I handle that");
            if (throttledExceptions == null || throttledExceptions.isEmpty()) {
                // if no exceptions defined then always fail 
                // (ie) assume we throttle on all exceptions
                answer = true;
            } else {
                for (Class<?> exception : throttledExceptions) {
                    // will look in exception hierarchy
                    if (exchange.getException(exception) != null) {
                        answer = true;
                        break;
                    }
                }
            }
        }

        if (log.isDebugEnabled()) {
            String exceptionName = exchange.getException() == null ? "none" : exchange.getException().getClass().getSimpleName();
            log.debug("hasFailed ({}) with Throttled Exception: {} for exchangeId: {}", answer, exceptionName, exchange.getExchangeId());
        }
        return answer;
    }

    private void calculateState(Route route) {
        
        // have we reached the failure limit?
        boolean failureLimitReached = isThresholdExceeded();
        
        if (state.get() == STATE_CLOSED) {
            if (failureLimitReached) {
                log.debug("opening circuit...");
                openCircuit(route);
            }
        } else if (state.get() == STATE_HALF_OPEN) {
            if (failureLimitReached) {
                log.debug("opening circuit...");
                openCircuit(route);
            } else {
                log.debug("closing circuit...");
                closeCircuit(route);
            }
        } else if (state.get() == STATE_OPEN) {
            long elapsedTimeSinceOpened = System.currentTimeMillis() - openedAt;
            if (halfOpenAfter <= elapsedTimeSinceOpened) {
                log.debug("checking an open circuit...");
                if (halfOpenHandler != null) {
                    if (halfOpenHandler.isReadyToBeClosed()) {
                        log.debug("closing circuit...");
                        closeCircuit(route);
                    } else {
                        log.debug("opening circuit...");
                        openCircuit(route);
                    }
                } else {
                    log.debug("half opening circuit...");
                    halfOpenCircuit(route);                    
                }
            } 
        }
        
    }
    
    protected boolean isThresholdExceeded() {
        boolean output = false;
        logState();
        // failures exceed the threshold 
        // AND the last of those failures occurred within window
        if ((failures.get() >= failureThreshold) && (lastFailure >= System.currentTimeMillis() - failureWindow)) {
            output = true;
        }
        
        return output;
    }
        
    protected void openCircuit(Route route) {
        try {
            lock.lock();
            stopConsumer(route.getConsumer());
            state.set(STATE_OPEN);
            openedAt = System.currentTimeMillis();
            halfOpenTimer = new Timer();
            halfOpenTimer.schedule(new HalfOpenTask(route), halfOpenAfter);
            logState();
        } catch (Exception e) {
            handleException(e);
        } finally {
            lock.unlock();
        }
    }

    protected void halfOpenCircuit(Route route) {
        try {
            lock.lock();
            startConsumer(route.getConsumer());
            state.set(STATE_HALF_OPEN);
            logState();
        } catch (Exception e) {
            handleException(e);
        } finally {
            lock.unlock();
        }
    }
    
    protected void closeCircuit(Route route) {
        try {
            lock.lock();
            startConsumer(route.getConsumer());
            this.reset();
            logState();
        } catch (Exception e) {
            handleException(e);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * reset the route 
     */
    private void reset() {
        failures.set(0);
        lastFailure = 0;
        openedAt = 0;
        state.set(STATE_CLOSED);
    }
    
    private void logState() {
        if (log.isDebugEnabled()) {
            log.debug(dumpState());
        }
    }
    
    public String dumpState() {
        int num = state.get();
        String state = stateAsString(num);
        if (failures.get() > 0) {
            return String.format("*** State %s, failures %d, last failure %d ms ago", state, failures.get(), System.currentTimeMillis() - lastFailure);
        } else {
            return String.format("*** State %s, failures %d", state, failures.get());
        }
    }
    
    private static String stateAsString(int num) {
        if (num == STATE_CLOSED) {
            return "closed";
        } else if (num == STATE_HALF_OPEN) {
            return "half opened";
        } else {
            return "opened";
        }
    }
    
    class HalfOpenTask extends TimerTask {
        private final Route route;
        
        HalfOpenTask(Route route) {
            this.route = route;
        }
        
        @Override
        public void run() {
            calculateState(route);
            halfOpenTimer.cancel();
        }
    }
    
    public ThrottingExceptionHalfOpenHandler getHalfOpenHandler() {
        return halfOpenHandler;
    }

    public void setHalfOpenHandler(ThrottingExceptionHalfOpenHandler halfOpenHandler) {
        this.halfOpenHandler = halfOpenHandler;
    }

}
