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
package org.apache.camel.support;

import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.catalog.RuntimeCamelCatalog;
import org.apache.camel.console.DevConsoleResolver;
import org.apache.camel.health.HealthCheckResolver;
import org.apache.camel.spi.AnnotationBasedProcessorFactory;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.spi.BeanProcessorFactory;
import org.apache.camel.spi.BeanProxyFactory;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.CamelDependencyInjectionAnnotationFactory;
import org.apache.camel.spi.ComponentNameResolver;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.ConfigurerResolver;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.camel.spi.DeferServiceFactory;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.spi.InterceptEndpointFactory;
import org.apache.camel.spi.InternalProcessorFactory;
import org.apache.camel.spi.LanguageResolver;
import org.apache.camel.spi.ModelJAXBContextFactory;
import org.apache.camel.spi.ModelToXMLDumper;
import org.apache.camel.spi.ModelToYAMLDumper;
import org.apache.camel.spi.ModelineFactory;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.PackageScanResourceResolver;
import org.apache.camel.spi.PeriodTaskResolver;
import org.apache.camel.spi.PeriodTaskScheduler;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.ResourceLoader;
import org.apache.camel.spi.RestBindingJaxbDataFormatFactory;
import org.apache.camel.spi.RouteFactory;
import org.apache.camel.spi.RoutesLoader;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.apache.camel.spi.UriFactoryResolver;

/**
 * Convenient helper to get easy access to various extensions from {@link ExtendedCamelContext}.
 */
public final class PluginHelper {

    private PluginHelper() {
    }

    /**
     * Returns the bean post processor used to do any bean customization.
     */
    public static CamelBeanPostProcessor getBeanPostProcessor(CamelContext camelContext) {
        return getBeanPostProcessor(camelContext.getCamelContextExtension());
    }

    /**
     * Returns the bean post processor used to do any bean customization.
     */
    public static CamelBeanPostProcessor getBeanPostProcessor(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(CamelBeanPostProcessor.class);
    }

    /**
     * Returns the annotation dependency injection factory.
     */
    public static CamelDependencyInjectionAnnotationFactory getDependencyInjectionAnnotationFactory(CamelContext camelContext) {
        return getDependencyInjectionAnnotationFactory(camelContext.getCamelContextExtension());
    }

    /**
     * Returns the annotation dependency injection factory.
     */
    public static CamelDependencyInjectionAnnotationFactory getDependencyInjectionAnnotationFactory(
            ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(CamelDependencyInjectionAnnotationFactory.class);
    }

