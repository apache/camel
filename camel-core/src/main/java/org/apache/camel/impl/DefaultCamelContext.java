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
package org.apache.camel.impl;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import javax.naming.Context;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Producer;
import org.apache.camel.TypeConverter;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.impl.converter.BaseTypeConverterRegistry;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.impl.health.DefaultHealthCheckRegistry;
import org.apache.camel.impl.transformer.TransformerKey;
import org.apache.camel.impl.validator.ValidatorKey;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.apache.camel.model.validator.ValidatorDefinition;
import org.apache.camel.runtimecatalog.RuntimeCamelCatalog;
import org.apache.camel.runtimecatalog.impl.DefaultRuntimeCamelCatalog;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
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
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.ManagementStrategyFactory;
import org.apache.camel.spi.MessageHistoryFactory;
import org.apache.camel.spi.ModelJAXBContextFactory;
import org.apache.camel.spi.NodeIdFactory;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.RestRegistry;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.spi.TransformerRegistry;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.spi.ValidatorRegistry;

/**
 * Represents the context used to configure routes and the policies to use.
 */
public class DefaultCamelContext extends AbstractCamelContext {

    /**
     * Creates the {@link CamelContext} using {@link JndiRegistry} as registry,
     * but will silently fallback and use {@link SimpleRegistry} if JNDI cannot be used.
     * <p/>
     * Use one of the other constructors to force use an explicit registry / JNDI.
     */
    public DefaultCamelContext() {
        super();
    }

    /**
     * Creates the {@link CamelContext} using the given JNDI context as the registry
     *
     * @param jndiContext the JNDI context
     */
    public DefaultCamelContext(Context jndiContext) {
        super(jndiContext);
    }

    /**
     * Creates the {@link CamelContext} using the given registry
     *
     * @param registry the registry
     */
    public DefaultCamelContext(Registry registry) {
        super(registry);
    }

    public DefaultCamelContext(boolean init) {
        super(init);
    }

    /**
     * Lazily create a default implementation
     */
    protected TypeConverter createTypeConverter() {
        BaseTypeConverterRegistry answer;
        answer = new DefaultTypeConverter(getPackageScanClassResolver(), getInjector(), getDefaultFactoryFinder(), isLoadTypeConverters());
        answer.setCamelContext(this);
        setTypeConverterRegistry(answer);
        return answer;
    }

    @Override
    protected TypeConverterRegistry createTypeConverterRegistry() {
        TypeConverter typeConverter = getTypeConverter();
        if (typeConverter instanceof TypeConverterRegistry) {
            return (TypeConverterRegistry) typeConverter;
        }
        return null;
    }

    /**
     * Lazily create a default implementation
     */
    protected Injector createInjector() {
        FactoryFinder finder = getDefaultFactoryFinder();
        try {
            return (Injector) finder.newInstance("Injector");
        } catch (NoFactoryAvailableException e) {
            // lets use the default injector
            return new DefaultInjector(this);
        }
    }

    /**
     * Lazily create a default implementation
     */
    protected ComponentResolver createComponentResolver() {
        return new DefaultComponentResolver();
    }

    /**
     * Lazily create a default implementation
     */
    protected Registry createRegistry() {
        JndiRegistry jndi = new JndiRegistry();
        try {
            // getContext() will force setting up JNDI
            jndi.getContext();
            return jndi;
        } catch (Throwable e) {
            log.debug("Cannot create javax.naming.InitialContext due " + e.getMessage() + ". Will fallback and use SimpleRegistry instead. This exception is ignored.", e);
            return new SimpleRegistry();
        }
    }

    protected ManagementStrategy createManagementStrategy() {
        if (!isJMXDisabled()) {
            try {
                ServiceLoader<ManagementStrategyFactory> loader = ServiceLoader.load(ManagementStrategyFactory.class);
                Iterator<ManagementStrategyFactory> iterator = loader.iterator();
                if (iterator.hasNext()) {
                    return iterator.next().create(this);
                }
            } catch (Exception e) {
                log.warn("Cannot create JMX lifecycle strategy. Will fallback and disable JMX.", e);
            }
        }
        return new DefaultManagementStrategy(this);
    }

    protected UuidGenerator createUuidGenerator() {
        if (System.getProperty("com.google.appengine.runtime.environment") != null) {
            // either "Production" or "Development"
            return new JavaUuidGenerator();
        } else {
            return new DefaultUuidGenerator();
        }
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

    protected LanguageResolver createLanguageResolver() {
        return new DefaultLanguageResolver();
    }

    @Override
    protected RestRegistry createRestRegistry() {
        return new DefaultRestRegistry();
    }

    protected EndpointRegistry<EndpointKey> createEndpointRegistry(Map<EndpointKey, Endpoint> endpoints) {
        return new DefaultEndpointRegistry(this, endpoints);
    }

    protected ValidatorRegistry<ValidatorKey> createValidatorRegistry(List<ValidatorDefinition> validators) throws Exception {
        return new DefaultValidatorRegistry(this, validators);
    }

    protected TransformerRegistry<TransformerKey> createTransformerRegistry(List<TransformerDefinition> transformers) throws Exception {
        return new DefaultTransformerRegistry(this, transformers);
    }

    @Override
    protected StreamCachingStrategy createStreamCachingStrategy() {
        return new DefaultStreamCachingStrategy();
    }
}
