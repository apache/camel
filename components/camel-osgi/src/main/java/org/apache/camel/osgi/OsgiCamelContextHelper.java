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

import java.util.List;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.converter.AnnotationTypeConverterLoader;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.impl.converter.TypeConverterLoader;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;

public final class OsgiCamelContextHelper {
    private static final transient Log LOG = LogFactory.getLog(OsgiCamelContextHelper.class);
    
    private OsgiCamelContextHelper() {
        // helper class
    }
    
    public static DefaultTypeConverter createTypeConverter(DefaultCamelContext context) {
        
        DefaultTypeConverter answer = new DefaultTypeConverter(context.getPackageScanClassResolver(), context.getInjector(), context.getDefaultFactoryFinder());
        List<TypeConverterLoader> typeConverterLoaders = answer.getTypeConverterLoaders();

        // Remove the AnnotationTypeConverterLoader
        TypeConverterLoader atLoader = null; 
        for (TypeConverterLoader loader : typeConverterLoaders) {
            if (loader instanceof AnnotationTypeConverterLoader) {
                atLoader = loader;
                break;
            }
        }
        if (atLoader != null) {
            typeConverterLoaders.remove(atLoader);
        }

        // add our osgi annotation loader
        typeConverterLoaders.add(new OsgiAnnotationTypeConverterLoader(context.getPackageScanClassResolver()));
        context.setTypeConverterRegistry(answer);
        return answer;
    }
    
    public static void osgiUpdate(DefaultCamelContext camelContext, BundleContext bundleContext) {
        if (bundleContext != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Using OSGi resolvers");
            }
            updateRegistry(camelContext, bundleContext);
            LOG.debug("Using the OsgiClassResolver");
            camelContext.setClassResolver(new OsgiClassResolver(bundleContext));
            LOG.debug("Using OsgiFactoryFinderResolver");
            camelContext.setFactoryFinderResolver(new OsgiFactoryFinderResolver());
            LOG.debug("Using OsgiPackageScanClassResolver");
            camelContext.setPackageScanClassResolver(new OsgiPackageScanClassResolver(bundleContext));
            LOG.debug("Using OsgiComponentResolver");
            camelContext.setComponentResolver(new OsgiComponentResolver());
            LOG.debug("Using OsgiLanguageResolver");
            camelContext.setLanguageResolver(new OsgiLanguageResolver());            
        } else {
            // TODO: should we not thrown an exception to not allow it to startup
            LOG.warn("BundleContext not set, cannot run in OSGI container");
        }
    }
    
    public static void updateRegistry(DefaultCamelContext camelContext, BundleContext bundleContext) {
        ObjectHelper.notNull(bundleContext, "BundleContext");
        LOG.debug("Setting the OSGi ServiceRegistry");
        OsgiServiceRegistry osgiServiceRegistry = new OsgiServiceRegistry(bundleContext);
        // Need to clean up the OSGi service when camel context is closed.
        camelContext.addLifecycleStrategy(osgiServiceRegistry);
        CompositeRegistry compositeRegistry = new CompositeRegistry();
        compositeRegistry.addRegistry(osgiServiceRegistry);
        compositeRegistry.addRegistry(camelContext.getRegistry());
        camelContext.setRegistry(compositeRegistry);        
    }

}
