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
package org.apache.camel.component.jms.reply;

import java.util.concurrent.ExecutorService;

import org.apache.camel.TimeoutMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link org.apache.camel.TimeoutMap} which is used to track reply messages which
 * has been timed out, and thus should trigger the waiting {@link org.apache.camel.Exchange} to
 * timeout as well. Zero (or negative) timeout means infinite but is actually encoded as {@link Integer#MAX_VALUE}
 * which is 24 days.
 */
public class CorrelationTimeoutMap implements TimeoutMap<String, ReplyHandler> {

    private static final Logger log = LoggerFactory.getLogger(CorrelationTimeoutMap.class);
    private final ExecutorService executorService;
    private final TimeoutMap<String, ReplyHandler> delegate;

    public CorrelationTimeoutMap(TimeoutMap<String, ReplyHandler> delegate, ExecutorService executorService) {
        this.delegate = delegate;
        delegate.addListener(this::onEviction);
        this.executorService = executorService;
    }

    private static long encode(long timeoutMillis) {
        return timeoutMillis > 0 ? timeoutMillis : Integer.MAX_VALUE; // TODO why not Long.MAX_VALUE!
    }

    private void onEviction(Listener.Type type, String key, ReplyHandler handler) {
        if (type == Listener.Type.Evict) {
            if (executorService != null) {
                executorService.submit(() -> handler.onTimeout(key));
            } else {
                // run task synchronously
                handler.onTimeout(key);
            }
            log.trace("Evicted correlationID: {}", key);
        }
    }

    @Override
    public ReplyHandler get(String key) {
        ReplyHandler answer = delegate.get(key);
        log.trace("Get correlationID: {} -> {}", key, answer != null);
        return answer;
    }

    @Override
    public ReplyHandler put(String key, ReplyHandler value, long timeoutMillis) {
        ReplyHandler result = delegate.put(key, value, encode(timeoutMillis));
        log.trace("Added correlationID: {} to timeout after: {} millis", key, timeoutMillis);
        return result;
    }

    @Override
    public ReplyHandler putIfAbsent(String key, ReplyHandler value, long timeoutMillis) {
        ReplyHandler result = delegate.putIfAbsent(key, value, encode(timeoutMillis));
        if (result == null) {
            log.trace("Added correlationID: {} to timeout after: {} millis", key, timeoutMillis);
        } else {
            log.trace("Duplicate correlationID: {} detected", key);
        }
        return result;
    }

    @Override
    public ReplyHandler remove(String key) {
        ReplyHandler answer = delegate.remove(key);
        log.trace("Removed correlationID: {} -> {}", key, answer != null);
        return answer;
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public void addListener(Listener<String, ReplyHandler> listener) {
        delegate.addListener(listener);
    }

    @Override
    public void start() throws Exception {
        delegate.start();
    }

    @Override
    public void stop() throws Exception {
        delegate.stop();
    }
}
