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
package org.apache.camel.osgi;

import org.apache.camel.core.osgi.OsgiDefaultCamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Registry;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.osgi.context.BundleContextAware;

/**
 * This factory just create a DefaultContext in OSGi without 
 * any spring application context involved.
 */
public class CamelContextFactory implements BundleContextAware {
    private static final Logger LOG = LoggerFactory.getLogger(CamelContextFactory.class);
    private BundleContext bundleContext;
    private Registry registry;

    public BundleContext getBundleContext() {
        return bundleContext;
    }

    public void setBundleContext(BundleContext bundleContext) {
        LOG.debug("Using BundleContext: {}", bundleContext);
        this.bundleContext = bundleContext;
    }

    public Registry getRegistry() {
        return registry;
    }

    public void setRegistry(Registry registry) {
        LOG.debug("Using Registry: {}", registry);
        this.registry = registry;
    }

    protected DefaultCamelContext newCamelContext() {
        if (registry != null) {
            return new OsgiDefaultCamelContext(bundleContext, registry);
        } else {
            return new OsgiDefaultCamelContext(bundleContext);
        }
    }
    
    public DefaultCamelContext createContext() {
        return newCamelContext();
    }

}
