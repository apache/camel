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
package org.apache.camel.test.junit5;

import java.lang.reflect.Method;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.Service;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.test.junit5.util.CamelContextTestHelper;
import org.apache.camel.test.junit5.util.ExtensionHelper;
import org.apache.camel.test.junit5.util.RouteCoverageDumperExtension;
import org.apache.camel.test.junit5.util.RouteDumperExtension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit5.TestSupport.isCamelDebugPresent;

public class TransientCamelContextManager implements CamelContextManager {

    private static final Logger LOG = LoggerFactory.getLogger(TransientCamelContextManager.class);

    private final TestExecutionConfiguration testConfigurationBuilder;
    private final CamelContextConfiguration camelContextConfiguration;
    private final Service service;
    private ModelCamelContext context;

    protected ProducerTemplate template;
    protected FluentProducerTemplate fluentTemplate;
    protected ConsumerTemplate consumer;
    private Properties extra;
    private ExtensionContext.Store globalStore;

    public TransientCamelContextManager(TestExecutionConfiguration testConfigurationBuilder,
                                        CamelContextConfiguration camelContextConfiguration) {
        this.testConfigurationBuilder = testConfigurationBuilder;
        this.camelContextConfiguration = camelContextConfiguration;

        service = camelContextConfiguration.camelContextService();
    }

    @Override
    public void createCamelContext(Object test) throws Exception {
        initialize(test);
    }

    @Override
    public void beforeContextStart(Object test) throws Exception {
        applyCamelPostProcessor(test);
        camelContextConfiguration.postProcessor().postSetup();
    }

    private void initialize(Object test) throws Exception {
        LOG.debug("Initializing a new CamelContext");

        // jmx is enabled if we have configured to use it, or if dump route coverage is enabled (it requires JMX) or if
        // the component camel-debug is in the classpath
        if (testConfigurationBuilder.isJmxEnabled() || testConfigurationBuilder.isRouteCoverageEnabled()
                || isCamelDebugPresent()) {
            enableJMX();
        } else {
            disableJMX();
        }

        context = (ModelCamelContext) camelContextConfiguration.camelContextSupplier().createCamelContext();
        assert context != null : "No context found!";

        // TODO: fixme (some tests try to access the context before it's set on the test)
        final Method setContextMethod = test.getClass().getMethod("setContext", ModelCamelContext.class);
        setContextMethod.invoke(test, context);

        // add custom beans
        camelContextConfiguration.registryBinder().bindToRegistry(context.getRegistry());

        // reduce default shutdown timeout to avoid waiting for 300 seconds
        context.getShutdownStrategy().setTimeout(camelContextConfiguration.shutdownTimeout());

        // set debugger if enabled
        if (camelContextConfiguration.useDebugger()) {
            CamelContextTestHelper.setupDebugger(context, camelContextConfiguration.breakpoint());
        }

        setupTemplates();

        // enable auto mocking if enabled
        final String mockPattern = camelContextConfiguration.mockEndpoints();
        final String mockAndSkipPattern = camelContextConfiguration.mockEndpointsAndSkip();
        CamelContextTestHelper.enableAutoMocking(context, mockPattern, mockAndSkipPattern);
        // enable auto stub if enabled
        final String stubPattern = camelContextConfiguration.stubEndpoints();
        CamelContextTestHelper.enableAutoStub(context, stubPattern);
        // auto startup exclude
        final String excludePattern = camelContextConfiguration.autoStartupExcludePatterns();
        if (excludePattern != null) {
            context.setAutoStartupExcludePattern(excludePattern);
        }

        // configure properties component (mandatory for testing)
        configurePropertiesComponent();

        configureIncludeExcludePatterns();

        // prepare for in-between tests
        beforeContextStart(test);

        if (testConfigurationBuilder.useRouteBuilder()) {
            setupRoutes();

            tryStartCamelContext();
        } else {
            CamelContextTestHelper.replaceFromEndpoints(context, camelContextConfiguration.fromEndpoints());
            LOG.debug("Using route builder from the created context: {}", context);
        }
        LOG.debug("Routing Rules are: {}", context.getRoutes());
    }

    private void setupTemplates() {
        template = context.createProducerTemplate();
        template.start();
        fluentTemplate = context.createFluentProducerTemplate();
        fluentTemplate.start();
        consumer = context.createConsumerTemplate();
        consumer.start();
    }

    private void configureIncludeExcludePatterns() {
        final String include = camelContextConfiguration.routeFilterIncludePattern();
        final String exclude = camelContextConfiguration.routeFilterExcludePattern();

        CamelContextTestHelper.configureIncludeExcludePatterns(context, include, exclude);
    }

