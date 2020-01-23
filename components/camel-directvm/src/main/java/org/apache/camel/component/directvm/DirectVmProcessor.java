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
package org.apache.camel.component.directvm;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Processor;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DirectVmProcessor extends DelegateAsyncProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(DirectVmProcessor.class);
    private final DirectVmEndpoint endpoint;

    public DirectVmProcessor(Processor processor, DirectVmEndpoint endpoint) {
        super(processor);
        this.endpoint = endpoint;
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        // need to use a copy of the incoming exchange, so we route using this camel context
        final Exchange copy = prepareExchange(exchange);

        ClassLoader current = Thread.currentThread().getContextClassLoader();
        boolean changed = false;
        try {
            // set TCCL to application context class loader if given
            ClassLoader appClassLoader = endpoint.getCamelContext().getApplicationContextClassLoader();
            if (appClassLoader != null) {
                LOG.trace("Setting Thread ContextClassLoader to {}", appClassLoader);
                Thread.currentThread().setContextClassLoader(appClassLoader);
                changed = true;
            }
            
            final boolean chgd = changed;
            return processor.process(copy, new AsyncCallback() {
                @Override
                public void done(boolean done) {
                    try {
                        // restore TCCL if it was changed during processing
                        if (chgd) {
                            LOG.trace("Restoring Thread ContextClassLoader to {}", current);
                            Thread.currentThread().setContextClassLoader(current);
                        }
                        // make sure to copy results back
                        ExchangeHelper.copyResults(exchange, copy);
                    } finally {
                        // must call callback when we are done
                        callback.done(done);
                    }
                }
            });
        } finally {
            // restore TCCL if it was changed during processing
            if (changed) {
                LOG.trace("Restoring Thread ContextClassLoader to {}", current);
                Thread.currentThread().setContextClassLoader(current);
            }
        }
    }

    /**
     * Strategy to prepare exchange for being processed by this consumer
     *
     * @param exchange the exchange
     * @return the exchange to process by this consumer.
     */
    protected Exchange prepareExchange(Exchange exchange) {
        // send a new copied exchange with new camel context (do not handover completions)
        Exchange newExchange = ExchangeHelper.copyExchangeAndSetCamelContext(exchange, endpoint.getCamelContext(), false);
        // set the from endpoint
        newExchange.adapt(ExtendedExchange.class).setFromEndpoint(endpoint);
        // The StreamCache created by the child routes must not be 
        // closed by the unit of work of the child route, but by the unit of 
        // work of the parent route or grand parent route or grand grand parent route ...(in case of nesting).
        // Set therefore the unit of work of the  parent route as stream cache unit of work, 
        // if it is not already set.
        if (newExchange.getProperty(Exchange.STREAM_CACHE_UNIT_OF_WORK) == null) {
            newExchange.setProperty(Exchange.STREAM_CACHE_UNIT_OF_WORK, exchange.getUnitOfWork());
        }
        return newExchange;
    }

    @Override
    public String toString() {
        return "DirectVm[" + processor + "]";
    }
}
