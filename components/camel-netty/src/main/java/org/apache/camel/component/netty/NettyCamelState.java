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
        if (markDone()) {
            // this is the first time we call the callback
            callback.done(doneSync);
        }
    }

    /**
     * Claims the completion of the exchange.
     *
     * @return <tt>true</tt> for the first caller only, which must then set the outcome on the exchange and call the
     *         callback, <tt>false</tt> if the exchange is already completed and must not be touched anymore
     */
    public boolean markDone() {
        return callbackCalled.compareAndSet(false, true);
    }

    public Exchange getExchange() {
        return exchange;
    }

    public void onExceptionCaught() {
        exceptionCaught.set(true);
    }

    public void onExceptionCaughtOnce(boolean doneSync) {
        onExceptionCaughtOnce(doneSync, null);
    }

    /**
     * Completes the exchange with the cause of a failed write, unless an exception has already been caught or the
     * exchange has already been completed, for example by the timeout of a correlation manager.
     */
    public void onExceptionCaughtOnce(boolean doneSync, Throwable cause) {
        // only trigger callback once if an exception has not already been caught
        // (ClientChannelHandler#exceptionCaught vs NettyProducer#processWithConnectedChannel)
        if (exceptionCaught.compareAndSet(false, true) && markDone()) {
            if (cause != null) {
                exchange.setException(cause);
            } else if (exchange.getException() == null) {
                // set some general exception as Camel should know the netty write operation failed
                exchange.setException(new IOException("Netty write operation failed"));
            }
            callback.done(doneSync);
        }
    }
}
