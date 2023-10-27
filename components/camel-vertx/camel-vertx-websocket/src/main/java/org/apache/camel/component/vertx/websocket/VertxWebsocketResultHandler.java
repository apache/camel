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

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;

/**
 * Handles the result of one or more asynchronous WebSocket write operations
 */
class VertxWebsocketResultHandler {
    private final Exchange exchange;
    private final AsyncCallback callback;
    private final Set<String> connectionKeys;
    private final Map<String, Throwable> errors = new HashMap<>();
    private final Object lock = new Object();

    VertxWebsocketResultHandler(Exchange exchange, AsyncCallback callback, Set<String> connectionKeys) {
        this.exchange = exchange;
        this.callback = callback;
        this.connectionKeys = new HashSet<>(connectionKeys);
    }

    void onResult(String connectionKey) {
        synchronized (lock) {
            connectionKeys.remove(connectionKey);
            if (connectionKeys.isEmpty()) {
                onComplete();
            }
        }
    }

    void onError(String connectionKey, Throwable cause) {
        synchronized (lock) {
            errors.put(connectionKey, cause);
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
        callback.done(false);
    }
}
