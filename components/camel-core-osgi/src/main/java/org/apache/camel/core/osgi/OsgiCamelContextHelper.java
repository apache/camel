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
import org.apache.camel.impl.CompositeRegistry;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.ObjectHelper;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OsgiCamelContextHelper {
    private static final Logger LOG = LoggerFactory.getLogger(OsgiCamelContextHelper.class);

    private OsgiCamelContextHelper() {
        // helper class
    }
    
    public static void osgiUpdate(DefaultCamelContext camelContext, BundleContext bundleContext) {
        ObjectHelper.notNull(bundleContext, "BundleContext");

        LOG.debug("Using OsgiCamelContextNameStrategy");
        camelContext.setNameStrategy(new OsgiCamelContextNameStrategy(bundleContext));
        LOG.debug("Using OsgiManagementNameStrategy");
        camelContext.setManagementNameStrategy(new OsgiManagementNameStrategy(camelContext, bundleContext));
        LOG.debug("Using OsgiClassResolver");
        camelContext.setClassResolver(new OsgiClassResolver(camelContext, bundleContext));
        LOG.debug("Using OsgiFactoryFinderResolver");
        camelContext.setFactoryFinderResolver(new OsgiFactoryFinderResolver(bundleContext));
        LOG.debug("Using OsgiPackageScanClassResolver");
        camelContext.setPackageScanClassResolver(new OsgiPackageScanClassResolver(bundleContext));
        LOG.debug("Using OsgiComponentResolver");
        camelContext.setComponentResolver(new OsgiComponentResolver(bundleContext));
        LOG.debug("Using OsgiLanguageResolver");
        camelContext.setLanguageResolver(new OsgiLanguageResolver(bundleContext));
        LOG.debug("Using OsgiDataFormatResolver");
        camelContext.setDataFormatResolver(new OsgiDataFormatResolver(bundleContext));
    }
    
    public static Registry wrapRegistry(CamelContext camelContext, Registry registry, BundleContext bundleContext) {
        ObjectHelper.notNull(bundleContext, "BundleContext");

        LOG.debug("Setting up OSGi ServiceRegistry");
        OsgiServiceRegistry osgiServiceRegistry = new OsgiServiceRegistry(bundleContext);
        // Need to clean up the OSGi service when camel context is closed.
        camelContext.addLifecycleStrategy(osgiServiceRegistry);
        CompositeRegistry compositeRegistry = new CompositeRegistry();
        compositeRegistry.addRegistry(osgiServiceRegistry);
        compositeRegistry.addRegistry(registry);
        return compositeRegistry;
    }

}
