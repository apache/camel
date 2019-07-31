/*
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
package org.apache.camel.impl;

import java.util.Map;
import javax.naming.Context;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Producer;
import org.apache.camel.TypeConverter;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.impl.engine.BeanProcessorFactoryResolver;
import org.apache.camel.impl.engine.BeanProxyFactoryResolver;
import org.apache.camel.impl.engine.DefaultAsyncProcessorAwaitManager;
import org.apache.camel.impl.engine.DefaultCamelBeanPostProcessor;
import org.apache.camel.impl.engine.DefaultCamelContextNameStrategy;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.impl.engine.DefaultComponentResolver;
import org.apache.camel.impl.engine.DefaultDataFormatResolver;
import org.apache.camel.impl.engine.DefaultEndpointRegistry;
import org.apache.camel.impl.engine.DefaultFactoryFinderResolver;
import org.apache.camel.impl.engine.DefaultInflightRepository;
import org.apache.camel.impl.engine.DefaultInjector;
import org.apache.camel.impl.engine.DefaultLanguageResolver;
import org.apache.camel.impl.engine.DefaultManagementNameStrategy;
import org.apache.camel.impl.engine.DefaultMessageHistoryFactory;
import org.apache.camel.impl.engine.DefaultNodeIdFactory;
import org.apache.camel.impl.engine.DefaultPackageScanClassResolver;
import org.apache.camel.impl.engine.DefaultProcessorFactory;
import org.apache.camel.impl.engine.DefaultRouteController;
import org.apache.camel.impl.engine.DefaultShutdownStrategy;
import org.apache.camel.impl.engine.DefaultStreamCachingStrategy;
import org.apache.camel.impl.engine.DefaultTracer;
import org.apache.camel.impl.engine.DefaultUnitOfWorkFactory;
import org.apache.camel.impl.engine.DefaultUuidGenerator;
import org.apache.camel.impl.engine.EndpointKey;
import org.apache.camel.impl.engine.HeadersMapFactoryResolver;
import org.apache.camel.impl.engine.ReactiveExecutorResolver;
import org.apache.camel.impl.engine.RestRegistryFactoryResolver;
import org.apache.camel.impl.engine.ServicePool;
import org.apache.camel.impl.engine.WebSpherePackageScanClassResolver;
import org.apache.camel.impl.health.DefaultHealthCheckRegistry;
import org.apache.camel.runtimecatalog.RuntimeCamelCatalog;
import org.apache.camel.runtimecatalog.impl.DefaultRuntimeCamelCatalog;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.spi.BeanProcessorFactory;
import org.apache.camel.spi.BeanProxyFactory;
import org.apache.camel.spi.BeanRepository;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.CamelContextNameStrategy;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.camel.spi.EndpointRegistry;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.spi.HeadersMapFactory;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.LanguageResolver;
import org.apache.camel.spi.ManagementNameStrategy;
import org.apache.camel.spi.MessageHistoryFactory;
import org.apache.camel.spi.ModelJAXBContextFactory;
import org.apache.camel.spi.NodeIdFactory;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.ReactiveExecutor;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.RestRegistryFactory;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.spi.Tracer;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.support.DefaultRegistry;

/**
 * Represents the context used to configure routes and the policies to use.
 */
public class DefaultCamelContext extends AbstractModelCamelContext {

    /**
     * Creates the {@link CamelContext} using {@link DefaultRegistry} as registry.
     * <p/>
     * Use one of the other constructors to force use an explicit registry.
     */
    public DefaultCamelContext() {
        super();
    }

    /**
     * Creates the {@link CamelContext} using the given {@link BeanRepository}
     * as first-choice repository, and the {@link org.apache.camel.support.SimpleRegistry} as fallback, via
     * the {@link DefaultRegistry} implementation.
     *
     * @param repository the bean repository.
     */
    public DefaultCamelContext(BeanRepository repository) {
        super(new DefaultRegistry(repository));
    }

    /**
     * Creates the {@link CamelContext} using the given JNDI context as the registry
     *
     * @param jndiContext the JNDI context
     * @deprecated create a new {@link JndiRegistry} and use the constructor that accepts this registry.
     */
    @Deprecated
    public DefaultCamelContext(Context jndiContext) {
        this(new JndiRegistry(jndiContext));
    }

    /**
     * Creates the {@link CamelContext} using the given registry
     *
     * @param registry the registry
     */
    public DefaultCamelContext(Registry registry) {
        super(registry);
    }

    /**
     * Creates the {@link CamelContext} and allows to control whether the context
     * should automatic initialize or not.
     * <p/>
     * This is used by some Camel components such as camel-cdi and camel-blueprint, however
     * this constructor is not intended for regular Camel end users.
     *
     * @param init whether to automatic initialize.
     */
    public DefaultCamelContext(boolean init) {
        super(init);
    }

    protected TypeConverter createTypeConverter() {
        return new DefaultTypeConverter(
                this, getPackageScanClassResolver(),
                getInjector(), getDefaultFactoryFinder(), isLoadTypeConverters());
    }

    protected TypeConverterRegistry createTypeConverterRegistry() {
        TypeConverter typeConverter = getTypeConverter();
        if (typeConverter instanceof TypeConverterRegistry) {
            return (TypeConverterRegistry) typeConverter;
        }
        return null;
    }

