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
package org.apache.camel.impl;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.CamelContextRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default {@link CamelContextRegistry}
 */
public final class DefaultCamelContextRegistry implements CamelContextRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultCamelContextRegistry.class);

    private final Set<CamelContext> contexts = new LinkedHashSet<CamelContext>();
    private final Set<Listener> listeners = new LinkedHashSet<Listener>();

    synchronized void afterCreate(CamelContext camelContext) {
        registerContext(camelContext);
    }

    synchronized void beforeStart(CamelContext camelContext) {
        if (!contexts.contains(camelContext)) {
            registerContext(camelContext);
        }
    }

    synchronized void afterStop(CamelContext camelContext) {
        unregisterContext(camelContext);
    }

    private void registerContext(CamelContext camelContext) {
        contexts.add(camelContext);
        for (Listener listener : listeners) {
            try {
                listener.contextAdded(camelContext);
            } catch (Throwable e) {
                LOG.warn("Error calling registry listener. This exception is ignored.", e);
            }
        }
    }

    private void unregisterContext(CamelContext camelContext) {
        contexts.remove(camelContext);
        for (Listener listener : listeners) {
            try {
                listener.contextRemoved(camelContext);
            } catch (Throwable e) {
                LOG.warn("Error calling registry listener. This exception is ignored.", e);
            }
        }
    }

    @Override
    public synchronized void addListener(Listener listener, boolean withCallback) {
        if (withCallback) {
            for (CamelContext ctx : contexts) {
                listener.contextAdded(ctx);
            }
        }
        listeners.add(listener);
    }

    @Override
    public synchronized void removeListener(Listener listener, boolean withCallback) {
        listeners.add(listener);
        if (withCallback) {
            for (CamelContext ctx : contexts) {
                listener.contextAdded(ctx);
            }
        }
    }

    @Override
    public synchronized Set<CamelContext> getContexts() {
        return new LinkedHashSet<CamelContext>(contexts);
    }

    @Override
    public synchronized Set<CamelContext> getContexts(String name) {
        Set<CamelContext> result = new LinkedHashSet<CamelContext>();
        for (CamelContext ctx : contexts) {
            if (ctx.getName().equals(name)) {
                result.add(ctx);
            }
        }
        return result;
    }

    @Override
    public synchronized CamelContext getRequiredContext(String name) {
        Iterator<CamelContext> it = getContexts(name).iterator();
        if (!it.hasNext()) {
            throw new IllegalStateException("Cannot find CamelContext with name: " + name);
        }
        return it.next();
    }

    @Override
    public synchronized CamelContext getContext(String name) {
        Iterator<CamelContext> it = getContexts(name).iterator();
        return it.hasNext() ? it.next() : null;
    }

}
