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
package org.apache.camel.processor.errorhandler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.ErrorHandler;
import org.apache.camel.support.AsyncCallbackToCompletableFutureAdapter;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.AsyncProcessorHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoErrorHandler extends ErrorHandlerSupport implements AsyncProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(NoErrorHandler.class);

    private final AsyncProcessor output;

    public NoErrorHandler(Processor processor) {
        this.output = AsyncProcessorConverterHelper.convert(processor);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    @Override
    public boolean process(Exchange exchange, final AsyncCallback callback) {
        final Continuation continuation = new Continuation(output, exchange, callback);
        try {
            return output.process(exchange, continuation);
        } catch (Exception e) {
            if (continuation.continued != 0) {
                // the callback was called before the exception was thrown, and it continued the exchange: leave the
                // exception to the caller, as before (the reactive executor logs it; a transacted exchange fails)
                throw e;
            }
            // the processor threw before it called the callback, such as an AsyncProcessor that throws from
            // process(exchange, callback) instead of setting the exception on the exchange: the exception would be
            // dropped by the reactive executor and the exchange would never complete. Fail the exchange through the
            // reactive executor, after the work already queued there, so a callback that the processor handed on and
            // that this queued work calls still comes first and continues the exchange
            exchange.getContext().getCamelContextExtension().getReactiveExecutor()
                    .schedule(() -> continuation.takeOver(e));
            return false;
        }
    }

    /**
     * The callback given to the processor. It and the takeover of a processor that throws before it calls the callback
     * compete for continuing the exchange (compareAndSet), so the exchange is continued exactly once.
     */
    private static final class Continuation implements AsyncCallback {
        private static final AtomicIntegerFieldUpdater<Continuation> CONTINUED
                = AtomicIntegerFieldUpdater.newUpdater(Continuation.class, "continued");

        private final AsyncProcessor output;
        private final Exchange exchange;
        private final AsyncCallback callback;
        // 1 once the exchange is continued (a field, not an object, as this callback is created for every step)
        private volatile int continued;

        Continuation(AsyncProcessor output, Exchange exchange, AsyncCallback callback) {
            this.output = output;
            this.exchange = exchange;
            this.callback = callback;
        }

        @Override
        public void done(boolean doneSync) {
            if (!CONTINUED.compareAndSet(this, 0, 1)) {
                // the logs here do not read the exchange id: it is created on first use, and the exchange may be in
                // use on another thread
                LOG.warn("The callback of {} was called when it was no longer awaited (after the processor had thrown an"
                         + " exception, which failed the exchange instead, or more than once; an AsyncProcessor must not"
                         + " throw from process). This callback is ignored.",
                        output);
                return;
            }
            exchange.getExchangeExtension().setRedeliveryExhausted(false);
            callback.done(doneSync);
        }

        void takeOver(Exception cause) {
            if (CONTINUED.compareAndSet(this, 0, 1)) {
                // fail the exchange, as when the processor sets the exception on the exchange
                exchange.setException(cause);
                exchange.getExchangeExtension().setRedeliveryExhausted(false);
                callback.done(false);
            } else {
                // the queued callback continued the exchange (which may already be done), so this exception is only
                // logged
                LOG.warn("Error processing an exchange by {} due to {}. The exchange continued, so this exception is"
                         + " ignored.",
                        output, cause.getMessage(), cause);
            }
        }
    }

    @Override
    public CompletableFuture<Exchange> processAsync(Exchange exchange) {
        AsyncCallbackToCompletableFutureAdapter<Exchange> callback = new AsyncCallbackToCompletableFutureAdapter<>(exchange);
        process(exchange, callback);
        return callback.getFuture();
    }

    @Override
    public String toString() {
        if (output == null) {
            // if no output then dont do any description
            return "";
        }
        return "NoErrorHandler[" + output + "]";
    }

    @Override
    public boolean supportTransacted() {
        return false;
    }

    @Override
    public Processor getOutput() {
        return output;
    }

    @Override
    public ErrorHandler clone(Processor output) {
        return new NoErrorHandler(output);
    }

    @Override
    protected void doBuild() throws Exception {
        ServiceHelper.buildService(output);
    }

    @Override
    protected void doInit() throws Exception {
        ServiceHelper.initService(output);
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(output);
    }

    @Override
    protected void doStop() throws Exception {
        // noop, do not stop any services which we only do when shutting down
        // as the error handler can be context scoped, and should not stop in case
        // a route stops
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(output);
    }

}
