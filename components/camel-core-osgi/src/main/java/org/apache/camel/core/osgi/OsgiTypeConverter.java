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
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultPackageScanClassResolver;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.TypeConverterLoader;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

public class OsgiTypeConverter extends ServiceSupport implements TypeConverter, TypeConverterRegistry, ServiceTrackerCustomizer {
    private static final Log LOG = LogFactory.getLog(OsgiTypeConverter.class);

    private final BundleContext bundleContext;
    private final Injector injector;
    private final ServiceTracker tracker;
    private volatile DefaultTypeConverter delegate;

    public OsgiTypeConverter(BundleContext bundleContext, Injector injector) {
        this.bundleContext = bundleContext;
        this.injector = injector;
        this.tracker = new ServiceTracker(bundleContext, TypeConverterLoader.class.getName(), this);
    }

    public Object addingService(ServiceReference serviceReference) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("AddingService: " + serviceReference);
        }
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
        if (LOG.isTraceEnabled()) {
            LOG.trace("RemovedService: " + serviceReference);
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

    public DefaultTypeConverter getDelegate() {
        if (delegate == null) {
            synchronized (this) {
                if (delegate != null) {
                    return delegate;
                } else {
                    delegate = createRegistry();
                }
            }
        }
        return delegate;
    }

    protected DefaultTypeConverter createRegistry() {
        // base the osgi type converter on the default type converter
        // TODO: Why is it based on the DefaultPackageScanClassResolver and not OsgiPackageScanClassResolver?
        DefaultTypeConverter reg = new DefaultTypeConverter(new DefaultPackageScanClassResolver() {
            @Override
            public Set<ClassLoader> getClassLoaders() {
                return Collections.emptySet();
            }
        }, injector, null);
        Object[] services = this.tracker.getServices();
        if (services != null) {
            for (Object o : services) {
                try {
                    ((TypeConverterLoader) o).load(reg);
                } catch (Throwable t) {
                    throw ObjectHelper.wrapRuntimeCamelException(t);
                }
            }
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Created TypeConverter: " + reg);
        }
        return reg;
    }



}
