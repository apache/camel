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
package org.apache.camel.blueprint;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used by {@link BlueprintCamelContext} to inform about state of Camel context. If running inside Karaf
 * and Karaf's BundleStateService is accessible, Camel context state will propagate as <em>extended
 * bundle state</em>.
 */
public class BlueprintCamelStateService {

    public static final Logger LOG = LoggerFactory.getLogger(BlueprintCamelStateService.class);

    public enum State {
        Starting,
        Active,
        Failure
    }

    private Map<String, State> states;
    private Map<String, Throwable> exceptions;

    private BundleContext bundleContext;

    private ServiceRegistration<?> registration;
    public BundleContext getBundleContext() {
        return bundleContext;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    /**
     * One of four {@link State states} is set for given {@link org.osgi.framework.Bundle} and context Id.
     * One (blueprint) bundle may declare one or more Camel context.
     * @param contextId
     * @param state
     */
    public void setBundleState(Bundle bundle, String contextId, State state) {
        setBundleState(bundle, contextId, state, null);
    }

    /**
     * One of four {@link State states} is set for given {@link org.osgi.framework.Bundle} and context Id.
     * One (blueprint) bundle may declare one or more Camel context.
     * @param contextId
     * @param state
     * @param t
     */
    public void setBundleState(Bundle bundle, String contextId, State state, Throwable t) {
        if (state == State.Failure) {
            LOG.warn("Changing Camel state for bundle {} to {}", bundle.getBundleId(), state);
        } else if (LOG.isDebugEnabled()) {
            LOG.debug("Changing Camel state for bundle {} to {}", bundle.getBundleId(), state);
        }

        String key = String.format("%d:%s", bundle.getBundleId(), contextId);
        if (state != null) {
            states.put(key, state);
        } else {
            states.remove(key);
        }
        if (t != null) {
            exceptions.put(key, t);
        } else {
            exceptions.remove(key);
        }
    }

    /**
     * Get states for all context registered for given {@link Bundle}
     * @param bundle
     * @return
     */
    public List<State> getStates(Bundle bundle) {
        List<State> result = new LinkedList<>();
        for (Map.Entry<String, State> e : states.entrySet()) {
            if (e.getKey().startsWith(bundle.getBundleId() + ":")) {
                result.add(e.getValue());
            }
        }
        return result;
    }

    /**
     * Get exceptions for all camel contexts for given bundle
     * @param bundle
     * @return
     */
    public Map<String, Throwable> getExceptions(Bundle bundle) {
        Map<String, Throwable> result = new LinkedHashMap<>();
        for (Map.Entry<String, Throwable> e : exceptions.entrySet()) {
            if (e.getKey().startsWith(bundle.getBundleId() + ":")) {
                result.put(e.getKey().substring(e.getKey().indexOf(":") + 1), e.getValue());
            }
        }
        return result;
    }

    /**
     * Attempts to register Karaf-specific BundleStateService - if possible
     */
    public void init() {
        try {
            states = new ConcurrentHashMap<>();
            exceptions = new ConcurrentHashMap<>();

            registration = new KarafBundleStateServiceCreator().create(bundleContext, this);
        } catch (NoClassDefFoundError e) {
            LOG.info("Karaf BundleStateService not accessible. Bundle state won't reflect Camel context state");
        }
    }

    /**
     * Unregisters any OSGi service registered
     */
    public void destroy() {
        if (registration != null) {
            registration.unregister();
        }
        states.clear();
        states = null;
        exceptions.clear();
        exceptions = null;
    }

    /**
     * Static creator to decouple from optional Karaf classes.
     */
    private static class KarafBundleStateServiceCreator {
        public ServiceRegistration<?> create(BundleContext context, BlueprintCamelStateService camelStateService) {
            KarafBundleStateService karafBundleStateService = new KarafBundleStateService(camelStateService);
            return karafBundleStateService.register(context);
        }
    }

}
