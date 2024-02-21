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
package org.apache.camel.test.spring.junit5;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.test.ExcludingPackageScanClassResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.support.AbstractContextLoader;
import org.springframework.test.context.support.AbstractGenericContextLoader;
import org.springframework.test.context.support.GenericXmlContextLoader;
import org.springframework.util.StringUtils;

/**
 * Replacement for the default {@link GenericXmlContextLoader} that provides hooks for processing some class level Camel
 * related test annotations.
 */
public class CamelSpringTestContextLoader extends AbstractContextLoader {

    private static final Logger LOG = LoggerFactory.getLogger(CamelSpringTestContextLoader.class);

    /**
     * Modeled after the Spring implementation in {@link AbstractGenericContextLoader}, this method creates and
     * refreshes the application context while providing for processing of additional Camel specific post-refresh
     * actions. We do not provide the pre-post hooks for customization seen in {@link AbstractGenericContextLoader}
     * because they probably are unnecessary for 90+% of users.
     * <p/>
     * For some functionality, we cannot use {@link org.springframework.test.context.TestExecutionListener} because we
     * need to both produce the desired outcome during application context loading, and also cleanup after ourselves
     * even if the test class never executes. Thus the listeners, which only run if the application context is
     * successfully initialized are insufficient to provide the behavior described above.
     */
    @Override
    public ApplicationContext loadContext(MergedContextConfiguration mergedConfig) throws Exception {
        Class<?> testClass = getTestClass();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Loading ApplicationContext for merged context configuration [{}].", mergedConfig);
        }

