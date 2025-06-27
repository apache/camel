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
package org.apache.camel.component.undertow.handlers;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;

/**
 * Extended PathHandler to monitor add/remove handlers.
 */
public class CamelPathHandler extends PathHandler {
    private final Lock lock = new ReentrantLock();
    private final Map<String, HttpHandler> handlers = new HashMap<>();
    private String handlerString;

    public CamelPathHandler(HttpHandler defaultHandler) {
        super(defaultHandler);
    }

    @Override
    public PathHandler addPrefixPath(final String path, final HttpHandler handler) {
        lock.lock();
        try {
            super.addPrefixPath(path, handler);
            handlers.put(path, handler);
            handlerString = null;
            return this;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PathHandler addExactPath(final String path, final HttpHandler handler) {
        lock.lock();
        try {
            super.addExactPath(path, handler);
            handlers.put(path, handler);
            handlerString = null;
            return this;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PathHandler removePrefixPath(final String path) {
        lock.lock();
        try {
            super.removePrefixPath(path);
            handlers.remove(path);
            handlerString = null;
            return this;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PathHandler removeExactPath(final String path) {
        lock.lock();
        try {
            super.removeExactPath(path);
            handlers.remove(path);
            handlerString = null;
            return this;
        } finally {
            lock.unlock();
        }
    }

    public HttpHandler getHandler(String path) {
        return handlers.get(path);
    }

    public boolean isEmpty() {
        return handlers.isEmpty();
    }

    @Override
    public String toString() {
        if (handlerString == null) {
            handlerString = "CamelPathHandler[" + handlers + "]";
        }
        return handlerString;
    }

}
