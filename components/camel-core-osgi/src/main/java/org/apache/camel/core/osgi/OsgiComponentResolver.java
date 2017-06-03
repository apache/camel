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
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResolverHelper;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OsgiComponentResolver implements ComponentResolver {
    private static final Logger LOG = LoggerFactory.getLogger(OsgiComponentResolver.class);

    private final BundleContext bundleContext;

    public OsgiComponentResolver(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public Component resolveComponent(String name, CamelContext context) throws Exception {
        Component componentReg = ResolverHelper.lookupComponentInRegistryWithFallback(context, name);
        if (componentReg != null) {
            return componentReg;
        }

        // Check in OSGi bundles
        return getComponent(name, context);
    }

    protected Component getComponent(String name, CamelContext context) throws Exception {
        LOG.trace("Finding Component: {}", name);
        try {
            ServiceReference<?>[] refs = bundleContext.getServiceReferences(ComponentResolver.class.getName(), "(component=" + name + ")");
            if (refs != null) {
                for (ServiceReference<?> ref : refs) {
                    Object service = bundleContext.getService(ref);
                    if (ComponentResolver.class.isAssignableFrom(service.getClass())) {
                        ComponentResolver resolver = (ComponentResolver) service;
                        return resolver.resolveComponent(name, context);
                    }
                }
            }
            return null;
        } catch (InvalidSyntaxException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

}