        try {
            GenericApplicationContext context = createContext(testClass, mergedConfig);
            prepareContext(context, mergedConfig);
            loadBeanDefinitions(context, mergedConfig);
            AnnotationConfigUtils.registerAnnotationConfigProcessors(context);
            customizeContext(context, mergedConfig);

            return loadContext(context, testClass);
        } finally {
            CamelAnnotationsHandler.cleanup();
        }
    }

    /**
     * Modeled after the Spring implementation in {@link AbstractGenericContextLoader}, this method creates and
     * refreshes the application context while providing for processing of additional Camel specific post-refresh
     * actions. We do not provide the pre-post hooks for customization seen in {@link AbstractGenericContextLoader}
     * because they probably are unnecessary for 90+% of users.
     * <p/>
     * For some functionality, we cannot use {@link org.springframework.test.context.TestExecutionListener} because we
     * need to both produce the desired outcome during application context loading, and also cleanup after ourselves
     * even if the test class never executes. Thus the listeners, which only run if the application context is
     * successfully initialized are insufficient to provide the behavior described above.
     */
    @Override
    public ApplicationContext loadContext(String... locations) throws Exception {
        Class<?> testClass = getTestClass();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Loading ApplicationContext for locations [{}].", StringUtils.arrayToCommaDelimitedString(locations));
        }

        try {
            GenericApplicationContext context = createContext(testClass, null);
            loadBeanDefinitions(context, testClass, locations);
            return loadContext(context, testClass);
        } finally {
            CamelAnnotationsHandler.cleanup();
        }
    }

    /**
     * Returns &quot;<code>-context.xml</code>&quot;.
     */
    @Override
    public String getResourceSuffix() {
        return "-context.xml";
    }

    /**
     * Performs the bulk of the Spring application context loading/customization.
     *
     * @param  context   the partially configured context. The context should have the bean definitions loaded, but
     *                   nothing else.
     * @param  testClass the test class being executed
     * @return           the initialized (refreshed) Spring application context
     *
     * @throws Exception if there is an error during initialization/customization
     */
    protected ApplicationContext loadContext(GenericApplicationContext context, Class<?> testClass) throws Exception {

        AnnotationConfigUtils.registerAnnotationConfigProcessors(context);

        // Pre CamelContext(s) instantiation setup
        CamelAnnotationsHandler.handleDisableJmx(testClass);
        CamelAnnotationsHandler.handleExcludeRoutes(testClass);
        CamelAnnotationsHandler.handleUseOverridePropertiesWithPropertiesComponent(context, testClass);

        // Temporarily disable CamelContext start while the contexts are instantiated.
        SpringCamelContext.setNoStart(true);
        try {
            context.refresh();
            context.registerShutdownHook();
        } finally {
            // Turn CamelContext startup back on since the context's have now been instantiated.
            SpringCamelContext.setNoStart(false);
        }

        // Post CamelContext(s) instantiation but pre CamelContext(s) start setup
        CamelAnnotationsHandler.handleRouteCoverage(context, testClass, s -> getTestMethod().getName());
        CamelAnnotationsHandler.handleProvidesBreakpoint(context, testClass);
        CamelAnnotationsHandler.handleShutdownTimeout(context, testClass);
        CamelAnnotationsHandler.handleMockEndpoints(context, testClass);
        CamelAnnotationsHandler.handleMockEndpointsAndSkip(context, testClass);

        // CamelContext(s) startup
        CamelAnnotationsHandler.handleCamelContextStartup(context, testClass);

        return context;
    }

    protected void loadBeanDefinitions(GenericApplicationContext context, MergedContextConfiguration mergedConfig) {
        loadBeanDefinitions(context, mergedConfig.getTestClass(), mergedConfig.getLocations());
        new AnnotatedBeanDefinitionReader(context).register(mergedConfig.getClasses());
    }

    protected void loadBeanDefinitions(GenericApplicationContext context, Class<?> clazz, String... locations) {
        Map<String, String> props = CamelSpringTestSupport.getTranslationProperties(clazz);
        XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(context);
        for (String location : locations) {
            Resource r = reader.getResourceLoader().getResource(location);
            Resource t = new CamelSpringTestSupport.TranslatedResource(r, props);
            reader.loadBeanDefinitions(t);
        }
    }

    /**
     * Creates and starts the Spring context while optionally starting any loaded Camel contexts.
     *
     * @param  testClass the test class that is being executed
     * @return           the loaded Spring context
     */
    protected GenericApplicationContext createContext(Class<?> testClass, MergedContextConfiguration mergedConfig) {
        ApplicationContext parentContext = null;
        GenericApplicationContext routeExcludingContext = null;

        if (mergedConfig != null) {
            parentContext = mergedConfig.getParentApplicationContext();
        }

        if (testClass.isAnnotationPresent(ExcludeRoutes.class)) {
            Class<?>[] excludedClasses = testClass.getAnnotation(ExcludeRoutes.class).value();

            if (excludedClasses.length > 0) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(
                            "Setting up package scanning excluded classes as ExcludeRoutes annotation was found. Excluding [{}]",
                            StringUtils.arrayToCommaDelimitedString(excludedClasses));
                }

                if (parentContext == null) {
                    routeExcludingContext = new GenericApplicationContext();
                } else {
                    routeExcludingContext = new GenericApplicationContext(parentContext);
                }
                routeExcludingContext.registerBeanDefinition("excludingResolver",
                        new RootBeanDefinition(ExcludingPackageScanClassResolver.class));
                routeExcludingContext.refresh();

                ExcludingPackageScanClassResolver excludingResolver
                        = routeExcludingContext.getBean("excludingResolver", ExcludingPackageScanClassResolver.class);
                List<Class<?>> excluded = Arrays.asList(excludedClasses);
                excludingResolver.setExcludedClasses(new HashSet<>(excluded));
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Not enabling package scanning excluded classes as ExcludeRoutes "
                              + "annotation was found but no classes were excluded.");
                }
            }
        }

        GenericApplicationContext context;

        if (routeExcludingContext != null) {
            context = new GenericApplicationContext(routeExcludingContext);
        } else {
            if (parentContext != null) {
                context = new GenericApplicationContext(parentContext);
            } else {
                context = new GenericApplicationContext();
            }
        }

        return context;
    }

    /**
     * Returns the class under test in order to enable inspection of annotations while the Spring context is being
     * created.
     *
     * @return the test class that is being executed
     * @see    CamelSpringTestHelper
     */
    protected Class<?> getTestClass() {
        return CamelSpringTestHelper.getTestClass();
    }

    /**
     * Returns the test method under test.
     *
     * @return the method that is being executed
     * @see    CamelSpringTestHelper
     */
    protected Method getTestMethod() {
        return CamelSpringTestHelper.getTestMethod();
    }
}
