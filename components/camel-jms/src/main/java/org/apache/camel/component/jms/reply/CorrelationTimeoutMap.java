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
package org.apache.camel.component.jms.reply;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;

import org.apache.camel.support.DefaultTimeoutMap;

import static org.apache.camel.TimeoutMap.Listener.Type.*;

/**
 * A {@link org.apache.camel.TimeoutMap} which is used to track reply messages which
 * has been timed out, and thus should trigger the waiting {@link org.apache.camel.Exchange} to
 * timeout as well. Zero (or negative) timeout means infinite but is actually encoded as {@link Integer#MAX_VALUE}
 * which is 24 days.
 */
class CorrelationTimeoutMap extends DefaultTimeoutMap<String, ReplyHandler> {

    private final BiConsumer<ReplyHandler, String> evictionTask;

    CorrelationTimeoutMap(ScheduledExecutorService executor, long requestMapPollTimeMillis, ExecutorService executorService) {
        super(executor, requestMapPollTimeMillis);
        // Support synchronous or asynchronous handling of evictions
        evictionTask = executorService == null
                ? ReplyHandler::onTimeout
                : (handler, key) -> executorService.submit(() -> handler.onTimeout(key));
        addListener(this::listener);
    }

    private static long encode(long timeoutMillis) {
        return timeoutMillis > 0 ? timeoutMillis : Integer.MAX_VALUE; // TODO why not Long.MAX_VALUE!
    }

    private void listener(Listener.Type type, String key, ReplyHandler handler) {
        if (type == Put) {
            log.trace("Added correlationID: {}", key);
        } else if (type == Remove) {
            log.trace("Removed correlationID: {}", key);
        } else if (type == Evict) {
            evictionTask.accept(handler, key);
            log.trace("Evicted correlationID: {}", key);
        }
    }

    @Override
    public ReplyHandler put(String key, ReplyHandler value, long timeoutMillis) {
        return super.put(key, value, encode(timeoutMillis));
    }

    @Override
    public ReplyHandler putIfAbsent(String key, ReplyHandler value, long timeoutMillis) {
        return super.putIfAbsent(key, value, encode(timeoutMillis));
    }

}
