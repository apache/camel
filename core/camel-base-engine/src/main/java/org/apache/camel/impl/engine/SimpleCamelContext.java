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
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RouteTemplateContext;
import org.apache.camel.TypeConverter;
import org.apache.camel.catalog.RuntimeCamelCatalog;
import org.apache.camel.console.DevConsoleRegistry;
import org.apache.camel.console.DevConsoleResolver;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckResolver;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.spi.AnnotationBasedProcessorFactory;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.spi.BeanProcessorFactory;
import org.apache.camel.spi.BeanProxyFactory;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.CamelContextNameStrategy;
import org.apache.camel.spi.CamelDependencyInjectionAnnotationFactory;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.CliConnectorFactory;
import org.apache.camel.spi.ComponentNameResolver;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.ConfigurerResolver;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.camel.spi.DeferServiceFactory;
import org.apache.camel.spi.DumpRoutesStrategy;
import org.apache.camel.spi.EndpointRegistry;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.spi.ExchangeFactoryManager;
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
import org.apache.camel.spi.ModelToYAMLDumper;
import org.apache.camel.spi.ModelineFactory;
import org.apache.camel.spi.NodeIdFactory;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.PackageScanResourceResolver;
import org.apache.camel.spi.PeriodTaskResolver;
import org.apache.camel.spi.PeriodTaskScheduler;
import org.apache.camel.spi.ProcessorExchangeFactory;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.ReactiveExecutor;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.ResourceLoader;
import org.apache.camel.spi.RestBindingJaxbDataFormatFactory;
import org.apache.camel.spi.RestRegistryFactory;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.RouteFactory;
import org.apache.camel.spi.RoutesLoader;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.spi.Tracer;
import org.apache.camel.spi.TransformerRegistry;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.apache.camel.spi.UriFactoryResolver;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.spi.ValidatorRegistry;
import org.apache.camel.spi.VariableRepositoryFactory;
import org.apache.camel.support.DefaultRegistry;
import org.apache.camel.support.DefaultUuidGenerator;
import org.apache.camel.support.NormalizedUri;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResolverHelper;
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
     * Note: Not for end users - this method is used internally by camel-blueprint/camel-cdi
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
    public void doBuild() throws Exception {
        super.doBuild();

        getCamelContextExtension().addContextPlugin(CliConnectorFactory.class, createCliConnectorFactory());
        getCamelContextExtension().addContextPlugin(ScheduledExecutorService.class, createErrorHandlerExecutorService());
    }

    @Override
    protected HealthCheckRegistry createHealthCheckRegistry() {
        Optional<HealthCheckRegistry> result = ResolverHelper.resolveService(
                getCamelContextReference(),
                getCamelContextExtension().getBootstrapFactoryFinder(),
                HealthCheckRegistry.FACTORY,
                HealthCheckRegistry.class);

        return result.orElse(null);
    }

    @Override
    protected DevConsoleRegistry createDevConsoleRegistry() {
        Optional<DevConsoleRegistry> result = ResolverHelper.resolveService(
                getCamelContextReference(),
                getCamelContextExtension().getBootstrapFactoryFinder(),
                DevConsoleRegistry.FACTORY,
                DevConsoleRegistry.class);

        return result.orElse(null);
    }

    @Override
    protected TypeConverter createTypeConverter() {
        return new DefaultTypeConverter(
                getCamelContextReference(), PluginHelper.getPackageScanClassResolver(this), getInjector(),
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
        FactoryFinder finder = getCamelContextExtension().getBootstrapFactoryFinder();
        Optional<Injector> result = finder.newInstance("Injector", Injector.class);
        return result.orElseGet(() -> new DefaultInjector(getCamelContextReference()));
    }

    @Override
    protected PropertiesComponent createPropertiesComponent() {
        Optional<PropertiesComponent> result = ResolverHelper.resolveService(
                getCamelContextReference(),
                getCamelContextExtension().getBootstrapFactoryFinder(),
                PropertiesComponent.FACTORY,
                PropertiesComponent.class);

        return result.orElseGet(org.apache.camel.component.properties.PropertiesComponent::new);
    }

    @Override
    protected CamelBeanPostProcessor createBeanPostProcessor() {
        return new DefaultCamelBeanPostProcessor(getCamelContextReference());
    }

    @Override
    protected CamelDependencyInjectionAnnotationFactory createDependencyInjectionAnnotationFactory() {
        return new DefaultDependencyInjectionAnnotationFactory(getCamelContextReference());
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
        Optional<ModelJAXBContextFactory> result = ResolverHelper.resolveService(
                getCamelContextReference(),
                getCamelContextExtension().getBootstrapFactoryFinder(),
                ModelJAXBContextFactory.FACTORY,
                ModelJAXBContextFactory.class);

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
    protected ModelineFactory createModelineFactory() {
        Optional<ModelineFactory> result = ResolverHelper.resolveService(
                getCamelContextReference(),
                getCamelContextExtension().getBootstrapFactoryFinder(),
                ModelineFactory.FACTORY,
                ModelineFactory.class);

        if (result.isPresent()) {
            return result.get();
        } else {
            throw new IllegalArgumentException(
                    "Cannot find ModelineFactory on classpath. Add camel-dsl-modeline to classpath.");
        }
    }

    @Override
    protected PeriodTaskResolver createPeriodTaskResolver() {
        // we need a factory finder
        FactoryFinder finder = PluginHelper.getFactoryFinderResolver(getCamelContextExtension())
                .resolveBootstrapFactoryFinder(getClassResolver(), PeriodTaskResolver.RESOURCE_PATH);
        return new DefaultPeriodTaskResolver(finder);
    }

    @Override
    protected PeriodTaskScheduler createPeriodTaskScheduler() {
        return new DefaultPeriodTaskScheduler();
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
        Optional<ProcessorFactory> result = ResolverHelper.resolveService(
                getCamelContextReference(),
                getCamelContextExtension().getBootstrapFactoryFinder(),
                ProcessorFactory.FACTORY,
                ProcessorFactory.class);

        if (result.isPresent()) {
            return result.get();
        } else {
            throw new IllegalArgumentException(
                    "Cannot find ProcessorFactory on classpath. Add camel-core-processor to classpath.");
        }
    }

    @Override
    protected InternalProcessorFactory createInternalProcessorFactory() {
        Optional<InternalProcessorFactory> result = ResolverHelper.resolveService(
                getCamelContextReference(),
                getCamelContextExtension().getBootstrapFactoryFinder(),
                InternalProcessorFactory.FACTORY,
                InternalProcessorFactory.class);

        if (result.isPresent()) {
            return result.get();
        } else {
            throw new IllegalArgumentException(
                    "Cannot find InternalProcessorFactory on classpath. Add camel-core-processor to classpath.");
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
    protected HealthCheckResolver createHealthCheckResolver() {
        return new DefaultHealthCheckResolver();
    }

    @Override
    protected DevConsoleResolver createDevConsoleResolver() {
        return new DefaultDevConsoleResolver();
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
        Optional<RuntimeCamelCatalog> result = ResolverHelper.resolveService(
                getCamelContextReference(),
                getCamelContextExtension().getBootstrapFactoryFinder(),
                RuntimeCamelCatalog.FACTORY,
                RuntimeCamelCatalog.class);

        if (result.isPresent()) {
            return result.get();
        } else {
            throw new IllegalArgumentException(
                    "Cannot find RuntimeCamelCatalog on classpath. Add camel-core-catalog to classpath.");
        }
    }

    @Override
    protected DumpRoutesStrategy createDumpRoutesStrategy() {
        DumpRoutesStrategy answer = getCamelContextReference().hasService(DumpRoutesStrategy.class);
        if (answer != null) {
            return answer;
        }

        // is there any custom which we prioritize over default
        Optional<DumpRoutesStrategy> result = ResolverHelper.resolveService(
                getCamelContextReference(),
                getCamelContextExtension().getBootstrapFactoryFinder(),
                DumpRoutesStrategy.FACTORY,
                DumpRoutesStrategy.class);

        if (result.isEmpty()) {
            // lookup default factory
            result = ResolverHelper.resolveService(
                    getCamelContextReference(),
                    getCamelContextExtension().getBootstrapFactoryFinder(),
                    "default-" + DumpRoutesStrategy.FACTORY,
                    DumpRoutesStrategy.class);
        }

        if (result.isPresent()) {
            return result.get();
        } else {
            throw new IllegalArgumentException(
                    "Cannot find DumpRoutesStrategy on classpath. Add camel-core-engine to classpath.");
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
        Optional<HeadersMapFactory> result = ResolverHelper.resolveService(
                getCamelContextReference(),
                getCamelContextExtension().getBootstrapFactoryFinder(),
                HeadersMapFactory.FACTORY,
                HeadersMapFactory.class);

        return result.orElseGet(DefaultHeadersMapFactory::new);
    }

    private CliConnectorFactory createCliConnectorFactory() {
        // lookup in registry first
        CliConnectorFactory ccf = getCamelContextReference().getRegistry().findSingleByType(CliConnectorFactory.class);
        if (ccf != null) {
            return ccf;
        }
        // then classpath scanning
        Optional<CliConnectorFactory> result = ResolverHelper.resolveService(
                getCamelContextReference(),
                getCamelContextExtension().getBootstrapFactoryFinder(),
                CliConnectorFactory.FACTORY,
                CliConnectorFactory.class);
        // cli-connector is optional
        return result.orElse(null);
    }

    @Override
    protected BeanProxyFactory createBeanProxyFactory() {
        Optional<BeanProxyFactory> result = ResolverHelper.resolveService(
                getCamelContextReference(),
                getCamelContextExtension().getBootstrapFactoryFinder(),
                BeanProxyFactory.FACTORY,
                BeanProxyFactory.class);

        if (result.isPresent()) {
            return result.get();
        } else {
            throw new IllegalArgumentException("Cannot find BeanProxyFactory on classpath. Add camel-bean to classpath.");
        }
    }

    @Override
    protected AnnotationBasedProcessorFactory createAnnotationBasedProcessorFactory() {
        Optional<AnnotationBasedProcessorFactory> result = ResolverHelper.resolveService(
                getCamelContextReference(),
                getCamelContextExtension().getBootstrapFactoryFinder(),
                AnnotationBasedProcessorFactory.FACTORY,
                AnnotationBasedProcessorFactory.class);

        if (result.isPresent()) {
            return result.get();
        } else {
            throw new IllegalArgumentException(
                    "Cannot find AnnotationBasedProcessorFactory on classpath. Add camel-core-processor to classpath.");
        }
    }

    @Override
    protected DeferServiceFactory createDeferServiceFactory() {
        Optional<DeferServiceFactory> result = ResolverHelper.resolveService(
                getCamelContextReference(),
                getCamelContextExtension().getBootstrapFactoryFinder(),
                DeferServiceFactory.FACTORY,
                DeferServiceFactory.class);

        if (result.isPresent()) {
            return result.get();
        } else {
            throw new IllegalArgumentException(
                    "Cannot find DeferServiceFactory on classpath. Add camel-core-processor to classpath.");
        }
    }

    @Override
    protected BeanProcessorFactory createBeanProcessorFactory() {
        Optional<BeanProcessorFactory> result = ResolverHelper.resolveService(
                getCamelContextReference(),
                getCamelContextExtension().getBootstrapFactoryFinder(),
                BeanProcessorFactory.FACTORY,
                BeanProcessorFactory.class);

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
    protected RoutesLoader createRoutesLoader() {
        Optional<RoutesLoader> result = ResolverHelper.resolveService(
                getCamelContextReference(),
                getCamelContextExtension().getBootstrapFactoryFinder(),
                RoutesLoader.FACTORY,
                RoutesLoader.class);

        return result.orElseGet(DefaultRoutesLoader::new);
    }

    @Override
    protected ResourceLoader createResourceLoader() {
        Optional<ResourceLoader> result = ResolverHelper.resolveService(
                getCamelContextReference(),
                getCamelContextExtension().getBootstrapFactoryFinder(),
                ResourceLoader.FACTORY,
                ResourceLoader.class);

        return result.orElseGet(DefaultResourceLoader::new);
    }

    @Override
    protected ModelToXMLDumper createModelToXMLDumper() {
        Optional<ModelToXMLDumper> result = ResolverHelper.resolveService(
                getCamelContextReference(),
                getCamelContextExtension().getBootstrapFactoryFinder(),
                ModelToXMLDumper.FACTORY,
                ModelToXMLDumper.class);

        if (result.isPresent()) {
            return result.get();
        } else {
            throw new IllegalArgumentException("Cannot find ModelToXMLDumper on classpath. Add camel-xml-io to classpath.");
        }
    }

    @Override
    protected ModelToYAMLDumper createModelToYAMLDumper() {
        Optional<ModelToYAMLDumper> result = ResolverHelper.resolveService(
                getCamelContextReference(),
                getCamelContextExtension().getBootstrapFactoryFinder(),
                ModelToYAMLDumper.FACTORY,
                ModelToYAMLDumper.class);

        if (result.isPresent()) {
            return result.get();
        } else {
            throw new IllegalArgumentException("Cannot find ModelToYAMLDumper on classpath. Add camel-yaml-io to classpath.");
        }
    }

    @Override
    protected RestBindingJaxbDataFormatFactory createRestBindingJaxbDataFormatFactory() {
        Optional<RestBindingJaxbDataFormatFactory> result = ResolverHelper.resolveService(
                getCamelContextReference(),
                getCamelContextExtension().getBootstrapFactoryFinder(),
                RestBindingJaxbDataFormatFactory.FACTORY,
                RestBindingJaxbDataFormatFactory.class);

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
            tracer = getRegistry().findSingleByType(Tracer.class);
        }
        if (tracer == null) {
            tracer = getCamelContextExtension().getContextPlugin(Tracer.class);
        }
        if (tracer == null) {
            tracer = new DefaultTracer();
            tracer.setEnabled(isTracing());
            tracer.setStandby(isTracingStandby());
            // enable both rest/templates if templates is enabled (we only want 1 public option)
            boolean restOrTemplates = isTracingTemplates();
            tracer.setTraceTemplates(restOrTemplates);
            tracer.setTraceRests(restOrTemplates);
            getCamelContextExtension().addContextPlugin(Tracer.class, tracer);
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
        Optional<RestRegistryFactory> result = ResolverHelper.resolveService(
                getCamelContextReference(),
                getCamelContextExtension().getBootstrapFactoryFinder(),
                RestRegistryFactory.FACTORY,
                RestRegistryFactory.class);

        if (result.isPresent()) {
            return result.get();
        } else {
            throw new IllegalArgumentException("Cannot find RestRegistryFactory on classpath. Add camel-rest to classpath.");
        }
    }

    @Override
    protected EndpointRegistry<NormalizedUri> createEndpointRegistry(Map<NormalizedUri, Endpoint> endpoints) {
        return new DefaultEndpointRegistry(getCamelContextReference(), endpoints);
    }

    @Override
    protected StreamCachingStrategy createStreamCachingStrategy() {
        return new DefaultStreamCachingStrategy();
    }

    @Override
    protected ExchangeFactory createExchangeFactory() {
        Optional<ExchangeFactory> result = ResolverHelper.resolveService(
                getCamelContextReference(),
                getCamelContextExtension().getBootstrapFactoryFinder(),
                ExchangeFactory.FACTORY,
                ExchangeFactory.class);

        return result.orElseGet(PrototypeExchangeFactory::new);
    }

    @Override
    protected ExchangeFactoryManager createExchangeFactoryManager() {
        return new DefaultExchangeFactoryManager();
    }

    @Override
    protected ProcessorExchangeFactory createProcessorExchangeFactory() {
        Optional<ProcessorExchangeFactory> result = ResolverHelper.resolveService(
                getCamelContextReference(),
                getCamelContextExtension().getBootstrapFactoryFinder(),
                ProcessorExchangeFactory.FACTORY,
                ProcessorExchangeFactory.class);

        return result.orElseGet(PrototypeProcessorExchangeFactory::new);
    }

    @Override
    protected ReactiveExecutor createReactiveExecutor() {
        Optional<ReactiveExecutor> result = ResolverHelper.resolveService(
                getCamelContextReference(),
                getCamelContextExtension().getBootstrapFactoryFinder(),
                ReactiveExecutor.FACTORY,
                ReactiveExecutor.class);

        return result.orElseGet(DefaultReactiveExecutor::new);
    }

    @Override
    protected ValidatorRegistry<ValidatorKey> createValidatorRegistry() {
        return new DefaultValidatorRegistry(getCamelContextReference());
    }

    @Override
    protected VariableRepositoryFactory createVariableRepositoryFactory() {
        return new DefaultVariableRepositoryFactory(getCamelContextReference());
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

    @Override
    public String addRouteFromTemplate(String routeId, String routeTemplateId, String prefixId, Map<String, Object> parameters)
            throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public String addRouteFromTemplate(
            String routeId, String routeTemplateId, String prefixId, RouteTemplateContext routeTemplateContext)
            throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeRouteTemplates(String pattern) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getTestExcludeRoutes() {
        throw new UnsupportedOperationException();
    }
}
