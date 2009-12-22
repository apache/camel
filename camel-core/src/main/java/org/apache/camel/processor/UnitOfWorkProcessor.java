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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultUnitOfWork;
import org.apache.camel.spi.RouteContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static org.apache.camel.util.ObjectHelper.wrapRuntimeCamelException;

/** 
 * Handles calling the UnitOfWork.done() method when processing of an exchange
 * is complete.
 */
public final class UnitOfWorkProcessor extends DelegateProcessor {

    private static final transient Log LOG = LogFactory.getLog(UnitOfWorkProcessor.class);
    private final RouteContext routeContext;

    public UnitOfWorkProcessor(Processor processor) {
        this(null, processor);
    }

    public UnitOfWorkProcessor(RouteContext routeContext, Processor processor) {
        super(processor);
        this.routeContext = routeContext;
    }

    @Override
    public String toString() {
        return "UnitOfWork(" + processor + ")";
    }

    @Override
    protected void processNext(Exchange exchange) throws Exception {
        if (exchange.getUnitOfWork() == null) {
            // If there is no existing UoW, then we should start one and
            // terminate it once processing is completed for the exchange.
            final DefaultUnitOfWork uow = new DefaultUnitOfWork(exchange);
            exchange.setUnitOfWork(uow);
            try {
                uow.start();
            } catch (Exception e) {
                throw wrapRuntimeCamelException(e);
            }

            // process the exchange
            try {
                processor.process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            } finally {
                // must always done unit of work
                done(uow, exchange);
            }
        } else {
            // There was an existing UoW, so we should just pass through..
            // so that the guy the initiated the UoW can terminate it.
            processor.process(exchange);
        }
    }

    private void done(DefaultUnitOfWork uow, Exchange exchange) {
        // unit of work is done
        exchange.getUnitOfWork().done(exchange);
        try {
            uow.stop();
        } catch (Exception e) {
            LOG.warn("Exception occurred during stopping UnitOfWork for Exchange: " + exchange
                + ". This exception will be ignored.");
        }
        exchange.setUnitOfWork(null);
    }

}
