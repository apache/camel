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
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.converter.AnnotationTypeConverterLoader;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.impl.converter.TypeConverterLoader;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.springframework.osgi.context.BundleContextAware;

/**
 * This factory just create a DefaultContext in OSGi without 
 * any spring application context involved.
 */
public class CamelContextFactory implements BundleContextAware {
    private static final transient Log LOG = LogFactory.getLog(CamelContextFactory.class);
    private BundleContext bundleContext;

    public BundleContext getBundleContext() {
        return bundleContext;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }
    
    public DefaultCamelContext createContext() {
        DefaultCamelContext context = new DefaultCamelContext();
        if (bundleContext != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("The bundle context is not be null, let's setup the Osgi resolvers");
            }
            context.setFactoryFinderResolver(new OsgiFactoryFinderResolver());
            context.setPackageScanClassResolver(new OsgiPackageScanClassResolver(bundleContext));
            context.setComponentResolver(new OsgiComponentResolver());
            context.setLanguageResolver(new OsgiLanguageResolver());
            addOsgiAnnotationTypeConverterLoader(context, bundleContext);            
        }
        
        return context;
    }
    
    protected void addOsgiAnnotationTypeConverterLoader(DefaultCamelContext context, BundleContext bundleContext) {
        DefaultTypeConverter typeConverter = (DefaultTypeConverter) context.getTypeConverter();
        List<TypeConverterLoader> typeConverterLoaders = typeConverter.getTypeConverterLoaders();
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
        typeConverterLoaders.add(new OsgiAnnotationTypeConverterLoader(context.getPackageScanClassResolver()));
        LOG.debug("added the OsgiAnnotationTypeConverterLoader");
    }    
}
