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
package org.apache.camel.component.rabbitmq.reply;

import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.support.DefaultTimeoutMap;

import static org.apache.camel.TimeoutMap.Listener.Type.*;

/**
 * A {@link org.apache.camel.TimeoutMap} which is used to track reply messages
 * which has been timed out, and thus should trigger the waiting
 * {@link org.apache.camel.Exchange} to timeout as well.
 */
class CorrelationTimeoutMap extends DefaultTimeoutMap<String, ReplyHandler> {

    CorrelationTimeoutMap(ScheduledExecutorService executor, long requestMapPollTimeMillis) {
        super(executor, requestMapPollTimeMillis);
        addListener(this::listener);
    }

    private static long encode(long timeoutMillis) {
        return timeoutMillis > 0 ? timeoutMillis : Integer.MAX_VALUE; // TODO
                                                                      // why not
                                                                      // Long.MAX_VALUE!
    }

    private void listener(Listener.Type type, String key, ReplyHandler handler) {
        if (type == Put) {
            log.trace("Added correlationID: {}", key);
        } else if (type == Remove) {
            log.trace("Removed correlationID: {}", key);
        } else if (type == Evict) {
            try {
                handler.onTimeout(key);
            } catch (Throwable e) {
                // must ignore so we ensure we evict the element
                log.warn("Error processing onTimeout for correlationID: " + key + " due: " + e.getMessage() + ". This exception is ignored.", e);
            }
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
