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
package org.apache.camel.util;

import java.util.concurrent.CountDownLatch;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.spi.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper methods for {@link AsyncProcessor} objects.
 */
public final class AsyncProcessorHelper {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncProcessorHelper.class);

    private AsyncProcessorHelper() {
        // utility class
    }

    /**
     * Calls the async version of the processor's process method.
     * <p/>
     * This implementation supports transacted {@link Exchange}s which ensure those are run in a synchronous fashion.
     * See more details at {@link org.apache.camel.AsyncProcessor}.
     *
     * @param processor the processor
     * @param exchange  the exchange
     * @param callback  the callback
     * @return <tt>true</tt> to continue execute synchronously, <tt>false</tt> to continue being executed asynchronously
     * @deprecated should no longer be needed, instead invoke the process method on the {@link AsyncProcessor} directly,
     * instead of using this method.
     */
    @Deprecated
    public static boolean process(final AsyncProcessor processor, final Exchange exchange, final AsyncCallback callback) {
        boolean sync;

        if (exchange.isTransacted()) {
            // must be synchronized for transacted exchanges
            LOG.trace("Transacted Exchange must be routed synchronously for exchangeId: {} -> {}", exchange.getExchangeId(), exchange);
            try {
                process(processor, exchange);
            } catch (Throwable e) {
                exchange.setException(e);
            }
            callback.done(true);
            sync = true;
        } else {
            final UnitOfWork uow = exchange.getUnitOfWork();

            // allow unit of work to wrap callback in case it need to do some special work
            // for example the MDCUnitOfWork
            AsyncCallback async = callback;
            if (uow != null) {
                async = uow.beforeProcess(processor, exchange, callback);
            }

            // we support asynchronous routing so invoke it
            sync = processor.process(exchange, async);

            // execute any after processor work (in current thread, not in the callback)
            if (uow != null) {
                uow.afterProcess(processor, exchange, callback, sync);
            }
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Exchange processed and is continued routed {} for exchangeId: {} -> {}",
                    new Object[]{sync ? "synchronously" : "asynchronously", exchange.getExchangeId(), exchange});
        }
        return sync;
    }

    /**
     * Calls the async version of the processor's process method and waits
     * for it to complete before returning. This can be used by {@link AsyncProcessor}
     * objects to implement their sync version of the process method.
     * <p/>
     * <b>Important:</b> This method is discouraged to be used, as its better to invoke the asynchronous
     * {@link AsyncProcessor#process(org.apache.camel.Exchange, org.apache.camel.AsyncCallback)} method, whenever possible.
     *
     * @param processor the processor
     * @param exchange  the exchange
     * @throws Exception can be thrown if waiting is interrupted
     */
    public static void process(final AsyncProcessor processor, final Exchange exchange) throws Exception {
        final AsyncProcessorAwaitManager awaitManager = exchange.getContext().getAsyncProcessorAwaitManager();

        final CountDownLatch latch = new CountDownLatch(1);
        boolean sync = processor.process(exchange, new AsyncCallback() {
            public void done(boolean doneSync) {
                if (!doneSync) {
                    awaitManager.countDown(exchange, latch);
                }
            }

            @Override
            public String toString() {
                return "Done " + processor;
            }
        });
        if (!sync) {
            awaitManager.await(exchange, latch);
        }
    }

}
