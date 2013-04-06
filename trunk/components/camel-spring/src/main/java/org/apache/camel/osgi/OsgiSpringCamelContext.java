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

import org.apache.camel.TypeConverter;
import org.apache.camel.core.osgi.OsgiCamelContextHelper;
import org.apache.camel.core.osgi.OsgiFactoryFinderResolver;
import org.apache.camel.core.osgi.OsgiTypeConverter;
import org.apache.camel.core.osgi.utils.BundleContextUtils;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.Registry;
import org.apache.camel.spring.SpringCamelContext;
import org.osgi.framework.BundleContext;
import org.springframework.context.ApplicationContext;

public class OsgiSpringCamelContext extends SpringCamelContext {

    private final BundleContext bundleContext;

    public OsgiSpringCamelContext(ApplicationContext applicationContext, BundleContext bundleContext) {
        super(applicationContext);
        this.bundleContext = bundleContext;
        OsgiCamelContextHelper.osgiUpdate(this, bundleContext);
    }

    @Override
    protected TypeConverter createTypeConverter() {
        // CAMEL-3614: make sure we use a bundle context which imports org.apache.camel.impl.converter package
        BundleContext ctx = BundleContextUtils.getBundleContext(getClass());
        if (ctx == null) {
            ctx = bundleContext;
        }
        FactoryFinder finder = new OsgiFactoryFinderResolver(bundleContext).resolveDefaultFactoryFinder(getClassResolver());
        return new OsgiTypeConverter(ctx, getInjector(), finder);
    }

    @Override
    protected Registry createRegistry() {
        return OsgiCamelContextHelper.wrapRegistry(this, super.createRegistry(), bundleContext);
    }

    @Override
    public void setName(String name) {
        super.setName(name);
        // in OSGi prefix the bundle id to the management name so it will be unique in the JVM
        // and also nicely sorted based on bundle id
        super.setManagementName(bundleContext.getBundle().getBundleId() + "-" + name);
    }

}
