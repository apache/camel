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
package org.apache.camel.component.stub;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.WaitForTaskToComplete;
import org.apache.camel.component.seda.QueueReference;
import org.apache.camel.component.seda.SedaEndpoint;
import org.apache.camel.component.seda.SedaProducer;

public class StubProducer extends SedaProducer {

    public StubProducer(SedaEndpoint endpoint, WaitForTaskToComplete waitForTaskToComplete, long timeout,
                        boolean blockWhenFull, boolean discardWhenFull, long offerTimeout) {
        super(endpoint, waitForTaskToComplete, timeout, blockWhenFull, discardWhenFull, offerTimeout);
    }

    @Override
    public StubEndpoint getEndpoint() {
        return (StubEndpoint) super.getEndpoint();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        AsyncCallback cb = callback;

        QueueReference queueReference = getEndpoint().getQueueReference();
        boolean empty = queueReference == null || !queueReference.hasConsumers();

        // if no consumers then use InOnly mode
        final ExchangePattern pattern = exchange.getPattern();
        if (empty && pattern != ExchangePattern.InOnly) {
            exchange.setPattern(ExchangePattern.InOnly);
            cb = doneSync -> {
                // and restore the old pattern after processing
                exchange.setPattern(pattern);
                callback.done(doneSync);
            };
        }

        return super.process(exchange, cb);
    }
    
}
