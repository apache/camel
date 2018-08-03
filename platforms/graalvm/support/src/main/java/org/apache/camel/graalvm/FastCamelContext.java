package org.apache.camel.graalvm;

import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.TypeConverter;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.impl.AbstractCamelContext;
import org.apache.camel.impl.DefaultAsyncProcessorAwaitManager;
import org.apache.camel.impl.DefaultCamelContextNameStrategy;
import org.apache.camel.impl.DefaultClassResolver;
import org.apache.camel.impl.DefaultComponentResolver;
import org.apache.camel.impl.DefaultDataFormatResolver;
import org.apache.camel.impl.DefaultEndpointRegistry;
import org.apache.camel.impl.DefaultExecutorServiceManager;
import org.apache.camel.impl.DefaultFactoryFinderResolver;
import org.apache.camel.impl.DefaultHeadersMapFactory;
import org.apache.camel.impl.DefaultInflightRepository;
import org.apache.camel.impl.DefaultInjector;
import org.apache.camel.impl.DefaultLanguageResolver;
import org.apache.camel.impl.DefaultManagementNameStrategy;
import org.apache.camel.impl.DefaultMessageHistoryFactory;
import org.apache.camel.impl.DefaultModelJAXBContextFactory;
import org.apache.camel.impl.DefaultNodeIdFactory;
import org.apache.camel.impl.DefaultPackageScanClassResolver;
import org.apache.camel.impl.DefaultProcessorFactory;
import org.apache.camel.impl.DefaultRouteController;
import org.apache.camel.impl.DefaultTransformerRegistry;
import org.apache.camel.impl.DefaultUnitOfWorkFactory;
import org.apache.camel.impl.DefaultValidatorRegistry;
import org.apache.camel.impl.EndpointKey;
import org.apache.camel.impl.ProcessorEndpoint;
import org.apache.camel.impl.SharedPollingConsumerServicePool;
import org.apache.camel.impl.SharedProducerServicePool;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.impl.SimpleUuidGenerator;
import org.apache.camel.impl.health.DefaultHealthCheckRegistry;
import org.apache.camel.impl.transformer.TransformerKey;
import org.apache.camel.impl.validator.ValidatorKey;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.apache.camel.model.validator.ValidatorDefinition;
import org.apache.camel.runtimecatalog.DefaultRuntimeCamelCatalog;
import org.apache.camel.runtimecatalog.RuntimeCamelCatalog;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.spi.CamelContextNameStrategy;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.camel.spi.EndpointRegistry;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.spi.HeadersMapFactory;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.LanguageResolver;
import org.apache.camel.spi.ManagementMBeanAssembler;
import org.apache.camel.spi.ManagementNameStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.MessageHistoryFactory;
import org.apache.camel.spi.ModelJAXBContextFactory;
import org.apache.camel.spi.NodeIdFactory;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.ServicePool;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.TransformerRegistry;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.spi.ValidatorRegistry;
import org.apache.camel.util.EventHelper;

public class FastCamelContext extends AbstractCamelContext {

    public FastCamelContext(SimpleRegistry registry) {
        super(registry);
    }

    @Override
    public void addRoutes(final RoutesBuilder builder) throws Exception {
        builder.addRoutesToCamelContext(this);
    }

    @Override
    protected synchronized void doStart() throws Exception {
        try {
            doStartCamel();
        } catch (Exception e) {
            // fire event that we failed to start
            EventHelper.notifyCamelContextStartupFailed(this, e);
            // rethrow cause
            throw e;
        }
    }

    @Override
    protected Injector createInjector() {
        return new DefaultInjector(this);
    }

    @Override
    protected UuidGenerator createDefaultUuidGenerator() {
        return new SimpleUuidGenerator();
    }

    @Override
    protected ManagementStrategy createManagementStrategy() {
        return new NoManagementStrategy();
    }

    @Override
    protected TypeConverter createTypeConverter() {
        FastTypeConverterRegistry answer
                = new FastTypeConverterRegistry(this, getPackageScanClassResolver(), getInjector(), getDefaultFactoryFinder());
        setTypeConverterRegistry(answer);
        return answer;
    }

    @Override
    public Boolean isTypeConverterStatisticsEnabled() {
        return null;
    }

    @Override
    protected ShutdownStrategy createShutdownStrategy() {
        return new NoShutdownStrategy();
    }

//    @Override
//    protected ExecutorServiceManager createExecutorServiceManager() {
//        return new FastExecutorServiceManager(this);
//    }

    protected ComponentResolver createComponentResolver() {
        return new DefaultComponentResolver();
    }

    protected Registry createRegistry() {
        throw new IllegalStateException();
    }

    protected Endpoint createEndpoint(String uri) {
        Object value = getRegistry().lookupByName(uri);
        if (value instanceof Endpoint) {
            return (Endpoint) value;
        } else if (value instanceof Processor) {
            return new ProcessorEndpoint(uri, this, (Processor) value);
        } else if (value != null) {
            return convertBeanToEndpoint(uri, value);
        }
        return null;
    }

    protected ModelJAXBContextFactory createModelJAXBContextFactory() {
        return new DefaultModelJAXBContextFactory();
    }

    public String toString() {
        return "FastCamelContext(" + getName() + ")";
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

    protected PackageScanClassResolver createPackageScanClassResolver() {
        return new DefaultPackageScanClassResolver();
    }

    protected ExecutorServiceManager createExecutorServiceManager() {
        return new DefaultExecutorServiceManager(this);
    }

    protected ServicePool<Endpoint, Producer> createProducerServicePool() {
        return new SharedProducerServicePool(100);
    }

    protected ServicePool<Endpoint, PollingConsumer> createPollingConsumerServicePool() {
        return new SharedPollingConsumerServicePool(100);
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
        return new DefaultHeadersMapFactory();
    }

    protected LanguageResolver createLanguageResolver() {
        return new DefaultLanguageResolver();
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

    protected ManagementMBeanAssembler createManagementMBeanAssembler() {
        return null;
    }
}
