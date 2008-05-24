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
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultUnitOfWork;

/** 
 * Handles calling the UnitOfWork.done() method when processing of an exchange
 * is complete.
 */
public final class UnitOfWorkProcessor extends DelegateAsyncProcessor {

    public UnitOfWorkProcessor(AsyncProcessor processor) {
        super(processor);
    }
    
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        if (exchange.getUnitOfWork() == null) {
            // If there is no existing UoW, then we should start one and
            // terminate it once processing is completed for the exchange.
            exchange.setUnitOfWork(new DefaultUnitOfWork());
            return processor.process(exchange, new AsyncCallback() {
                public void done(boolean sync) {
                    // Order here matters. We need to complete the callbacks
                    // since they will likely update the exchange with 
                    // some final results.
                    callback.done(sync);
                    exchange.getUnitOfWork().done(exchange);
                    exchange.setUnitOfWork(null);
                }
            });
        } else {
            // There was an existing UoW, so we should just pass through..
            // so that the guy the initiated the UoW can terminate it.
            return processor.process(exchange, callback);
        }
    }

}