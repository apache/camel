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
package org.apache.camel.component.netty;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;

/**
 * Stores state for {@link NettyProducer} when sending messages.
 * <p/>
 * This allows the {@link org.apache.camel.component.netty.handlers.ClientChannelHandler} to access this state, which is
 * needed so we can get hold of the current {@link Exchange} and the {@link AsyncCallback} so we can continue routing
 * the message in the Camel routing engine.
 */
public final class NettyCamelState {

    private final Exchange exchange;
    private final AsyncCallback callback;
    // It is never a good idea to call the same callback twice
    private final AtomicBoolean callbackCalled;
    private final AtomicBoolean exceptionCaught;

    public NettyCamelState(AsyncCallback callback, Exchange exchange) {
        this.callback = callback;
        this.exchange = exchange;
        this.callbackCalled = new AtomicBoolean();
        this.exceptionCaught = new AtomicBoolean();
    }

    public AsyncCallback getCallback() {
        return callback;
    }

    public boolean isDone() {
        return callbackCalled.get();
    }

    public void callbackDoneOnce(boolean doneSync) {
        if (!callbackCalled.getAndSet(true)) {
            // this is the first time we call the callback
            callback.done(doneSync);
        }
    }

    public Exchange getExchange() {
        return exchange;
    }

    public void onExceptionCaught() {
        exceptionCaught.set(true);
    }

    public void onExceptionCaughtOnce(boolean doneSync) {
        // only trigger callback once if an exception has not already been caught
        // (ClientChannelHandler#exceptionCaught vs NettyProducer#processWithConnectedChannel)
        if (exceptionCaught.compareAndSet(false, true)) {
            // set some general exception as Camel should know the netty write operation failed
            if (exchange.getException() == null) {
                exchange.setException(new IOException("Netty write operation failed"));
            }
            callbackDoneOnce(doneSync);
        }
    }
}
