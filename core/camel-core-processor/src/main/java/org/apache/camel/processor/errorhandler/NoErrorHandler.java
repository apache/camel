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

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.ErrorHandler;
import org.apache.camel.support.AsyncCallbackToCompletableFutureAdapter;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.AsyncProcessorHelper;
import org.apache.camel.support.service.ServiceHelper;

public class NoErrorHandler extends ErrorHandlerSupport implements AsyncProcessor, ErrorHandler {

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
        return output.process(exchange, new AsyncCallback() {
            @Override
            public void done(boolean doneSync) {
                exchange.getExchangeExtension().setRedeliveryExhausted(false);
                callback.done(doneSync);
            }
        });
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
