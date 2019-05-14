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
package org.apache.camel.impl.engine;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.spi.ClaimCheckRepository;

/**
 * The default {@link ClaimCheckRepository} implementation that is an in-memory storage.
 */
public class DefaultClaimCheckRepository implements ClaimCheckRepository {

    private final Map<String, Exchange> map = new HashMap<>();
    private final Deque<Exchange> stack = new ArrayDeque<>();

    @Override
    public boolean add(String key, Exchange exchange) {
        return map.put(key, exchange) == null;
    }

    @Override
    public boolean contains(String key) {
        return map.containsKey(key);
    }

    @Override
    public Exchange get(String key) {
        return map.get(key);
    }

    @Override
    public Exchange getAndRemove(String key) {
        return map.remove(key);
    }

    @Override
    public void push(Exchange exchange) {
        stack.push(exchange);
    }

    @Override
    public Exchange pop() {
        if (!stack.isEmpty()) {
            return stack.pop();
        } else {
            return null;
        }
    }

    @Override
    public void clear() {
        map.clear();
        stack.clear();
    }

    @Override
    public void start() {
        // noop
    }

    @Override
    public void stop() {
        // noop
    }
}
