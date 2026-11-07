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
package org.apache.camel.impl.engine;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.ErrorHandlerAware;
import org.apache.camel.spi.StepListener;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calls the {@link StepListener}s around each call of the processor of a step. {@link DefaultChannel} adds it after the
 * {@link org.apache.camel.spi.InterceptStrategy} wrappers of the step, only when a listener is installed when the route
 * is created, so it is the output of the channel that the error handler of the step calls for each attempt.
 */
class StepListenerProcessor extends DelegateAsyncProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(StepListenerProcessor.class);

    private static final StepListener[] NO_LISTENERS = new StepListener[0];

    // the state of a listener whose onStepCall threw: it gets no more events for that call
    private static final Object FAILED = new Object();

    // the listeners that already failed once, so they are logged at WARN once (weak keys: a listener is not kept)
    private static final Map<StepListener, Boolean> FAILED_LISTENERS = Collections.synchronizedMap(new WeakHashMap<>());

    private final List<StepListener> listeners;
    private final Processor step;
    private final String routeId;
    private final String nodeId;

    StepListenerProcessor(List<StepListener> listeners, String routeId, String nodeId, Processor step,
                          Processor processor) {
        super(processor);
        this.listeners = listeners;
        this.routeId = routeId;
        this.nodeId = nodeId;
        this.step = step;
    }

    /**
     * Creates the wrapper, which is also {@link ErrorHandlerAware} when what it wraps is, so the error handler of the
     * step is still given to the processor (multicast, recipient list, bean) when the error handler of the step is
     * created, as without the wrapper. (At the end of route creation, {@code RouteReifier} gives these processors the
     * error handler of the route anyway, as it finds them through {@link org.apache.camel.Channel#getNextProcessor()}.)
     */
    static StepListenerProcessor create(
            List<StepListener> listeners, String routeId, String nodeId, Processor step, Processor processor) {
        if (processor instanceof ErrorHandlerAware) {
            return new ErrorHandlerAwareStepListenerProcessor(listeners, routeId, nodeId, step, processor);
        }
        return new StepListenerProcessor(listeners, routeId, nodeId, step, processor);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        // the listeners when the call starts are kept for its callback
        final StepListener[] called = listeners.toArray(NO_LISTENERS);
        if (called.length == 0) {
            return processor.process(exchange, callback);
        }
        final Object[] states = new Object[called.length];
        for (int i = 0; i < called.length; i++) {
            try {
                states[i] = called[i].onStepCall(exchange, routeId, nodeId);
            } catch (Throwable e) {
                states[i] = FAILED;
                failed(called[i], "onStepCall", e);
            }
        }
        final StepCallback stepCallback = new StepCallback(called, states, callback);
        final boolean doneSync;
        try {
            doneSync = processor.process(exchange, stepCallback);
        } catch (Throwable e) {
            for (int i = 0; i < called.length; i++) {
                if (states[i] != FAILED) {
                    try {
                        called[i].onStepThrow(states[i], e);
                    } catch (Throwable t) {
                        failed(called[i], "onStepThrow", t);
                    }
                }
            }
            // what the processor threw is thrown on unchanged
            throw e;
        }
        for (int i = 0; i < called.length; i++) {
            if (states[i] != FAILED) {
                try {
                    called[i].onStepReturn(states[i], doneSync);
                } catch (Throwable t) {
                    failed(called[i], "onStepReturn", t);
                }
            }
        }
        return doneSync;
    }

    static void failed(StepListener listener, String method, Throwable cause) {
        try {
            if (FAILED_LISTENERS.put(listener, Boolean.TRUE) == null) {
                LOG.warn("StepListener {} failed in {} (logged once for this listener, then at DEBUG)", listener, method,
                        cause);
            } else {
                LOG.debug("StepListener {} failed in {}", listener, method, cause);
            }
        } catch (Throwable t) {
            // a listener cannot change the routing, also when logging it fails (such as its toString)
        }
    }

    @Override
    public String toString() {
        return step != null ? step.toString() : super.toString();
    }

    /**
     * The callback of one call: calls the listeners, and then the callback of the call, each time it is called.
     */
    private static final class StepCallback implements AsyncCallback {

        private final StepListener[] called;
        private final Object[] states;
        private final AsyncCallback callback;

        StepCallback(StepListener[] called, Object[] states, AsyncCallback callback) {
            this.called = called;
            this.states = states;
            this.callback = callback;
        }

        @Override
        public void done(boolean doneSync) {
            try {
                for (int i = 0; i < called.length; i++) {
                    if (states[i] != FAILED) {
                        try {
                            called[i].onStepCallback(states[i], doneSync);
                        } catch (Throwable t) {
                            failed(called[i], "onStepCallback", t);
                        }
                    }
                }
            } finally {
                callback.done(doneSync);
            }
        }
    }

    /**
     * The wrapper of a processor that is {@link ErrorHandlerAware}.
     */
    private static final class ErrorHandlerAwareStepListenerProcessor extends StepListenerProcessor
            implements ErrorHandlerAware {

        private final ErrorHandlerAware errorHandlerAware;

        ErrorHandlerAwareStepListenerProcessor(List<StepListener> listeners, String routeId, String nodeId,
                                               Processor step, Processor processor) {
            super(listeners, routeId, nodeId, step, processor);
            this.errorHandlerAware = (ErrorHandlerAware) processor;
        }

        @Override
        public void setErrorHandler(Processor errorHandler) {
            errorHandlerAware.setErrorHandler(errorHandler);
        }

        @Override
        public Processor getErrorHandler() {
            return errorHandlerAware.getErrorHandler();
        }
    }
}
