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

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.spi.StepIdAware;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The processor which sets an {@link Exception} on the {@link Exchange}
 */
public class ThrowExceptionProcessor extends BaseProcessorSupport
        implements Traceable, IdAware, RouteIdAware, StepIdAware, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(ThrowExceptionProcessor.class);

    // The exception instances that throwException steps are configured with (also by ref, which is looked up when the
    // route is created): each is one object shared by all the messages. Kept by the exception, and not on the
    // exchange, as the exception can reach another exchange without the properties of the one it was thrown on (such
    // as from a copy of the exchange by the topic load balancer). Weak keys, so an instance is not kept after its route
    // is removed. Throwable does not override equals, so the set is by identity.
    private static final Set<Throwable> SHARED_EXCEPTIONS
            = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private String id;
    private String routeId;
    private String stepId;
    private CamelContext camelContext;
    private Expression simple;
    private final Exception exception;
    private final Class<? extends Exception> type;
    private final String message;

    public ThrowExceptionProcessor(Exception exception) {
        this(exception, null, null);
    }

    public ThrowExceptionProcessor(Exception exception, Class<? extends Exception> type, String message) {
        this.exception = exception;
        this.type = type;
        this.message = message;
        if (exception != null && (type == null || message == null)) {
            // the instance is set on every message (see process)
            SHARED_EXCEPTIONS.add(exception);
        }
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        Exception cause = exception;

        try {
            if (message != null && type != null) {
                // create the message using simple language so it can be dynamic
                String text = simple.evaluate(exchange, String.class);
                // create a new exception of that type, and provide the message as
                Constructor<?> constructor = type.getConstructor(String.class);
                cause = (Exception) constructor.newInstance(text);
                exchange.setException(cause);
            } else if (cause == null && type != null) {
                // create a new exception of that type using its default constructor
                Constructor<?> constructor = type.getDeclaredConstructor();
                cause = (Exception) constructor.newInstance();
                exchange.setException(cause);
            } else {
                exchange.setException(cause);
            }
        } catch (Exception e) {
            Class<? extends Exception> exceptionClass = exception != null ? exception.getClass() : type;
            exchange.setException(
                    new CamelExchangeException("Error creating new instance of " + exceptionClass, exchange, e));
        }

        callback.done(true);
        return true;
    }

    /**
     * Whether the exception is an instance a throwException step is configured with: one object shared by all the
     * messages, to which Camel must not add the exceptions of a message as suppressed (it would keep them all).
     * <p>
     * Internal API, for the processors of Camel.
     *
     * @param  exception the exception
     * @return           <code>true</code> if the exception is shared by all the messages
     */
    public static boolean isSharedException(Throwable exception) {
        return exception != null && SHARED_EXCEPTIONS.contains(exception);
    }

    /**
     * Adds the other exception as suppressed to the exception the exchange failed with, unless that is an instance a
     * throwException step is configured with (see {@link #isSharedException(Throwable)}): then the other exception is
     * logged instead.
     * <p>
     * Internal API, for the processors of Camel.
     *
     * @param exchange  the exchange
     * @param exception the exception the exchange failed with
     * @param other     the other exception, such as from a doFinally that failed too
     */
    public static void addSuppressed(Exchange exchange, Throwable exception, Throwable other) {
        if (isSharedException(exception)) {
            LOG.warn("Exchange: {} failed with {}, the exception instance of a throwException step which all the messages"
                     + " share, so this other exception is logged and not added to it as suppressed. Caused by: {}",
                    exchange.getExchangeId(), exception, other.getMessage(), other);
        } else {
            exception.addSuppressed(other);
        }
    }

    @Override
    public String getTraceLabel() {
        String className = this.exception == null ? this.type.getSimpleName() : this.exception.getClass().getSimpleName();
        return "throwException[" + className + "]";
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    @Override
    public String getStepId() {
        return stepId;
    }

    @Override
    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public Exception getException() {
        return exception;
    }

    public Class<? extends Exception> getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    protected void doInit() throws Exception {
        ObjectHelper.notNull(camelContext, "camelContext", this);

        if (message != null) {
            simple = camelContext.resolveLanguage("simple").createExpression(message);
        }
    }

}
