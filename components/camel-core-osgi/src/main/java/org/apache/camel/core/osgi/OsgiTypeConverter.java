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

import java.util.Collections;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultPackageScanClassResolver;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.TypeConverterLoader;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OsgiTypeConverter extends ServiceSupport implements TypeConverter, TypeConverterRegistry, ServiceTrackerCustomizer {
    private static final Logger LOG = LoggerFactory.getLogger(OsgiTypeConverter.class);

    private final BundleContext bundleContext;
    private final Injector injector;
    private final FactoryFinder factoryFinder;
    private final ServiceTracker tracker;
    private volatile DefaultTypeConverter delegate;

    public OsgiTypeConverter(BundleContext bundleContext, Injector injector, FactoryFinder factoryFinder) {
        this.bundleContext = bundleContext;
        this.injector = injector;
        this.factoryFinder = factoryFinder;
        this.tracker = new ServiceTracker(bundleContext, TypeConverterLoader.class.getName(), this);
    }

    public Object addingService(ServiceReference serviceReference) {
        LOG.trace("AddingService: {}", serviceReference);
        TypeConverterLoader loader = (TypeConverterLoader) bundleContext.getService(serviceReference);
        if (loader != null) {
            try {
                loader.load(getDelegate());
            } catch (Throwable t) {
                throw ObjectHelper.wrapRuntimeCamelException(t);
            }
        }
        return loader;
    }

    public void modifiedService(ServiceReference serviceReference, Object o) {
    }

    public void removedService(ServiceReference serviceReference, Object o) {
        LOG.trace("RemovedService: {}", serviceReference);
        try {
            ServiceHelper.stopService(this.delegate);
        } catch (Exception e) {
            // ignore
            LOG.debug("Error stopping service due: " + e.getMessage() + ". This exception will be ignored.", e);
        }
        this.delegate = null;
    }

    @Override
    protected void doStart() throws Exception {
        this.tracker.open();
    }

    @Override
    protected void doStop() throws Exception {
        this.tracker.close();
        ServiceHelper.stopService(this.delegate);
        this.delegate = null;
    }

    public <T> T convertTo(Class<T> type, Object value) {
        return getDelegate().convertTo(type, value);
    }

    public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
        return getDelegate().convertTo(type, exchange, value);
    }

    public <T> T mandatoryConvertTo(Class<T> type, Object value) throws NoTypeConversionAvailableException {
        return getDelegate().mandatoryConvertTo(type, value);
    }

    public <T> T mandatoryConvertTo(Class<T> type, Exchange exchange, Object value) throws NoTypeConversionAvailableException {
        return getDelegate().mandatoryConvertTo(type, exchange, value);
    }

    public void addTypeConverter(Class<?> toType, Class<?> fromType, TypeConverter typeConverter) {
        getDelegate().addTypeConverter(toType, fromType, typeConverter);
    }

    public void addFallbackTypeConverter(TypeConverter typeConverter, boolean canPromote) {
        getDelegate().addFallbackTypeConverter(typeConverter, canPromote);
    }

    public TypeConverter lookup(Class<?> toType, Class<?> fromType) {
        return getDelegate().lookup(toType, fromType);
    }

    public void setInjector(Injector injector) {
        getDelegate().setInjector(injector);
    }

    public Injector getInjector() {
        return getDelegate().getInjector();
    }

    public synchronized DefaultTypeConverter getDelegate() {
        if (delegate == null) {
            delegate = createRegistry();
        }
        return delegate;
    }

    protected DefaultTypeConverter createRegistry() {
        // base the osgi type converter on the default type converter
        DefaultTypeConverter answer = new DefaultTypeConverter(new DefaultPackageScanClassResolver() {
            @Override
            public Set<ClassLoader> getClassLoaders() {
                // we don't need any classloaders as we use osgi service tracker instead
                return Collections.emptySet();
            }
        }, injector, factoryFinder);

        // load the type converters the tracker has been tracking
        Object[] services = this.tracker.getServices();
        if (services != null) {
            for (Object o : services) {
                try {
                    ((TypeConverterLoader) o).load(answer);
                } catch (Throwable t) {
                    throw new RuntimeCamelException("Error loading type converters from service: " + o + " due: " + t.getMessage(), t);
                }
            }
        }

        try {
            ServiceHelper.startService(answer);
        } catch (Exception e) {
            throw new RuntimeCamelException("Error staring OSGiTypeConverter due: " + e.getMessage(), e);
        }

        LOG.trace("Created TypeConverter: {}", answer);
        return answer;
    }
}
