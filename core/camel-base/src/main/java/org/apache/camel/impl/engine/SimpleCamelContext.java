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
package org.apache.camel.impl.engine;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.TypeConverter;
import org.apache.camel.catalog.RuntimeCamelCatalog;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.impl.health.DefaultHealthCheckRegistry;
import org.apache.camel.impl.transformer.TransformerKey;
import org.apache.camel.impl.validator.ValidatorKey;
import org.apache.camel.processor.MulticastProcessor;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.spi.BeanProcessorFactory;
import org.apache.camel.spi.BeanProxyFactory;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.CamelContextNameStrategy;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.ComponentNameResolver;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.ConfigurerResolver;
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
import org.apache.camel.spi.ModelToXMLDumper;
import org.apache.camel.spi.NodeIdFactory;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.PackageScanResourceResolver;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.ReactiveExecutor;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.RestBindingJaxbDataFormatFactory;
import org.apache.camel.spi.RestRegistryFactory;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.spi.Tracer;
import org.apache.camel.spi.TransformerRegistry;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.spi.ValidatorRegistry;
import org.apache.camel.spi.XMLRoutesDefinitionLoader;
import org.apache.camel.support.DefaultRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the context used to configure routes and the policies to use.
 */
public class SimpleCamelContext extends AbstractCamelContext {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleCamelContext.class);

    /**
     * Creates the {@link CamelContext} using {@link DefaultRegistry} as
     * registry.
     * <p/>
     * Use one of the other constructors to force use an explicit registry.
     */
    public SimpleCamelContext() {
        this(true);
    }

    /**
     * Creates the {@link CamelContext} and allows to control whether the
     * context should automatic initialize or not.
     * <p/>
     * This is used by some Camel components such as camel-cdi and
     * camel-blueprint, however this constructor is not intended for regular
     * Camel end users.
     *
     * @param init whether to automatic initialize.
     */
    public SimpleCamelContext(boolean init) {
        super(init);
    }

    @Override
    protected HealthCheckRegistry createHealthCheckRegistry() {
        return new DefaultHealthCheckRegistry(this);
    }

    @Override
    protected TypeConverter createTypeConverter() {
        return new DefaultTypeConverter(getCamelContextReference(), getPackageScanClassResolver(), getInjector(),
                getDefaultFactoryFinder(), isLoadTypeConverters());
    }

    @Override
    protected TypeConverterRegistry createTypeConverterRegistry() {
        TypeConverter typeConverter = getTypeConverter();
        // type converter is also registry so create type converter
        if (typeConverter == null) {
            typeConverter = createTypeConverter();
        }
        if (typeConverter instanceof TypeConverterRegistry) {
            return (TypeConverterRegistry)typeConverter;
        }
        return null;
    }

    @Override
    protected Injector createInjector() {
        FactoryFinder finder = getDefaultFactoryFinder();
        return finder.newInstance("Injector", Injector.class).orElse(new DefaultInjector(getCamelContextReference()));
    }

    @Override
    protected PropertiesComponent createPropertiesComponent() {
        return new BaseServiceResolver<>(PropertiesComponent.FACTORY, PropertiesComponent.class)
                .resolve(getCamelContextReference())
                .orElseGet(org.apache.camel.component.properties.PropertiesComponent::new);
    }

    @Override
    protected CamelBeanPostProcessor createBeanPostProcessor() {
        return new DefaultCamelBeanPostProcessor(getCamelContextReference());
    }

    @Override
    protected ComponentResolver createComponentResolver() {
        return new DefaultComponentResolver();
    }

    @Override
    protected ComponentNameResolver createComponentNameResolver() {
        return new DefaultComponentNameResolver();
    }

    @Override
    protected Registry createRegistry() {
        return new DefaultRegistry();
    }

    @Override
    protected UuidGenerator createUuidGenerator() {
        return new DefaultUuidGenerator();
    }

    @Override
    protected ModelJAXBContextFactory createModelJAXBContextFactory() {
        return new BaseServiceResolver<>(ModelJAXBContextFactory.FACTORY, ModelJAXBContextFactory.class)
                .resolve(getCamelContextReference())
                .orElseThrow(() -> new IllegalArgumentException("Cannot find ModelJAXBContextFactory on classpath. "
                        + "Add camel-xml-jaxb to classpath."));
    }

    @Override
    protected NodeIdFactory createNodeIdFactory() {
        return new DefaultNodeIdFactory();
    }

    @Override
    protected FactoryFinderResolver createFactoryFinderResolver() {
        return new DefaultFactoryFinderResolver();
    }

    @Override
    protected ClassResolver createClassResolver() {
        return new DefaultClassResolver(getCamelContextReference());
    }

    @Override
    protected ProcessorFactory createProcessorFactory() {
        return new DefaultProcessorFactory();
    }

    @Override
    protected DataFormatResolver createDataFormatResolver() {
        return new DefaultDataFormatResolver();
    }

    @Override
    protected MessageHistoryFactory createMessageHistoryFactory() {
        return new DefaultMessageHistoryFactory();
    }

    @Override
    protected InflightRepository createInflightRepository() {
        return new DefaultInflightRepository();
    }

    @Override
    protected AsyncProcessorAwaitManager createAsyncProcessorAwaitManager() {
        return new DefaultAsyncProcessorAwaitManager();
    }

    @Override
    protected RouteController createRouteController() {
        // TODO:
        return new DefaultRouteController(getCamelContextReference());
    }

    @Override
    protected ShutdownStrategy createShutdownStrategy() {
        return new DefaultShutdownStrategy(getCamelContextReference());
    }

    @Override
    protected PackageScanClassResolver createPackageScanClassResolver() {
        PackageScanClassResolver packageScanClassResolver;
        // use WebSphere specific resolver if running on WebSphere
        if (WebSpherePackageScanClassResolver.isWebSphereClassLoader(this.getClass().getClassLoader())) {
            LOG.info("Using WebSphere specific PackageScanClassResolver");
            packageScanClassResolver = new WebSpherePackageScanClassResolver("META-INF/services/org/apache/camel/TypeConverter");
        } else {
            packageScanClassResolver = new DefaultPackageScanClassResolver();
        }
        return packageScanClassResolver;
    }

    @Override
    protected PackageScanResourceResolver createPackageScanResourceResolver() {
        return new DefaultPackageScanResourceResolver();
    }

    @Override
    protected UnitOfWorkFactory createUnitOfWorkFactory() {
        return new DefaultUnitOfWorkFactory();
    }

    @Override
    protected RuntimeCamelCatalog createRuntimeCamelCatalog() {
        return new BaseServiceResolver<>(RuntimeCamelCatalog.FACTORY, RuntimeCamelCatalog.class)
                .resolve(getCamelContextReference())
                .orElseThrow(() -> new IllegalArgumentException("Cannot find RuntimeCamelCatalog on classpath. "
                        + "Add camel-core-catalog to classpath."));
    }

    @Override
    protected CamelContextNameStrategy createCamelContextNameStrategy() {
        return new DefaultCamelContextNameStrategy();
    }

    @Override
    protected ManagementNameStrategy createManagementNameStrategy() {
        return new DefaultManagementNameStrategy(getCamelContextReference());
    }

    @Override
    protected HeadersMapFactory createHeadersMapFactory() {
        return new BaseServiceResolver<>(HeadersMapFactory.FACTORY, HeadersMapFactory.class)
                .resolve(getCamelContextReference())
                .orElseGet(DefaultHeadersMapFactory::new);
    }

    @Override
    protected BeanProxyFactory createBeanProxyFactory() {
        return new BaseServiceResolver<>(BeanProxyFactory.FACTORY, BeanProxyFactory.class)
                .resolve(getCamelContextReference())
                .orElseThrow(() -> new IllegalArgumentException("Cannot find BeanProxyFactory on classpath. "
                        + "Add camel-bean to classpath."));
    }

    @Override
    protected BeanProcessorFactory createBeanProcessorFactory() {
        return new BaseServiceResolver<>(BeanProcessorFactory.FACTORY, BeanProcessorFactory.class)
                .resolve(getCamelContextReference())
                .orElseThrow(() -> new IllegalArgumentException("Cannot find BeanProcessorFactory on classpath. "
                        + "Add camel-bean to classpath."));
    }

    @Override
    protected BeanIntrospection createBeanIntrospection() {
        return new DefaultBeanIntrospection();
    }

    @Override
    protected XMLRoutesDefinitionLoader createXMLRoutesDefinitionLoader() {
        return new BaseServiceResolver<>(XMLRoutesDefinitionLoader.FACTORY, XMLRoutesDefinitionLoader.class)
                .resolve(getCamelContextReference())
                .orElseThrow(() -> new IllegalArgumentException("Cannot find XMLRoutesDefinitionLoader on classpath. "
                        + "Add either camel-xml-io or camel-xml-jaxb to classpath."));
    }

    @Override
    protected ModelToXMLDumper createModelToXMLDumper() {
        return new BaseServiceResolver<>(ModelToXMLDumper.FACTORY, ModelToXMLDumper.class)
                .resolve(getCamelContextReference())
                .orElseThrow(() -> new IllegalArgumentException("Cannot find ModelToXMLDumper on classpath. "
                        + "Add camel-xml-jaxb to classpath."));
    }

    @Override
    protected RestBindingJaxbDataFormatFactory createRestBindingJaxbDataFormatFactory() {
        return new BaseServiceResolver<>(RestBindingJaxbDataFormatFactory.FACTORY, RestBindingJaxbDataFormatFactory.class)
                .resolve(getCamelContextReference())
                .orElseThrow(() -> new IllegalArgumentException("Cannot find RestBindingJaxbDataFormatFactory on classpath. "
                        + "Add camel-jaxb to classpath."));
    }

    @Override
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

    @Override
    protected LanguageResolver createLanguageResolver() {
        return new DefaultLanguageResolver();
    }

    @Override
    protected ConfigurerResolver createConfigurerResolver() {
        return new DefaultConfigurerResolver();
    }

    @Override
    protected RestRegistryFactory createRestRegistryFactory() {
        return new BaseServiceResolver<>(RestRegistryFactory.FACTORY, RestRegistryFactory.class)
                .resolve(getCamelContextReference())
                .orElseThrow(() -> new IllegalArgumentException("Cannot find RestRegistryFactory on classpath. "
                        + "Add camel-rest to classpath."));
    }

    @Override
    protected EndpointRegistry<EndpointKey> createEndpointRegistry(Map<EndpointKey, Endpoint> endpoints) {
        return new DefaultEndpointRegistry(getCamelContextReference(), endpoints);
    }

    @Override
    protected StreamCachingStrategy createStreamCachingStrategy() {
        return new DefaultStreamCachingStrategy();
    }

    @Override
    protected ReactiveExecutor createReactiveExecutor() {
        return new BaseServiceResolver<>(ReactiveExecutor.FACTORY, ReactiveExecutor.class)
                .resolve(getCamelContextReference())
                .orElseGet(DefaultReactiveExecutor::new);
    }

    @Override
    public AsyncProcessor createMulticast(Collection<Processor> processors, ExecutorService executor, boolean shutdownExecutorService) {
        return new MulticastProcessor(getCamelContextReference(), null, processors, null, true, executor, shutdownExecutorService, false, false, 0, null, false, false);
    }

    @Override
    protected ValidatorRegistry<ValidatorKey> createValidatorRegistry() {
        return new DefaultValidatorRegistry(getCamelContextReference());
    }

    @Override
    protected TransformerRegistry<TransformerKey> createTransformerRegistry() {
        return new DefaultTransformerRegistry(getCamelContextReference());
    }

    @Override
    protected ExecutorServiceManager createExecutorServiceManager() {
        return new BaseExecutorServiceManager(getCamelContextReference());
    }

    @Override
    public Processor createErrorHandler(Route route, Processor processor) throws Exception {
        throw new UnsupportedOperationException();
    }
}