    private void configurePropertiesComponent() {
        if (extra == null) {
            extra = camelContextConfiguration.useOverridePropertiesWithPropertiesComponent();
        }

        Boolean ignore = camelContextConfiguration.ignoreMissingLocationWithPropertiesComponent();
        CamelContextTestHelper.configurePropertiesComponent(context, extra, new JunitPropertiesSource(globalStore), ignore);
    }

    private void setupRoutes() throws Exception {
        RoutesBuilder[] builders = camelContextConfiguration.routesSupplier().createRouteBuilders();

        CamelContextTestHelper.setupRoutes(context, builders);

        CamelContextTestHelper.replaceFromEndpoints(context, camelContextConfiguration.fromEndpoints());
    }

    private void tryStartCamelContext() throws Exception {
        boolean skip = CamelContextTestHelper.isSkipAutoStartContext(testConfigurationBuilder);
        if (skip) {
            LOG.info(
                    "Skipping starting CamelContext as system property skipStartingCamelContext is set to be true or auto start context is false.");
        } else if (testConfigurationBuilder.isUseAdviceWith()) {
            LOG.info("Skipping starting CamelContext as isUseAdviceWith is set to true.");
        } else {
            CamelContextTestHelper.startCamelContextOrService(context, camelContextConfiguration.camelContextService());
        }
    }

    /**
     * Disables the JMX agent.
     */
    protected void disableJMX() {
        DefaultCamelContext.setDisableJmx(true);
    }

    /**
     * Enables the JMX agent.
     */
    protected void enableJMX() {
        DefaultCamelContext.setDisableJmx(false);
    }

    @Override
    public ModelCamelContext context() {
        return context;
    }

    @Override
    public ProducerTemplate template() {
        return template;
    }

    @Override
    public FluentProducerTemplate fluentTemplate() {
        return fluentTemplate;
    }

    @Override
    public ConsumerTemplate consumer() {
        return consumer;
    }

    @Override
    public Service camelContextService() {
        return service;
    }

    @Override
    public void startCamelContext() throws Exception {
        CamelContextTestHelper.startCamelContextOrService(context, camelContextConfiguration.camelContextService());
    }

    @Override
    public void stopCamelContext() {
        doStopCamelContext(context, camelContextConfiguration.camelContextService());
    }

    @Override
    public void stop() {
        doStopTemplates(consumer, template, fluentTemplate);
        doStopCamelContext(context, service);
    }

    @Override
    public void stopTemplates() {

    }

    @Override
    public void close() {
        // NO-OP
    }

    private static void doStopTemplates(
            ConsumerTemplate consumer, ProducerTemplate template, FluentProducerTemplate fluentTemplate) {
        if (consumer != null) {
            consumer.stop();
        }
        if (template != null) {
            template.stop();
        }
        if (fluentTemplate != null) {
            fluentTemplate.stop();
        }
    }

    protected void doStopCamelContext(CamelContext context, Service camelContextService) {
        if (camelContextService != null) {
            camelContextService.stop();
        } else {
            if (context != null) {
                context.stop();
            }
        }
    }

    protected void applyCamelPostProcessor(Object test) throws Exception {
        // use the bean post processor if the test class is not dependency
        // injected already by Spring Framework
        boolean spring
                = ExtensionHelper.hasClassAnnotation(test.getClass(), "org.springframework.context.annotation.ComponentScan");
        if (!spring) {
            PluginHelper.getBeanPostProcessor(context).postProcessBeforeInitialization(test,
                    test.getClass().getName());
            PluginHelper.getBeanPostProcessor(context).postProcessAfterInitialization(test,
                    test.getClass().getName());
        }
    }

    @Override
    public void setGlobalStore(ExtensionContext.Store globalStore) {
        this.globalStore = globalStore;
    }

    @Override
    public void dumpRouteCoverage(Class<?> clazz, String currentTestName, long time) throws Exception {
        if (testConfigurationBuilder.isRouteCoverageEnabled()) {
            RouteCoverageDumperExtension wrapper = new RouteCoverageDumperExtension(context);
            wrapper.dumpRouteCoverage(clazz, currentTestName, time);
        }
    }

    @Override
    public void dumpRoute(Class<?> clazz, String currentTestName, String format) throws Exception {
        if (format != null && !"false".equals(format)) {
            RouteDumperExtension wrapper = new RouteDumperExtension(context);
            wrapper.dumpRoute(clazz, currentTestName, format);
        }
    }
}
