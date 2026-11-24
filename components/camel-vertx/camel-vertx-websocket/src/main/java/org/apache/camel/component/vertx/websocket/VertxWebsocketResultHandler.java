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
package org.apache.camel.component.vertx.websocket;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;

/**
 * Handles the result of one or more asynchronous WebSocket write operations
 */
class VertxWebsocketResultHandler {
    private final Vertx vertx;
    private final Exchange exchange;
    private final AsyncCallback callback;
    private final Set<String> connectionKeys;
    private final Map<String, Throwable> errors = new HashMap<>();
    private final Lock lock = new ReentrantLock();

    VertxWebsocketResultHandler(Vertx vertx, Exchange exchange, AsyncCallback callback, Set<String> connectionKeys) {
        this.vertx = vertx;
        this.exchange = exchange;
        this.callback = callback;
        this.connectionKeys = new HashSet<>(connectionKeys);
    }

    void onResult(String connectionKey) {
        boolean complete;
        lock.lock();
        try {
            // a key is removed once, so only the last result completes the exchange
            complete = connectionKeys.remove(connectionKey) && connectionKeys.isEmpty();
        } finally {
            lock.unlock();
        }
        if (complete) {
            onComplete();
        }
    }

    void onError(String connectionKey, Throwable cause) {
        lock.lock();
        try {
            errors.put(connectionKey, cause);
        } finally {
            lock.unlock();
        }
    }

    private void onComplete() {
        if (!errors.isEmpty()) {
            if (errors.size() == 1) {
                final Map.Entry<String, Throwable> entry = errors.entrySet().iterator().next();
                final String msg = "Sending message to WebSocket peer for connection key " + entry.getKey() + " failed";
                exchange.setException(new CamelExecutionException(msg, exchange, entry.getValue()));
            } else {
                final StringBuilder msg = new StringBuilder("Sending message to multiple WebSocket peers failed:");
                for (Map.Entry<String, Throwable> entry : errors.entrySet()) {
                    msg.append("\n  connection key: ")
                            .append(entry.getKey())
                            .append(", cause: ")
                            .append(entry.getValue().getMessage());
                }
                exchange.setException(new CamelExecutionException(msg.toString(), exchange));
            }
        }
        if (Context.isOnEventLoopThread()) {
            // a write completes on the Vert.x event loop: the route must not continue there, as a blocking step
            // stalls the I/O of that event loop, and a producer that connects to another server waits for a connect
            // that needs this very event loop (CAMEL-27507)
            completeOnWorkerThread();
        } else {
            callback.done(false);
        }
    }

    private void completeOnWorkerThread() {
        // the exchange is completed once: by the task, or here when the worker pool refuses the task
        AtomicBoolean completed = new AtomicBoolean();
        try {
            vertx.executeBlocking((Callable<Void>) () -> {
                complete(completed);
                return null;
            }, false).onFailure(e -> complete(completed));
        } catch (RejectedExecutionException e) {
            // the worker pool of the Vert.x is already shut down (for example while the Vert.x closes): the message
            // was sent, so complete the exchange on this thread rather than lose it
            complete(completed);
        }
    }

    private void complete(AtomicBoolean completed) {
        if (completed.compareAndSet(false, true)) {
            callback.done(false);
        }
    }
}
