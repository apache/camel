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
package org.apache.camel.processor;

import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.util.EventHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A processor which catches exceptions.
 *
 * @version 
 */
public class CatchProcessor extends DelegateAsyncProcessor implements Traceable, IdAware {
    private static final Logger LOG = LoggerFactory.getLogger(CatchProcessor.class);

    private String id;
    private final List<Class<? extends Throwable>> exceptions;
    private final Predicate onWhen;
    private final Predicate handled;

    public CatchProcessor(List<Class<? extends Throwable>> exceptions, Processor processor, Predicate onWhen, Predicate handled) {
        super(processor);
        this.exceptions = exceptions;
        this.onWhen = onWhen;
        this.handled = handled;
    }

    @Override
    public String toString() {
        return "Catch[" + exceptions + " -> " + getProcessor() + "]";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTraceLabel() {
        return "catch";
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        Exception e = exchange.getException();
        Throwable caught = catches(exchange, e);
        // If a previous catch clause handled the exception or if this clause does not match, exit
        if (exchange.getProperty(Exchange.EXCEPTION_HANDLED) != null || caught == null) {
            callback.done(true);
            return true;
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("This CatchProcessor catches the exception: {} caused by: {}", caught.getClass().getName(), e.getMessage());
        }

        // store the last to endpoint as the failure endpoint
        if (exchange.getProperty(Exchange.FAILURE_ENDPOINT) == null) {
            exchange.setProperty(Exchange.FAILURE_ENDPOINT, exchange.getProperty(Exchange.TO_ENDPOINT));
        }
        // give the rest of the pipeline another chance
        exchange.setProperty(Exchange.EXCEPTION_HANDLED, true);
        exchange.setProperty(Exchange.EXCEPTION_CAUGHT, e);
        exchange.setException(null);
        // and we should not be regarded as exhausted as we are in a try .. catch block
        exchange.removeProperty(Exchange.REDELIVERY_EXHAUSTED);

        // is the exception handled by the catch clause
        final boolean handled = handles(exchange);

        if (LOG.isDebugEnabled()) {
            LOG.debug("The exception is handled: {} for the exception: {} caused by: {}",
                    new Object[]{handled, e.getClass().getName(), e.getMessage()});
        }

        if (handled) {
            // emit event that the failure is being handled
            EventHelper.notifyExchangeFailureHandling(exchange.getContext(), exchange, processor, false, null);
        }

        boolean sync = processor.process(exchange, new AsyncCallback() {
            public void done(boolean doneSync) {
                if (handled) {
                    // emit event that the failure was handled
                    EventHelper.notifyExchangeFailureHandled(exchange.getContext(), exchange, processor, false, null);
                } else {
                    if (exchange.getException() == null) {
                        exchange.setException(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class));
                    }
                }
                // always clear redelivery exhausted in a catch clause
                exchange.removeProperty(Exchange.REDELIVERY_EXHAUSTED);

                if (!doneSync) {
                    // signal callback to continue routing async
                    ExchangeHelper.prepareOutToIn(exchange);
                }

                callback.done(doneSync);
            }
        });

        return sync;
    }

    /**
     * Returns with the exception that is caught by this processor.
     *
     * This method traverses exception causes, so sometimes the exception
     * returned from this method might be one of causes of the parameter
     * passed.
     *
     * @param exchange  the current exchange
     * @param exception the thrown exception
     * @return Throwable that this processor catches. <tt>null</tt> if nothing matches.
     */
    protected Throwable catches(Exchange exchange, Throwable exception) {
        // use the exception iterator to walk the caused by hierarchy
        for (final Throwable e : ObjectHelper.createExceptionIterable(exception)) {
            // see if we catch this type
            for (final Class<?> type : exceptions) {
                if (type.isInstance(e) && matchesWhen(exchange)) {
                    return e;
                }
            }
        }

        // not found
        return null;
    }

    /**
     * Whether this catch processor handles the exception it have caught
     *
     * @param exchange  the current exchange
     * @return <tt>true</tt> if this processor handles it, <tt>false</tt> otherwise.
     */
    protected boolean handles(Exchange exchange) {
        if (handled == null) {
            // handle by default
            return true;
        }

        return handled.matches(exchange);
    }

    public List<Class<? extends Throwable>> getExceptions() {
        return exceptions;
    }

    /**
     * Strategy method for matching the exception type with the current exchange.
     * <p/>
     * This default implementation will match as:
     * <ul>
     *   <li>Always true if no when predicate on the exception type
     *   <li>Otherwise the when predicate is matches against the current exchange
     * </ul>
     *
     * @param exchange the current {@link org.apache.camel.Exchange}
     * @return <tt>true</tt> if matched, <tt>false</tt> otherwise.
     */
    protected boolean matchesWhen(Exchange exchange) {
        if (onWhen == null) {
            // if no predicate then it's always a match
            return true;
        }
        return onWhen.matches(exchange);
    }
}
