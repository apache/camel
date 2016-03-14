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

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.util.ExchangeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processor to handle do finally supporting asynchronous routing engine
 *
 * @version
 */
public class FinallyProcessor extends DelegateAsyncProcessor implements Traceable, IdAware {

    private static final Logger LOG = LoggerFactory.getLogger(FinallyProcessor.class);
    private String id;

    public FinallyProcessor(Processor processor) {
        super(processor);
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        // clear exception and fault so finally block can be executed
        final boolean fault;
        if (exchange.hasOut()) {
            fault = exchange.getOut().isFault();
            exchange.getOut().setFault(false);
        } else {
            fault = exchange.getIn().isFault();
            exchange.getIn().setFault(false);
        }

        final Exception exception = exchange.getException();
        exchange.setException(null);
        // but store the caught exception as a property
        if (exception != null) {
            exchange.setProperty(Exchange.EXCEPTION_CAUGHT, exception);
        }

        // store the last to endpoint as the failure endpoint
        if (exchange.getProperty(Exchange.FAILURE_ENDPOINT) == null) {
            exchange.setProperty(Exchange.FAILURE_ENDPOINT, exchange.getProperty(Exchange.TO_ENDPOINT));
        }

        // continue processing
        return processor.process(exchange, new FinallyAsyncCallback(exchange, callback, exception, fault));
    }

    @Override
    public String toString() {
        return "Finally{" + getProcessor() + "}";
    }

    public String getTraceLabel() {
        return "finally";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    private static final class FinallyAsyncCallback implements AsyncCallback {

        private final Exchange exchange;
        private final AsyncCallback callback;
        private final Exception exception;
        private final boolean fault;

        FinallyAsyncCallback(Exchange exchange, AsyncCallback callback, Exception exception, boolean fault) {
            this.exchange = exchange;
            this.callback = callback;
            this.exception = exception;
            this.fault = fault;
        }

        @Override
        public void done(boolean doneSync) {
            try {
                if (exception == null) {
                    exchange.removeProperty(Exchange.FAILURE_ENDPOINT);
                } else {
                    // set exception back on exchange
                    exchange.setException(exception);
                    exchange.setProperty(Exchange.EXCEPTION_CAUGHT, exception);
                }
                // set fault flag back
                if (fault) {
                    if (exchange.hasOut()) {
                        exchange.getOut().setFault(true);
                    } else {
                        exchange.getIn().setFault(true);
                    }
                }

                if (!doneSync) {
                    // signal callback to continue routing async
                    ExchangeHelper.prepareOutToIn(exchange);
                    LOG.trace("Processing complete for exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);
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
