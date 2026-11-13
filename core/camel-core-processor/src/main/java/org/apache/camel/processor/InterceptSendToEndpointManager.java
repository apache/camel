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
package org.apache.camel.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.spi.InterceptSendToEndpoint;
import org.apache.camel.spi.NormalizedEndpointUri;
import org.apache.camel.support.DefaultInterceptSendToEndpoint;
import org.apache.camel.support.DefaultInterceptSendToEndpoint.Interceptor;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.URISupport;

/**
 * Manages the interceptors of the intercept send to endpoint EIP for a {@link CamelContext}.
 * <p>
 * Each endpoint that is intercepted is wrapped once in a {@link DefaultInterceptSendToEndpoint}, and the routes
 * register and unregister their interceptors on it (when the route starts and stops). The wrapped endpoint (and the
 * producers created from it) stay the same, so removing a route does not affect the other routes that send to the
 * endpoint.
 */
public final class InterceptSendToEndpointManager implements EndpointStrategy {

    private record Registration(String matchUri, Interceptor interceptor) {
    }

    private final CamelContext camelContext;
    private final List<Registration> registrations = new CopyOnWriteArrayList<>();
    // the uri patterns of the endpoints to wrap (null to wrap all)
    private final List<String> patterns = new CopyOnWriteArrayList<>();
    private volatile boolean wrapAll;
    // the endpoints this manager wrapped, with the uri they were wrapped for. It includes the endpoints that are being
    // created and are not in the endpoint registry yet. Weak keys, as an endpoint can be evicted from the registry (toD).
    private final Map<DefaultInterceptSendToEndpoint, String> wrappedEndpoints = new WeakHashMap<>();
    // addPattern, register and unregister take the write lock. Creating an endpoint takes the read lock, so endpoints
    // can be created in parallel (a prototype toD creates one per exchange); the puts to wrappedEndpoints are
    // synchronized between them. The write lock excludes the readers, so iterating wrappedEndpoints needs no monitor.
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    private InterceptSendToEndpointManager(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    /**
     * Gets (or creates) the manager of the CamelContext
     */
    public static InterceptSendToEndpointManager getOrCreate(CamelContext camelContext) {
        InterceptSendToEndpointManager answer
                = camelContext.getCamelContextExtension().getContextPlugin(InterceptSendToEndpointManager.class);
        if (answer == null) {
            answer = new InterceptSendToEndpointManager(camelContext);
            camelContext.getCamelContextExtension().addContextPlugin(InterceptSendToEndpointManager.class, answer);
            camelContext.getCamelContextExtension().registerEndpointCallback(answer);
        }
        return answer;
    }

    /**
     * Adds the uri pattern (null to match all) of the endpoints to wrap, so the interceptors of routes can be
     * registered on them. This must be done when the route is created (before its endpoints are resolved), as the
     * producers of the route are created from the (wrapped) endpoints.
     */
    public void addPattern(String matchUri) {
        writeLock.lock();
        try {
            if (matchUri == null) {
                if (wrapAll) {
                    return;
                }
                wrapAll = true;
            } else if (patterns.contains(matchUri)) {
                return;
            } else {
                patterns.add(matchUri);
            }
            wrapExistingEndpoints();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Registers the interceptor for the endpoints that match the uri (null to match all)
     */
    public void register(String matchUri, Interceptor interceptor) {
        writeLock.lock();
        try {
            Registration registration = new Registration(matchUri, interceptor);
            if (registrations.contains(registration)) {
                return;
            }
            registrations.add(registration);
            wrapExistingEndpoints();
            // and the wrapped endpoints that are not in the registry (yet)
            for (Map.Entry<DefaultInterceptSendToEndpoint, String> entry : wrappedEndpoints.entrySet()) {
                if (matchUri == null || matchPattern(entry.getValue(), matchUri)) {
                    entry.getKey().addInterceptor(interceptor);
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    private void wrapExistingEndpoints() {
        List<Map.Entry<NormalizedEndpointUri, Endpoint>> replace = new ArrayList<>();
        for (Map.Entry<NormalizedEndpointUri, Endpoint> entry : camelContext.getEndpointRegistry().entrySet()) {
            Endpoint endpoint = entry.getValue();
            Endpoint answer = registerEndpoint(endpoint.getEndpointUri(), endpoint);
            if (answer != endpoint) {
                replace.add(Map.entry(entry.getKey(), answer));
            }
        }
        for (Map.Entry<NormalizedEndpointUri, Endpoint> entry : replace) {
            camelContext.getEndpointRegistry().put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Unregisters the interceptor. The endpoints stay wrapped (as producers may use them), but no longer use the
     * interceptor.
     */
    public void unregister(Interceptor interceptor) {
        writeLock.lock();
        try {
            registrations.removeIf(r -> r.interceptor().equals(interceptor));
            for (Endpoint endpoint : camelContext.getEndpointRegistry().values()) {
                if (endpoint instanceof DefaultInterceptSendToEndpoint dise) {
                    dise.removeInterceptor(interceptor);
                }
            }
            // and the endpoints that are wrapped but not in the registry (yet)
            for (DefaultInterceptSendToEndpoint dise : wrappedEndpoints.keySet()) {
                dise.removeInterceptor(interceptor);
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Endpoint registerEndpoint(String uri, Endpoint endpoint) {
        if (!wrapAll && patterns.isEmpty()) {
            return endpoint;
        }
        if (endpoint instanceof InterceptSendToEndpoint && !(endpoint instanceof DefaultInterceptSendToEndpoint)) {
            // endpoint decorated by a custom interceptor
            return endpoint;
        }
        if (!(endpoint instanceof DefaultInterceptSendToEndpoint) && !matchesAnyPattern(uri)) {
            return endpoint;
        }
        // the registrations are read and the wrapper is remembered under the read lock, so a route that starts or stops
        // meanwhile sees this endpoint in wrappedEndpoints (it is not in the registry yet)
        readLock.lock();
        try {
            return doRegisterEndpoint(uri, endpoint);
        } finally {
            readLock.unlock();
        }
    }

    private Endpoint doRegisterEndpoint(String uri, Endpoint endpoint) {
        DefaultInterceptSendToEndpoint wrapped = endpoint instanceof DefaultInterceptSendToEndpoint dise ? dise : null;
        if (wrapped == null) {
            InterceptSendToEndpoint answer = PluginHelper.getInterceptEndpointFactory(camelContext)
                    .createInterceptSendToEndpoint(camelContext, endpoint, false, null, null, null);
            if (!(answer instanceof DefaultInterceptSendToEndpoint dise)) {
                // a custom factory that does not support the interceptors of routes
                return endpoint;
            }
            wrapped = dise;
        }
        // add the interceptors of the running routes that match
        for (Registration registration : registrations) {
            if (registration.matchUri() == null || matchPattern(uri, registration.matchUri())) {
                wrapped.addInterceptor(registration.interceptor());
            }
        }
        // remember it, so the routes that start or stop before it is in the registry update it as well
        synchronized (wrappedEndpoints) {
            wrappedEndpoints.putIfAbsent(wrapped, uri);
        }
        return wrapped;
    }

    private boolean matchesAnyPattern(String uri) {
        if (wrapAll) {
            return true;
        }
        for (String pattern : patterns) {
            if (matchPattern(uri, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Does the uri match the pattern.
     *
     * @param  uri     the uri
     * @param  pattern the pattern, which can be an endpoint uri as well
     * @return         <code>true</code> if matched and we should intercept, <code>false</code> if not matched, and not
     *                 intercept.
     */
    private boolean matchPattern(String uri, String pattern) {
        // match using the pattern as-is
        boolean match = EndpointHelper.matchEndpoint(camelContext, uri, pattern);
        if (!match) {
            try {
                // the pattern could be an uri, so we need to normalize it
                // before matching again
                pattern = URISupport.normalizeUri(pattern);
                match = EndpointHelper.matchEndpoint(camelContext, uri, pattern);
            } catch (Exception e) {
                // ignore
            }
        }
        return match;
    }
}