    /**
     * Gets the {@link ComponentResolver} to use.
     */
    public static ComponentResolver getComponentResolver(CamelContext camelContext) {
        return getComponentResolver(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the {@link ComponentResolver} to use.
     */
    public static ComponentResolver getComponentResolver(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(ComponentResolver.class);
    }

    /**
     * Gets the {@link ComponentNameResolver} to use.
     */
    public static ComponentNameResolver getComponentNameResolver(CamelContext camelContext) {
        return getComponentNameResolver(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the {@link ComponentNameResolver} to use.
     */
    public static ComponentNameResolver getComponentNameResolver(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(ComponentNameResolver.class);
    }

    /**
     * Gets the {@link LanguageResolver} to use.
     */
    public static LanguageResolver getLanguageResolver(CamelContext camelContext) {
        return getLanguageResolver(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the {@link LanguageResolver} to use.
     */
    public static LanguageResolver getLanguageResolver(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(LanguageResolver.class);
    }

    /**
     * Gets the {@link ConfigurerResolver} to use.
     */
    public static ConfigurerResolver getConfigurerResolver(CamelContext camelContext) {
        return getConfigurerResolver(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the {@link ConfigurerResolver} to use.
     */
    public static ConfigurerResolver getConfigurerResolver(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(ConfigurerResolver.class);
    }

    /**
     * Gets the {@link UriFactoryResolver} to use.
     */
    public static UriFactoryResolver getUriFactoryResolver(CamelContext camelContext) {
        return getUriFactoryResolver(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the {@link UriFactoryResolver} to use.
     */
    public static UriFactoryResolver getUriFactoryResolver(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(UriFactoryResolver.class);
    }

    /**
     * Gets the default shared thread pool for error handlers which leverages this for asynchronous redelivery tasks.
     */
    public static ScheduledExecutorService getErrorHandlerExecutorService(CamelContext camelContext) {
        return getErrorHandlerExecutorService(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the default shared thread pool for error handlers which leverages this for asynchronous redelivery tasks.
     */
    public static ScheduledExecutorService getErrorHandlerExecutorService(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(ScheduledExecutorService.class);
    }

    /**
     * Gets the bootstrap {@link ConfigurerResolver} to use. This bootstrap resolver is only intended to be used during
     * bootstrap (starting) CamelContext.
     */
    public static ConfigurerResolver getBootstrapConfigurerResolver(CamelContext camelContext) {
        return getBootstrapConfigurerResolver(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the bootstrap {@link ConfigurerResolver} to use. This bootstrap resolver is only intended to be used during
     * bootstrap (starting) CamelContext.
     */
    public static ConfigurerResolver getBootstrapConfigurerResolver(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(ConfigurerResolver.class);
    }

    /**
     * Gets the factory finder resolver to use
     */
    public static FactoryFinderResolver getFactoryFinderResolver(CamelContext camelContext) {
        return getFactoryFinderResolver(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the factory finder resolver to use
     */
    public static FactoryFinderResolver getFactoryFinderResolver(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(FactoryFinderResolver.class);
    }

    /**
     * Returns the package scanning class resolver
     */
    public static PackageScanClassResolver getPackageScanClassResolver(CamelContext camelContext) {
        return getPackageScanClassResolver(camelContext.getCamelContextExtension());
    }

    /**
     * Returns the package scanning class resolver
     */
    public static PackageScanClassResolver getPackageScanClassResolver(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(PackageScanClassResolver.class);
    }

    /**
     * Returns the package scanning resource resolver
     */
    public static PackageScanResourceResolver getPackageScanResourceResolver(CamelContext camelContext) {
        return getPackageScanResourceResolver(camelContext.getCamelContextExtension());
    }

    /**
     * Returns the package scanning resource resolver
     */
    public static PackageScanResourceResolver getPackageScanResourceResolver(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(PackageScanResourceResolver.class);
    }

    /**
     * Returns the JAXB Context factory used to create Models.
     */
    public static ModelJAXBContextFactory getModelJAXBContextFactory(CamelContext camelContext) {
        return getModelJAXBContextFactory(camelContext.getCamelContextExtension());
    }

    /**
     * Returns the JAXB Context factory used to create Models.
     */
    public static ModelJAXBContextFactory getModelJAXBContextFactory(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(ModelJAXBContextFactory.class);
    }

    /**
     * Gets the {@link ModelineFactory}.
     */
    public static ModelineFactory getModelineFactory(CamelContext camelContext) {
        return getModelineFactory(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the {@link ModelineFactory}.
     */
    public static ModelineFactory getModelineFactory(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(ModelineFactory.class);
    }

    /**
     * Gets the current data format resolver
     */
    public static DataFormatResolver getDataFormatResolver(CamelContext camelContext) {
        return getDataFormatResolver(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the current data format resolver
     */
    public static DataFormatResolver getDataFormatResolver(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(DataFormatResolver.class);
    }

    /**
     * Gets the period task resolver
     */
    public static PeriodTaskResolver getPeriodTaskResolver(CamelContext camelContext) {
        return getPeriodTaskResolver(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the period task resolver
     */
    public static PeriodTaskResolver getPeriodTaskResolver(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(PeriodTaskResolver.class);
    }

    /**
     * Gets the period task scheduler
     */
    public static PeriodTaskScheduler getPeriodTaskScheduler(CamelContext camelContext) {
        return getPeriodTaskScheduler(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the period task scheduler
     */
    public static PeriodTaskScheduler getPeriodTaskScheduler(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(PeriodTaskScheduler.class);
    }

    /**
     * Gets the current health check resolver
     */
    public static HealthCheckResolver getHealthCheckResolver(CamelContext camelContext) {
        return getHealthCheckResolver(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the current health check resolver
     */
    public static HealthCheckResolver getHealthCheckResolver(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(HealthCheckResolver.class);
    }

    /**
     * Gets the current dev console resolver
     */
    public static DevConsoleResolver getDevConsoleResolver(CamelContext camelContext) {
        return getDevConsoleResolver(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the current dev console resolver
     */
    public static DevConsoleResolver getDevConsoleResolver(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(DevConsoleResolver.class);
    }

    /**
     * Gets the current {@link org.apache.camel.spi.ProcessorFactory}
     *
     * @return the factory, can be <tt>null</tt> if no custom factory has been set
     */
    public static ProcessorFactory getProcessorFactory(CamelContext camelContext) {
        return getProcessorFactory(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the current {@link org.apache.camel.spi.ProcessorFactory}
     *
     * @return the factory, can be <tt>null</tt> if no custom factory has been set
     */
    public static ProcessorFactory getProcessorFactory(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(ProcessorFactory.class);
    }

    /**
     * Gets the current {@link org.apache.camel.spi.InternalProcessorFactory}
     */
    public static InternalProcessorFactory getInternalProcessorFactory(CamelContext camelContext) {
        return getInternalProcessorFactory(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the current {@link org.apache.camel.spi.InternalProcessorFactory}
     */
    public static InternalProcessorFactory getInternalProcessorFactory(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(InternalProcessorFactory.class);
    }

    /**
     * Gets the current {@link org.apache.camel.spi.InterceptEndpointFactory}
     */
    public static InterceptEndpointFactory getInterceptEndpointFactory(CamelContext camelContext) {
        return getInterceptEndpointFactory(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the current {@link org.apache.camel.spi.InterceptEndpointFactory}
     */
    public static InterceptEndpointFactory getInterceptEndpointFactory(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(InterceptEndpointFactory.class);
    }

    /**
     * Gets the current {@link org.apache.camel.spi.RouteFactory}
     */
    public static RouteFactory getRouteFactory(CamelContext camelContext) {
        return getRouteFactory(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the current {@link org.apache.camel.spi.RouteFactory}
     */
    public static RouteFactory getRouteFactory(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(RouteFactory.class);
    }

    /**
     * Gets the {@link RoutesLoader} to be used.
     */
    public static RoutesLoader getRoutesLoader(CamelContext camelContext) {
        return getRoutesLoader(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the {@link RoutesLoader} to be used.
     */
    public static RoutesLoader getRoutesLoader(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(RoutesLoader.class);
    }

    /**
     * Gets the {@link org.apache.camel.AsyncProcessor} await manager.
     */
    public static AsyncProcessorAwaitManager getAsyncProcessorAwaitManager(CamelContext camelContext) {
        return getAsyncProcessorAwaitManager(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the {@link org.apache.camel.AsyncProcessor} await manager.
     */
    public static AsyncProcessorAwaitManager getAsyncProcessorAwaitManager(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(AsyncProcessorAwaitManager.class);
    }

    /**
     * Gets the {@link RuntimeCamelCatalog} if available on the classpath.
     */
    public static RuntimeCamelCatalog getRuntimeCamelCatalog(CamelContext camelContext) {
        return getRuntimeCamelCatalog(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the {@link RuntimeCamelCatalog} if available on the classpath.
     */
    public static RuntimeCamelCatalog getRuntimeCamelCatalog(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(RuntimeCamelCatalog.class);
    }

    /**
     * Gets the {@link RestBindingJaxbDataFormatFactory} to be used.
     */
    public static RestBindingJaxbDataFormatFactory getRestBindingJaxbDataFormatFactory(CamelContext camelContext) {
        return getRestBindingJaxbDataFormatFactory(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the {@link RestBindingJaxbDataFormatFactory} to be used.
     */
    public static RestBindingJaxbDataFormatFactory getRestBindingJaxbDataFormatFactory(
            ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(RestBindingJaxbDataFormatFactory.class);
    }

    /**
     * Gets the {@link BeanProxyFactory} to use.
     */
    public static BeanProxyFactory getBeanProxyFactory(CamelContext camelContext) {
        return getBeanProxyFactory(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the {@link BeanProxyFactory} to use.
     */
    public static BeanProxyFactory getBeanProxyFactory(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(BeanProxyFactory.class);
    }

    /**
     * Gets the {@link UnitOfWorkFactory} to use.
     */
    public static UnitOfWorkFactory getUnitOfWorkFactory(CamelContext camelContext) {
        return getUnitOfWorkFactory(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the {@link UnitOfWorkFactory} to use.
     */
    public static UnitOfWorkFactory getUnitOfWorkFactory(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(UnitOfWorkFactory.class);
    }

    /**
     * Gets the {@link BeanIntrospection}
     */
    public static BeanIntrospection getBeanIntrospection(CamelContext camelContext) {
        return getBeanIntrospection(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the {@link BeanIntrospection}
     */
    public static BeanIntrospection getBeanIntrospection(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(BeanIntrospection.class);
    }

    /**
     * Gets the {@link ResourceLoader} to be used.
     */
    public static ResourceLoader getResourceLoader(CamelContext camelContext) {
        return getResourceLoader(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the {@link ResourceLoader} to be used.
     */
    public static ResourceLoader getResourceLoader(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(ResourceLoader.class);
    }

    /**
     * Gets the {@link BeanProcessorFactory} to use.
     */
    public static BeanProcessorFactory getBeanProcessorFactory(CamelContext camelContext) {
        return getBeanProcessorFactory(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the {@link BeanProcessorFactory} to use.
     */
    public static BeanProcessorFactory getBeanProcessorFactory(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(BeanProcessorFactory.class);
    }

    /**
     * Gets the {@link ModelToXMLDumper} to be used.
     */
    public static ModelToXMLDumper getModelToXMLDumper(CamelContext camelContext) {
        return getModelToXMLDumper(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the {@link ModelToXMLDumper} to be used.
     */
    public static ModelToXMLDumper getModelToXMLDumper(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(ModelToXMLDumper.class);
    }

    /**
     * Gets the {@link ModelToXMLDumper} to be used.
     */
    public static ModelToYAMLDumper getModelToYAMLDumper(CamelContext camelContext) {
        return getModelToYAMLDumper(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the {@link ModelToXMLDumper} to be used.
     */
    public static ModelToYAMLDumper getModelToYAMLDumper(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(ModelToYAMLDumper.class);
    }

    /**
     * Gets the {@link DeferServiceFactory} to use.
     */
    public static DeferServiceFactory getDeferServiceFactory(CamelContext camelContext) {
        return getDeferServiceFactory(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the {@link DeferServiceFactory} to use.
     */
    public static DeferServiceFactory getDeferServiceFactory(ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(DeferServiceFactory.class);
    }

    /**
     * Gets the {@link AnnotationBasedProcessorFactory} to use.
     */
    public static AnnotationBasedProcessorFactory getAnnotationBasedProcessorFactory(CamelContext camelContext) {
        return getAnnotationBasedProcessorFactory(camelContext.getCamelContextExtension());
    }

    /**
     * Gets the {@link AnnotationBasedProcessorFactory} to use.
     */
    public static AnnotationBasedProcessorFactory getAnnotationBasedProcessorFactory(
            ExtendedCamelContext extendedCamelContext) {
        return extendedCamelContext.getContextPlugin(AnnotationBasedProcessorFactory.class);
    }
}