    protected Injector createInjector() {
        FactoryFinder finder = getDefaultFactoryFinder();
        try {
            return (Injector) finder.newInstance("Injector");
        } catch (NoFactoryAvailableException e) {
            // lets use the default injector
            return new DefaultInjector(this);
        }
    }

    protected CamelBeanPostProcessor createBeanPostProcessor() {
        return new DefaultCamelBeanPostProcessor(this);
    }

    protected ComponentResolver createComponentResolver() {
        return new DefaultComponentResolver();
    }

    protected Registry createRegistry() {
        return new DefaultRegistry();
    }

    protected UuidGenerator createUuidGenerator() {
        return new DefaultUuidGenerator();
    }

    protected ModelJAXBContextFactory createModelJAXBContextFactory() {
        return new DefaultModelJAXBContextFactory();
    }

    protected NodeIdFactory createNodeIdFactory() {
        return new DefaultNodeIdFactory();
    }

    protected FactoryFinderResolver createFactoryFinderResolver() {
        return new DefaultFactoryFinderResolver();
    }

    protected ClassResolver createClassResolver() {
        return new DefaultClassResolver(this);
    }

    protected ProcessorFactory createProcessorFactory() {
        return new DefaultProcessorFactory();
    }

    protected DataFormatResolver createDataFormatResolver() {
        return new DefaultDataFormatResolver();
    }

    protected MessageHistoryFactory createMessageHistoryFactory() {
        return new DefaultMessageHistoryFactory();
    }

    protected InflightRepository createInflightRepository() {
        return new DefaultInflightRepository();
    }

    protected AsyncProcessorAwaitManager createAsyncProcessorAwaitManager() {
        return new DefaultAsyncProcessorAwaitManager();
    }

    protected RouteController createRouteController() {
        return new DefaultRouteController(this);
    }

    protected HealthCheckRegistry createHealthCheckRegistry() {
        return new DefaultHealthCheckRegistry(this);
    }

    protected ShutdownStrategy createShutdownStrategy() {
        return new DefaultShutdownStrategy(this);
    }

    protected PackageScanClassResolver createPackageScanClassResolver() {
        PackageScanClassResolver packageScanClassResolver;
        // use WebSphere specific resolver if running on WebSphere
        if (WebSpherePackageScanClassResolver.isWebSphereClassLoader(this.getClass().getClassLoader())) {
            log.info("Using WebSphere specific PackageScanClassResolver");
            packageScanClassResolver = new WebSpherePackageScanClassResolver("META-INF/services/org/apache/camel/TypeConverter");
        } else {
            packageScanClassResolver = new DefaultPackageScanClassResolver();
        }
        return packageScanClassResolver;
    }

    protected ExecutorServiceManager createExecutorServiceManager() {
        return new DefaultExecutorServiceManager(this);
    }

    protected ServicePool<Producer> createProducerServicePool() {
        return new ServicePool<>(Endpoint::createProducer, Producer::getEndpoint, 100);
    }

    protected ServicePool<PollingConsumer> createPollingConsumerServicePool() {
        return new ServicePool<>(Endpoint::createPollingConsumer, PollingConsumer::getEndpoint, 100);
    }

    protected UnitOfWorkFactory createUnitOfWorkFactory() {
        return new DefaultUnitOfWorkFactory();
    }

    protected RuntimeCamelCatalog createRuntimeCamelCatalog() {
        return new DefaultRuntimeCamelCatalog(this, true);
    }

    protected CamelContextNameStrategy createCamelContextNameStrategy() {
        return new DefaultCamelContextNameStrategy();
    }

    protected ManagementNameStrategy createManagementNameStrategy() {
        return new DefaultManagementNameStrategy(this);
    }

    protected HeadersMapFactory createHeadersMapFactory() {
        return new HeadersMapFactoryResolver().resolve(this);
    }

    protected BeanProxyFactory createBeanProxyFactory() {
        return new BeanProxyFactoryResolver().resolve(this);
    }

    protected BeanProcessorFactory createBeanProcessorFactory() {
        return new BeanProcessorFactoryResolver().resolve(this);
    }

    protected Tracer createTracer() {
        Tracer tracer = null;
        if (getRegistry() != null) {
            // lookup in registry
            Map<String, Tracer> map = getRegistry().findByTypeWithName(Tracer.class);
            if (map.size() == 1) {
                tracer = map.values().iterator().next();
            }
        }
        if (tracer == null) {
            tracer = getExtension(Tracer.class);
        }
        if (tracer == null) {
            tracer = new DefaultTracer();
            setExtension(Tracer.class, tracer);
        }
        return tracer;
    }

    protected LanguageResolver createLanguageResolver() {
        return new DefaultLanguageResolver();
    }

    protected RestRegistryFactory createRestRegistryFactory() {
        return new RestRegistryFactoryResolver().resolve(this);
    }

    protected EndpointRegistry<EndpointKey> createEndpointRegistry(Map<EndpointKey, Endpoint> endpoints) {
        return new DefaultEndpointRegistry(this, endpoints);
    }

    protected StreamCachingStrategy createStreamCachingStrategy() {
        return new DefaultStreamCachingStrategy();
    }

    protected ReactiveExecutor createReactiveExecutor() {
        return new ReactiveExecutorResolver().resolve(this);
    }
}
