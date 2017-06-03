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
package org.apache.camel.component.sjms.consumer;

import java.util.concurrent.ExecutorService;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.sjms.SjmsEndpoint;
import org.apache.camel.spi.Synchronization;

/**
 * An InOnly {@link AbstractMessageHandler}
 */
public class InOnlyMessageHandler extends AbstractMessageHandler {

    /**
     * @param endpoint
     * @param executor
     */
    public InOnlyMessageHandler(SjmsEndpoint endpoint, ExecutorService executor) {
        super(endpoint, executor);
    }

    /**
     * @param endpoint
     * @param executor
     * @param synchronization
     */
    public InOnlyMessageHandler(SjmsEndpoint endpoint, ExecutorService executor, Synchronization synchronization) {
        super(endpoint, executor, synchronization);
    }

    /**
     * @param exchange
     */
    @Override
    public void handleMessage(final Exchange exchange) {
        if (log.isDebugEnabled()) {
            log.debug("Handling InOnly Message: {}", exchange.getIn().getBody());
        }
        if (!exchange.isFailed()) {
            NoOpAsyncCallback callback = new NoOpAsyncCallback();
            if (isTransacted() || isSynchronous()) {
                // must process synchronous if transacted or configured to
                // do so
                if (log.isDebugEnabled()) {
                    log.debug("Synchronous processing: Message[{}], Destination[{}] ", exchange.getIn().getBody(), getEndpoint().getEndpointUri());
                }
                try {
                    getProcessor().process(exchange);
                } catch (Exception e) {
                    exchange.setException(e);
                } finally {
                    callback.done(true);
                }
            } else {
                // process asynchronous using the async routing engine
                log.debug("Asynchronous processing: Message[{}], Destination[{}] ", exchange.getIn().getBody(), getEndpoint().getEndpointUri());

                getProcessor().process(exchange, callback);
            }
        }
    }

    @Override
    public void close() {
        // no-op
    }

    protected class NoOpAsyncCallback implements AsyncCallback {

        public NoOpAsyncCallback() {
        }

        @Override
        public void done(boolean sync) {
            log.debug("NoOpAsyncCallback InOnly Exchange complete");
        }
    }
}
