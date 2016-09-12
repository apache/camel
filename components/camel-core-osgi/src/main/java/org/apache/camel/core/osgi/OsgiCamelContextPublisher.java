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
package org.apache.camel.core.osgi;

import java.util.Dictionary;
import java.util.EventObject;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.management.event.CamelContextStartedEvent;
import org.apache.camel.management.event.CamelContextStoppingEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;

/**
 * This {@link org.apache.camel.spi.EventNotifier} is in charge of registering
 * {@link CamelContext} in the OSGi registry
 */
public class OsgiCamelContextPublisher extends EventNotifierSupport {

    public static final String CONTEXT_SYMBOLIC_NAME_PROPERTY = "camel.context.symbolicname";
    public static final String CONTEXT_VERSION_PROPERTY = "camel.context.version";
    public static final String CONTEXT_NAME_PROPERTY = "camel.context.name";
    public static final String CONTEXT_MANAGEMENT_NAME_PROPERTY = "camel.context.managementname";

    private final BundleContext bundleContext;
    private final Map<CamelContext, ServiceRegistration<?>> registrations 
        = new ConcurrentHashMap<CamelContext, ServiceRegistration<?>>();

    public OsgiCamelContextPublisher(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void notify(EventObject event) throws Exception {
        if (event instanceof CamelContextStartedEvent) {
            CamelContext context = ((CamelContextStartedEvent) event).getContext();
            registerCamelContext(context);
        } else if (event instanceof CamelContextStoppingEvent) {
            CamelContext context = ((CamelContextStoppingEvent) event).getContext();
            ServiceRegistration<?> reg = registrations.remove(context);
            if (reg != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Unregistering CamelContext [{}] from OSGi registry", context.getName());
                }
                try {
                    reg.unregister();
                } catch (Exception e) {
                    log.warn("Error unregistering CamelContext [{}] from OSGi registry. This exception will be ignored.", context.getName(), e);
                }
            }
        }
    }

    public boolean isEnabled(EventObject event) {
        if (event instanceof CamelContextStartedEvent || event instanceof CamelContextStoppingEvent) {
            return true;
        }
        return false;
    }

    @Override
    protected void doStart() throws Exception {
    }

    @Override
    protected void doStop() throws Exception {
    }

    @Override
    protected void doShutdown() throws Exception {
        registrations.clear();
    }

    public ServiceRegistration<?> registerCamelContext(CamelContext camelContext) throws InvalidSyntaxException {
        // avoid registering the same service again
        // we must include unique camel management name so the symbolic name becomes unique,
        // in case the bundle has more than one CamelContext
        String name = camelContext.getName();
        String managementName = camelContext.getManagementName();
        String symbolicName = bundleContext.getBundle().getSymbolicName();

        if (!lookupCamelContext(bundleContext, symbolicName, name)) {
            Version bundleVersion = getBundleVersion(bundleContext.getBundle());

            Dictionary<String, Object > props = new Hashtable<String, Object>();
            props.put(CONTEXT_SYMBOLIC_NAME_PROPERTY, symbolicName);
            props.put(CONTEXT_VERSION_PROPERTY, bundleVersion);
            props.put(CONTEXT_NAME_PROPERTY, name);
            if (managementName != null) {
                props.put(CONTEXT_MANAGEMENT_NAME_PROPERTY, managementName);
            }

            if (log.isDebugEnabled()) {
                log.debug("Registering CamelContext [{}] of in OSGi registry", name);
            }

            ServiceRegistration<?> reg = bundleContext.registerService(CamelContext.class.getName(), camelContext, props);
            if (reg != null) {
                registrations.put(camelContext, reg);
            }
            return reg;
        } else {
            return null;
        }
    }

    public static Version getBundleVersion(Bundle bundle) {
        Dictionary<?, ?> headers = bundle.getHeaders();
        String version = (String) headers.get(Constants.BUNDLE_VERSION);
        return (version != null) ? Version.parseVersion(version) : Version.emptyVersion;
    }

    /**
     * Lookup in the OSGi Service Registry whether a {@link org.apache.camel.CamelContext} is already registered with the given symbolic name.
     *
     * @return <tt>true</tt> if exists, <tt>false</tt> otherwise
     */
    public static boolean lookupCamelContext(BundleContext bundleContext, String symbolicName, String contextName) throws InvalidSyntaxException {
        Version bundleVersion = getBundleVersion(bundleContext.getBundle());
        ServiceReference<?>[] refs = bundleContext.getServiceReferences(CamelContext.class.getName(),
                "(&(" + CONTEXT_SYMBOLIC_NAME_PROPERTY + "=" + symbolicName + ")"
                + "(" + CONTEXT_NAME_PROPERTY + "=" + contextName + ")"
                + "(" + CONTEXT_VERSION_PROPERTY + "=" + bundleVersion + "))");
        return refs != null && refs.length > 0;
    }

}
