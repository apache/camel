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
        // clear exception so finally block can be executed
        final Exception e = exchange.getException();
        exchange.setException(null);
        // but store the caught exception as a property
        if (e != null) {
            exchange.setProperty(Exchange.EXCEPTION_CAUGHT, e);
        }
        // store the last to endpoint as the failure endpoint
        if (exchange.getProperty(Exchange.FAILURE_ENDPOINT) == null) {
            exchange.setProperty(Exchange.FAILURE_ENDPOINT, exchange.getProperty(Exchange.TO_ENDPOINT));
        }

        boolean sync = processor.process(exchange, new AsyncCallback() {
            public void done(boolean doneSync) {
                if (e == null) {
                    exchange.removeProperty(Exchange.FAILURE_ENDPOINT);
                } else {
                    // set exception back on exchange
                    exchange.setException(e);
                    exchange.setProperty(Exchange.EXCEPTION_CAUGHT, e);
                }

                if (!doneSync) {
                    // signal callback to continue routing async
                    ExchangeHelper.prepareOutToIn(exchange);
                    LOG.trace("Processing complete for exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);
                }
                callback.done(doneSync);
            }
        });
        return sync;
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
}
