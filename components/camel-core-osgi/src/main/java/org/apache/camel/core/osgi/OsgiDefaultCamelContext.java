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

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.TypeConverter;
import org.apache.camel.core.osgi.utils.BundleContextUtils;
import org.apache.camel.core.osgi.utils.BundleDelegatingClassLoader;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.LoadPropertiesException;
import org.osgi.framework.BundleContext;

public class OsgiDefaultCamelContext extends DefaultCamelContext {

    private final BundleContext bundleContext;
    private final Registry registry;

    public OsgiDefaultCamelContext(BundleContext bundleContext) {
        this(bundleContext, new OsgiServiceRegistry(bundleContext));
    }

    public OsgiDefaultCamelContext(BundleContext bundleContext, Registry registry) {
        super(registry);
        this.bundleContext = bundleContext;
        this.registry = registry;
        OsgiCamelContextHelper.osgiUpdate(this, bundleContext);
        // setup the application context classloader with the bundle classloader
        setApplicationContextClassLoader(new BundleDelegatingClassLoader(bundleContext.getBundle()));
    }

    @Override
    public Map<String, Properties> findComponents() throws LoadPropertiesException, IOException {
        return BundleContextUtils.findComponents(bundleContext, this);
    }

    @Override
    protected Registry createRegistry() {
        if (registry != null) {
            return OsgiCamelContextHelper.wrapRegistry(this, registry, bundleContext);
        } else {
            return OsgiCamelContextHelper.wrapRegistry(this, super.createRegistry(), bundleContext);
        }
    }

    @Override
    protected TypeConverter createTypeConverter() {
        // CAMEL-3614: make sure we use a bundle context which imports org.apache.camel.impl.converter package
        BundleContext ctx = BundleContextUtils.getBundleContext(getClass());
        if (ctx == null) {
            ctx = bundleContext;
        }
        FactoryFinder finder = new OsgiFactoryFinderResolver(bundleContext).resolveDefaultFactoryFinder(getClassResolver());
        return new OsgiTypeConverter(ctx, this, getInjector(), finder);
    }

}
