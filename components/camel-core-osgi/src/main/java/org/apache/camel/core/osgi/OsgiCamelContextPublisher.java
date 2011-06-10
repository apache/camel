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
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.management.EventNotifierSupport;
import org.apache.camel.management.event.CamelContextStartedEvent;
import org.apache.camel.management.event.CamelContextStoppingEvent;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;

/**
 * This {@link org.apache.camel.spi.EventNotifier} is in charge of registering
 * {@link CamelContext} in the OSGi registry
 */
public class OsgiCamelContextPublisher extends EventNotifierSupport {

    public static final String CONTEXT_SYMBOLIC_NAME_PROPERTY = "camel.context.symbolicname";
    public static final String CONTEXT_VERSION_PROPERTY = "camel.context.version";

    private final BundleContext bundleContext;
    private final Map<CamelContext, ServiceRegistration> registrations = new ConcurrentHashMap<CamelContext, ServiceRegistration>();

    public OsgiCamelContextPublisher(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void notify(EventObject event) throws Exception {
        if (event instanceof CamelContextStartedEvent) {
            CamelContext context = ((CamelContextStartedEvent) event).getContext();

            Properties props = new Properties();
            props.put(CONTEXT_SYMBOLIC_NAME_PROPERTY, bundleContext.getBundle().getSymbolicName());
            props.put(CONTEXT_VERSION_PROPERTY, getBundleVersion(bundleContext.getBundle()));

            if (log.isDebugEnabled()) {
                log.debug("Registering CamelContext [{}] in OSGi registry", context.getName());
            }
            ServiceRegistration reg = bundleContext.registerService(CamelContext.class.getName(), context, props);
            registrations.put(context, reg);
        } else if (event instanceof CamelContextStoppingEvent) {
            CamelContext context = ((CamelContextStoppingEvent) event).getContext();
            ServiceRegistration reg = registrations.get(context);
            if (reg != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Unregistering CamelContext [{}] from OSGi registry", context.getName());
                }
                reg.unregister();
            }
        }
    }

    public boolean isEnabled(EventObject event) {
        return true;
    }

    @Override
    protected void doStart() throws Exception {
    }

    @Override
    protected void doStop() throws Exception {
        registrations.clear();
    }

    public static Version getBundleVersion(Bundle bundle) {
        Dictionary headers = bundle.getHeaders();
        String version = (String) headers.get(Constants.BUNDLE_VERSION);
        return (version != null) ? Version.parseVersion(version) : Version.emptyVersion;
    }
}
