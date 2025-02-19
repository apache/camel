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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.Service;
import org.apache.camel.component.mock.MockEndpoint;
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

/**
 * A {@link CamelContext} test lifecycle manager based on the behavior that was built in {@link CamelTestSupport} up to
 * Camel 4.7.0
 */
public class LegacyCamelContextManager implements CamelContextManager {

    private static final Logger LOG = LoggerFactory.getLogger(LegacyCamelContextManager.class);

    private static final ThreadLocal<LegacyCamelContextManager> INSTANCE = new ThreadLocal<>();
    private static final ThreadLocal<AtomicInteger> TESTS = new ThreadLocal<>();
    private static final ThreadLocal<ModelCamelContext> THREAD_CAMEL_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<ProducerTemplate> THREAD_TEMPLATE = new ThreadLocal<>();
    private static final ThreadLocal<FluentProducerTemplate> THREAD_FLUENT_TEMPLATE = new ThreadLocal<>();
    private static final ThreadLocal<ConsumerTemplate> THREAD_CONSUMER = new ThreadLocal<>();
    private static final ThreadLocal<Service> THREAD_SERVICE = new ThreadLocal<>();

    private final TestExecutionConfiguration testConfigurationBuilder;
    private final CamelContextConfiguration camelContextConfiguration;
    private ModelCamelContext context;

    protected volatile ProducerTemplate template;
    protected volatile FluentProducerTemplate fluentTemplate;
    protected volatile ConsumerTemplate consumer;
    private Properties extra;
    private ExtensionContext.Store globalStore;

    public LegacyCamelContextManager(TestExecutionConfiguration testConfigurationBuilder,
                                     CamelContextConfiguration camelContextConfiguration) {
        this.testConfigurationBuilder = testConfigurationBuilder;
        this.camelContextConfiguration = camelContextConfiguration;

        final Service service = camelContextConfiguration.camelContextService();
        if (service != null) {
            THREAD_SERVICE.set(service);
        }
    }

    @Override
    public void createCamelContext(Object test) throws Exception {
        if (testConfigurationBuilder.isCreateCamelContextPerClass()) {
            createCamelContextPerClass(test);
        } else {
            initialize(test);
        }
    }

    @Override
    public void beforeContextStart(Object test) throws Exception {
        context = THREAD_CAMEL_CONTEXT.get();
        template = THREAD_TEMPLATE.get();
        fluentTemplate = THREAD_FLUENT_TEMPLATE.get();
        consumer = THREAD_CONSUMER.get();

        applyCamelPostProcessor(test);
        camelContextConfiguration.postProcessor().postSetup();
    }

    private void createCamelContextPerClass(Object test) throws Exception {
        INSTANCE.set(this);
        AtomicInteger v = TESTS.get();
        if (v == null) {
            v = new AtomicInteger();
            TESTS.set(v);
        }
        if (v.getAndIncrement() == 0) {
            LOG.debug("Setup CamelContext before running first test");
            // test is per class, so only setup once (the first time)
            initialize(test);
        } else {
            LOG.debug("Reset between test methods");
            // and in between tests we must do IoC and reset mocks
            beforeContextStart(test);
            MockEndpoint.resetMocks(context);
        }
    }

    private void initialize(Object test) throws Exception {
        if (context != null) {
            return;
        }

        doCreateContext(test);

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

    private void doCreateContext(Object test) throws Exception {
        LOG.debug("Initializing a new CamelContext");

        // jmx is enabled if we have configured to use it, if dump route coverage is enabled (it requires JMX) or if
        // the component camel-debug is in the classpath
        if (testConfigurationBuilder.isJmxEnabled() || testConfigurationBuilder.isRouteCoverageEnabled()
                || isCamelDebugPresent()) {
            enableJMX();
        } else {
            disableJMX();
        }

        context = (ModelCamelContext) camelContextConfiguration.camelContextSupplier().createCamelContext();
        assert context != null : "No context found!";

        THREAD_CAMEL_CONTEXT.set(context);

        // TODO: fixme (some tests try to access the context before it's set on the test)
        final Method setContextMethod = test.getClass().getMethod("setContext", ModelCamelContext.class);
        setContextMethod.invoke(test, context);
    }

    private void setupTemplates() {
        template = context.createProducerTemplate();
        template.start();
        fluentTemplate = context.createFluentProducerTemplate();
        fluentTemplate.start();
        consumer = context.createConsumerTemplate();
        consumer.start();

        THREAD_TEMPLATE.set(template);
        THREAD_FLUENT_TEMPLATE.set(fluentTemplate);
        THREAD_CONSUMER.set(consumer);
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
        return THREAD_SERVICE.get();
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
        // NO-OP
    }

    @Override
    public void close() {
        LegacyCamelContextManager support = INSTANCE.get();
        if (support != null && testConfigurationBuilder.isCreateCamelContextPerClass()) {
            try {
                support.tearDownCreateCamelContextPerClass();
            } catch (Exception e) {
                // ignore
            }
        }

        doStopCamelContext(THREAD_CAMEL_CONTEXT.get(), THREAD_SERVICE.get());
    }

    @Override
    public void stopTemplates() {
        doStopTemplates(THREAD_CONSUMER.get(), THREAD_TEMPLATE.get(), THREAD_FLUENT_TEMPLATE.get());
    }

    void tearDownCreateCamelContextPerClass() {
        LOG.debug("tearDownCreateCamelContextPerClass()");
        TESTS.remove();
        stopTemplates();
        doStopCamelContext(THREAD_CAMEL_CONTEXT.get(), THREAD_SERVICE.get());
    }

    private static void doStopTemplates(
            ConsumerTemplate consumer, ProducerTemplate template, FluentProducerTemplate fluentTemplate) {
        if (consumer != null) {
            if (consumer == THREAD_CONSUMER.get()) {
                THREAD_CONSUMER.remove();
            }
            consumer.stop();
        }
        if (template != null) {
            if (template == THREAD_TEMPLATE.get()) {
                THREAD_TEMPLATE.remove();
            }
            template.stop();
        }
        if (fluentTemplate != null) {
            if (fluentTemplate == THREAD_FLUENT_TEMPLATE.get()) {
                THREAD_FLUENT_TEMPLATE.remove();
            }
            fluentTemplate.stop();
        }
    }

    protected void doStopCamelContext(CamelContext context, Service camelContextService) {
        if (camelContextService != null) {
            if (camelContextService == THREAD_SERVICE.get()) {
                THREAD_SERVICE.remove();
            }
            camelContextService.stop();
        } else {
            if (context != null) {
                if (context == THREAD_CAMEL_CONTEXT.get()) {
                    THREAD_CAMEL_CONTEXT.remove();
                }
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
