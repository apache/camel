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
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.converter.AnnotationTypeConverterLoader;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.impl.converter.TypeConverterLoader;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.springframework.osgi.context.BundleContextAware;

@XmlRootElement(name = "camelContext")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelContextFactoryBean extends org.apache.camel.spring.CamelContextFactoryBean implements BundleContextAware {
    private static final transient Log LOG = LogFactory.getLog(CamelContextFactoryBean.class);
    
    @XmlTransient
    private BundleContext bundleContext;

    public BundleContext getBundleContext() {
        return bundleContext;
    }

    public void setBundleContext(BundleContext bundleContext) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Using BundleContext: " + bundleContext);
        }
        this.bundleContext = bundleContext;
    }
    
    protected SpringCamelContext createContext() {
        SpringCamelContext context = super.createContext();
        if (bundleContext != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Using OSGI resolvers");
            }
            updateRegistry(context);
            LOG.debug("Using OsgiFactoryFinderResolver");
            context.setFactoryFinderResolver(new OsgiFactoryFinderResolver());
            LOG.debug("Using OsgiPackageScanClassResolver");
            context.setPackageScanClassResolver(new OsgiPackageScanClassResolver(bundleContext));
            LOG.debug("Using OsgiComponentResolver");
            context.setComponentResolver(new OsgiComponentResolver());
            LOG.debug("Using OsgiLanguageResolver");
            context.setLanguageResolver(new OsgiLanguageResolver());
            addOsgiAnnotationTypeConverterLoader(context);
        } else {
            // TODO: should we not thrown an excpetion to not allow it to startup
            LOG.warn("BundleContext not set, cannot run in OSGI container");
        }
        
        return context;
    }    
    
    protected void updateRegistry(DefaultCamelContext context) {
        ObjectHelper.notNull(bundleContext, "BundleContext");
        LOG.debug("Setting the OSGi ServiceRegistry");
        OsgiServiceRegistry osgiServiceRegistry = new OsgiServiceRegistry(bundleContext);
        // Need to clean up the OSGi service when camel context is closed.
        context.addLifecycleStrategy(osgiServiceRegistry);
        CompositeRegistry compositeRegistry = new CompositeRegistry();
        compositeRegistry.addRegistry(osgiServiceRegistry);
        compositeRegistry.addRegistry(context.getRegistry());
        context.setRegistry(compositeRegistry);        
    }

    protected void addOsgiAnnotationTypeConverterLoader(SpringCamelContext context) {
        LOG.debug("Using OsgiAnnotationTypeConverterLoader");

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

        // add our osgi annotation loader
        typeConverterLoaders.add(new OsgiAnnotationTypeConverterLoader(context.getPackageScanClassResolver()));
    }
    
}
