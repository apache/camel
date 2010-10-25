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

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.spi.ComponentResolver;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

public class OsgiComponentResolver implements ComponentResolver {
    
    private static final transient Log LOG = LogFactory.getLog(OsgiComponentResolver.class);

    private final BundleContext bundleContext;

    public OsgiComponentResolver(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }
    
    public Component resolveComponent(String name, CamelContext context) throws Exception {
        Object bean = null;
        try {
            bean = context.getRegistry().lookup(name);
            if (bean != null && LOG.isDebugEnabled()) {
                LOG.debug("Found component: " + name + " in registry: " + bean);
            }
        } catch (Exception e) {
            LOG.debug("Ignored error looking up bean: " + name + ". Error: " + e);
        }
        if (bean instanceof Component) {
            return (Component)bean;
        }

        // Check in OSGi bundles
        return getComponent(name, context);
    }

    protected Component getComponent(String name, CamelContext context) throws Exception {
        LOG.trace("Finding Component: " + name);
        try {
            ServiceReference[] refs = bundleContext.getServiceReferences(ComponentResolver.class.getName(), "(component=" + name + ")");
            if (refs != null && refs.length > 0) {
                ComponentResolver resolver = (ComponentResolver) bundleContext.getService(refs[0]);
                return resolver.resolveComponent(name, context);
            }
            return null;
        } catch (InvalidSyntaxException e) {
            throw new RuntimeException(e); // Should never happen
        }
    }

}
