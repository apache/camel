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

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeExtension;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Processor;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.InterceptableProcessor;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.spi.StepIdAware;
import org.apache.camel.support.ExchangeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processor to handle do finally supporting asynchronous routing engine
 */
public class FinallyProcessor extends BaseDelegateProcessorSupport
        implements Traceable, IdAware, RouteIdAware, StepIdAware, InterceptableProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(FinallyProcessor.class);

    private String id;
    private String routeId;
    private String stepId;

    public FinallyProcessor(Processor processor) {
        super(processor);
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        Exception exception = exchange.getException();
        if (exception != null) {
            // store the caught exception as a property
            exchange.setException(null);
            exchange.setProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, exception);

            // store the last to endpoint as the failure endpoint
            if (exchange.getProperty(ExchangePropertyKey.FAILURE_ENDPOINT) == null) {
                exchange.setProperty(ExchangePropertyKey.FAILURE_ENDPOINT,
                        exchange.getProperty(ExchangePropertyKey.TO_ENDPOINT));
            }
        }

        // a route called from the doTry (such as with direct) can have handled a failure with its error handler (dead
        // letter channel or a handled onException), which marks the exchange as handled by the error handler and as
        // failure handled. The route then does not continue after the doTry, but the doFinally block must still run all
        // its steps (a pipeline stops before its second step when the exchange is handled by the error handler), and a
        // failure in a route that the block calls must be handled by that route's error handler as usual (which does not
        // handle a new failure of an exchange that is failure handled). So the marks are cleared while the block runs,
        // and set again afterwards (as the onCompletion does)
        final ExchangeExtension ext = exchange.getExchangeExtension();
        // a primitive "set and true" snapshot, unlike the nullable Boolean of OnCompletionProcessor: only a failure that
        // an error handler handled needs clearing and restoring here
        final boolean errorHandlerHandled = ext.isErrorHandlerHandledSet() && ext.isErrorHandlerHandled();
        final boolean failureHandled = errorHandlerHandled && ext.isFailureHandled();
        if (errorHandlerHandled) {
            ext.setErrorHandlerHandled(null);
            ext.setFailureHandled(false);
        }

        // continue processing
        return processor.process(exchange,
                new FinallyAsyncCallback(exchange, callback, exception, errorHandlerHandled, failureHandled));
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public String getTraceLabel() {
        return "finally";
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

    @Override
    public boolean canIntercept() {
        return false;
    }

    private static final class FinallyAsyncCallback implements AsyncCallback {

        private final Exchange exchange;
        private final AsyncCallback callback;
        private final Exception exception;
        private final boolean errorHandlerHandled;
        private final boolean failureHandled;

        FinallyAsyncCallback(Exchange exchange, AsyncCallback callback, Exception exception,
                             boolean errorHandlerHandled, boolean failureHandled) {
            this.exchange = exchange;
            this.callback = callback;
            this.exception = exception;
            this.errorHandlerHandled = errorHandlerHandled;
            this.failureHandled = failureHandled;
        }

        @Override
        public void done(boolean doneSync) {
            try {
                if (errorHandlerHandled) {
                    // the doFinally block has run, so the route does not continue after the doTry, as before
                    // (unless the block marked it itself, such as a route it called that failed)
                    final ExchangeExtension ext = exchange.getExchangeExtension();
                    if (!ext.isErrorHandlerHandledSet()) {
                        ext.setErrorHandlerHandled(true);
                    }
                    // one-way restore, unlike OnCompletionProcessor: set again but never cleared, so a failure that the
                    // block handled itself stays marked (errorHandlerHandled without failureHandled does not occur)
                    if (failureHandled) {
                        ext.setFailureHandled(true);
                    }
                }
                if (exception == null) {
                    // only clear the failure details when the doFinally did not fail
                    // (the doTry restores the failure details of an earlier failure)
                    if (exchange.getException() == null) {
                        exchange.removeProperty(ExchangePropertyKey.FAILURE_ENDPOINT);
                        exchange.removeProperty(ExchangePropertyKey.FAILURE_ROUTE_ID);
                        exchange.removeProperty(ExchangePropertyKey.FAILURE_NODE_ID);
                        exchange.removeProperty(ExchangePropertyKey.FAILURE_LOCATION);
                    }
                } else {
                    // the original exception is kept, and an exception from the doFinally is added as suppressed
                    Exception finallyException = exchange.getException();
                    if (finallyException != null && finallyException != exception) {
                        exception.addSuppressed(finallyException);
                    }
                    // set exception back on exchange
                    exchange.setException(exception);
                    exchange.setProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, exception);
                }

                if (!doneSync) {
                    // signal callback to continue routing async
                    ExchangeHelper.prepareOutToIn(exchange);
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Processing complete for exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);
                    }
                }
            } finally {
                // callback must always be called
                callback.done(doneSync);
            }
        }

        @Override
        public String toString() {
            return "FinallyAsyncCallback";
        }
    }

}
