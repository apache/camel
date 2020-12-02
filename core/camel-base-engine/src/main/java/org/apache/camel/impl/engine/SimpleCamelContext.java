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

import java.util.Map;
import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.TypeConverter;
import org.apache.camel.catalog.RuntimeCamelCatalog;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.spi.AnnotationBasedProcessorFactory;
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
import org.apache.camel.spi.DeferServiceFactory;
import org.apache.camel.spi.EndpointRegistry;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.spi.HeadersMapFactory;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.InterceptEndpointFactory;
import org.apache.camel.spi.InternalProcessorFactory;
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
import org.apache.camel.spi.RouteFactory;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.spi.Tracer;
import org.apache.camel.spi.TransformerRegistry;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.apache.camel.spi.UriFactoryResolver;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.spi.ValidatorRegistry;
import org.apache.camel.spi.XMLRoutesDefinitionLoader;
import org.apache.camel.support.DefaultRegistry;
import org.apache.camel.support.DefaultUuidGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the context used to configure routes and the policies to use.
 */
public class SimpleCamelContext extends AbstractCamelContext {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleCamelContext.class);

    /**
     * Creates the {@link CamelContext} using {@link DefaultRegistry} as registry.
     * <p/>
     * Use one of the other constructors to force use an explicit registry.
     */
    public SimpleCamelContext() {
        this(true);
    }

    /**
     * Creates the {@link CamelContext} and allows to control whether the context should automatic initialize or not.
     * <p/>
     * This is used by some Camel components such as camel-cdi and camel-blueprint, however this constructor is not
     * intended for regular Camel end users.
     *
     * @param init whether to automatic initialize.
     */
    public SimpleCamelContext(boolean init) {
        super(init);
    }

    @Override
    public void disposeModel() {
        // noop
    }

    @Override
    protected HealthCheckRegistry createHealthCheckRegistry() {
        BaseServiceResolver<HealthCheckRegistry> resolver = new BaseServiceResolver<>(
                HealthCheckRegistry.FACTORY, HealthCheckRegistry.class, getBootstrapFactoryFinder());
        Optional<HealthCheckRegistry> result = resolver.resolve(getCamelContextReference());
        return result.orElse(null);
    }

    @Override
    protected TypeConverter createTypeConverter() {
        return new DefaultTypeConverter(
                getCamelContextReference(), getPackageScanClassResolver(), getInjector(),
                isLoadTypeConverters());
    }

    @Override
    protected TypeConverterRegistry createTypeConverterRegistry() {
        TypeConverter typeConverter = getTypeConverter();
        // type converter is also registry so create type converter
        if (typeConverter == null) {
            typeConverter = createTypeConverter();
        }
        if (typeConverter instanceof TypeConverterRegistry) {
            return (TypeConverterRegistry) typeConverter;
        }
        return null;
    }

    @Override
    protected Injector createInjector() {
        FactoryFinder finder = getBootstrapFactoryFinder();
        Optional<Injector> result = finder.newInstance("Injector", Injector.class);
        if (result.isPresent()) {
            return result.get();
        } else {
            return new DefaultInjector(getCamelContextReference());
        }
    }

    @Override
    protected PropertiesComponent createPropertiesComponent() {
        BaseServiceResolver<PropertiesComponent> resolver = new BaseServiceResolver<>(
                PropertiesComponent.FACTORY, PropertiesComponent.class, getBootstrapFactoryFinder());
        Optional<PropertiesComponent> result = resolver.resolve(getCamelContextReference());
        if (result.isPresent()) {
            return result.get();
        } else {
            return new org.apache.camel.component.properties.PropertiesComponent();
        }
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
        BaseServiceResolver<ModelJAXBContextFactory> resolver = new BaseServiceResolver<>(
                ModelJAXBContextFactory.FACTORY, ModelJAXBContextFactory.class, getBootstrapFactoryFinder());
        Optional<ModelJAXBContextFactory> result = resolver.resolve(getCamelContextReference());
        if (result.isPresent()) {
            return result.get();
        } else {
            throw new IllegalArgumentException(
                    "Cannot find ModelJAXBContextFactory on classpath. Add camel-xml-jaxb to classpath.");
        }
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
        BaseServiceResolver<ProcessorFactory> resolver
                = new BaseServiceResolver<>(ProcessorFactory.FACTORY, ProcessorFactory.class, getBootstrapFactoryFinder());
        Optional<ProcessorFactory> result = resolver.resolve(getCamelContextReference());
        if (result.isPresent()) {
            return result.get();
        } else {
            throw new IllegalArgumentException(
                    "Cannot find ProcessorFactory on classpath. Add camel-core-processor to classpath.");
        }
    }

    @Override
    protected InternalProcessorFactory createInternalProcessorFactory() {
        BaseServiceResolver<InternalProcessorFactory> resolver = new BaseServiceResolver<>(
                InternalProcessorFactory.FACTORY, InternalProcessorFactory.class, getBootstrapFactoryFinder());
        Optional<InternalProcessorFactory> result = resolver.resolve(getCamelContextReference());
        if (result.isPresent()) {
            return result.get();
        } else {
            throw new IllegalArgumentException(
                    "Cannot find InternalProcessorFactory on classpath. Add ccamel-core-processor to classpath.");
        }
    }

    @Override
    protected InterceptEndpointFactory createInterceptEndpointFactory() {
        return new DefaultInterceptEndpointFactory();
    }

    @Override
    protected RouteFactory createRouteFactory() {
        return new DefaultRouteFactory();
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
            packageScanClassResolver
                    = new WebSpherePackageScanClassResolver("META-INF/services/org/apache/camel/TypeConverter");
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
        BaseServiceResolver<RuntimeCamelCatalog> resolver = new BaseServiceResolver<>(
                RuntimeCamelCatalog.FACTORY, RuntimeCamelCatalog.class, getBootstrapFactoryFinder());
        Optional<RuntimeCamelCatalog> result = resolver.resolve(getCamelContextReference());
        if (result.isPresent()) {
            return result.get();
        } else {
            throw new IllegalArgumentException(
                    "Cannot find RuntimeCamelCatalog on classpath. Add camel-core-catalog to classpath.");
        }
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
        BaseServiceResolver<HeadersMapFactory> resolver
                = new BaseServiceResolver<>(HeadersMapFactory.FACTORY, HeadersMapFactory.class, getBootstrapFactoryFinder());
        Optional<HeadersMapFactory> result = resolver.resolve(getCamelContextReference());
        if (result.isPresent()) {
            return result.get();
        } else {
            return new DefaultHeadersMapFactory();
        }
    }

    @Override
    protected BeanProxyFactory createBeanProxyFactory() {
        BaseServiceResolver<BeanProxyFactory> resolver
                = new BaseServiceResolver<>(BeanProxyFactory.FACTORY, BeanProxyFactory.class, getBootstrapFactoryFinder());
        Optional<BeanProxyFactory> result = resolver.resolve(getCamelContextReference());
        if (result.isPresent()) {
            return result.get();
        } else {
            throw new IllegalArgumentException("Cannot find BeanProxyFactory on classpath. Add camel-bean to classpath.");
        }
    }

    @Override
    protected AnnotationBasedProcessorFactory createAnnotationBasedProcessorFactory() {
        BaseServiceResolver<AnnotationBasedProcessorFactory> resolver = new BaseServiceResolver<>(
                AnnotationBasedProcessorFactory.FACTORY, AnnotationBasedProcessorFactory.class, getBootstrapFactoryFinder());
        Optional<AnnotationBasedProcessorFactory> result = resolver.resolve(getCamelContextReference());
        if (result.isPresent()) {
            return result.get();
        } else {
            throw new IllegalArgumentException(
                    "Cannot find AnnotationBasedProcessorFactory on classpath. Add camel-core-processor to classpath.");
        }
    }

    @Override
    protected DeferServiceFactory createDeferServiceFactory() {
        BaseServiceResolver<DeferServiceFactory> resolver = new BaseServiceResolver<>(
                DeferServiceFactory.FACTORY, DeferServiceFactory.class, getBootstrapFactoryFinder());
        Optional<DeferServiceFactory> result = resolver.resolve(getCamelContextReference());
        if (result.isPresent()) {
            return result.get();
        } else {
            throw new IllegalArgumentException(
                    "Cannot find DeferServiceFactory on classpath. Add camel-core-processor to classpath.");
        }
    }

    @Override
    protected BeanProcessorFactory createBeanProcessorFactory() {
        BaseServiceResolver<BeanProcessorFactory> resolver = new BaseServiceResolver<>(
                BeanProcessorFactory.FACTORY, BeanProcessorFactory.class, getBootstrapFactoryFinder());
        Optional<BeanProcessorFactory> result = resolver.resolve(getCamelContextReference());
        if (result.isPresent()) {
            return result.get();
        } else {
            throw new IllegalArgumentException("Cannot find BeanProcessorFactory on classpath. Add camel-bean to classpath.");
        }
    }

    @Override
    protected BeanIntrospection createBeanIntrospection() {
        return new DefaultBeanIntrospection();
    }

    @Override
    protected XMLRoutesDefinitionLoader createXMLRoutesDefinitionLoader() {
        BaseServiceResolver<XMLRoutesDefinitionLoader> resolver = new BaseServiceResolver<>(
                XMLRoutesDefinitionLoader.FACTORY, XMLRoutesDefinitionLoader.class, getBootstrapFactoryFinder());
        Optional<XMLRoutesDefinitionLoader> result = resolver.resolve(getCamelContextReference());
        if (result.isPresent()) {
            return result.get();
        } else {
            throw new IllegalArgumentException(
                    "Cannot find ModelJAXBContextFactory on classpath. Add either camel-xml-io or camel-xml-jaxb to classpath.");
        }
    }

    @Override
    protected ModelToXMLDumper createModelToXMLDumper() {
        BaseServiceResolver<ModelToXMLDumper> resolver
                = new BaseServiceResolver<>(ModelToXMLDumper.FACTORY, ModelToXMLDumper.class, getBootstrapFactoryFinder());
        Optional<ModelToXMLDumper> result = resolver.resolve(getCamelContextReference());
        if (result.isPresent()) {
            return result.get();
        } else {
            throw new IllegalArgumentException("Cannot find ModelToXMLDumper on classpath. Add camel-xml-jaxb to classpath.");
        }
    }

    @Override
    protected RestBindingJaxbDataFormatFactory createRestBindingJaxbDataFormatFactory() {
        BaseServiceResolver<RestBindingJaxbDataFormatFactory> resolver = new BaseServiceResolver<>(
                RestBindingJaxbDataFormatFactory.FACTORY, RestBindingJaxbDataFormatFactory.class, getBootstrapFactoryFinder());
        Optional<RestBindingJaxbDataFormatFactory> result = resolver.resolve(getCamelContextReference());
        if (result.isPresent()) {
            return result.get();
        } else {
            throw new IllegalArgumentException(
                    "Cannot find RestBindingJaxbDataFormatFactory on classpath. Add camel-jaxb to classpath.");
        }
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
    protected UriFactoryResolver createUriFactoryResolver() {
        return new DefaultUriFactoryResolver();
    }

    @Override
    protected RestRegistryFactory createRestRegistryFactory() {
        BaseServiceResolver<RestRegistryFactory> resolver = new BaseServiceResolver<>(
                RestRegistryFactory.FACTORY, RestRegistryFactory.class, getBootstrapFactoryFinder());
        Optional<RestRegistryFactory> result = resolver.resolve(getCamelContextReference());
        if (result.isPresent()) {
            return result.get();
        } else {
            throw new IllegalArgumentException("Cannot find RestRegistryFactory on classpath. Add camel-rest to classpath.");
        }
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
        BaseServiceResolver<ReactiveExecutor> resolver
                = new BaseServiceResolver<>(ReactiveExecutor.FACTORY, ReactiveExecutor.class, getBootstrapFactoryFinder());
        Optional<ReactiveExecutor> result = resolver.resolve(getCamelContextReference());
        if (result.isPresent()) {
            return result.get();
        } else {
            return new DefaultReactiveExecutor();
        }
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

    @Override
    public String addRouteFromTemplate(String routeId, String routeTemplateId, Map<String, Object> parameters)
            throws Exception {
        throw new UnsupportedOperationException();
    }

}
