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
package org.apache.camel.test.spring;

import java.lang.reflect.Method;

import org.apache.camel.management.JmxSystemPropertyKeys;
import org.apache.camel.spring.SpringCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.support.DelegatingSmartContextLoader;

/**
 * CamelSpringDelegatingTestContextLoader which fixes issues in Camel's JavaConfigContextLoader. (adds support for Camel's test annotations)
 * <br>
 * <em>This loader can handle either classes or locations for configuring the context.</em>
 * <br>
 * NOTE: This TestContextLoader doesn't support the annotation of ExcludeRoutes now.
 */
public class CamelSpringDelegatingTestContextLoader extends DelegatingSmartContextLoader {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public ApplicationContext loadContext(MergedContextConfiguration mergedConfig) throws Exception {
        
        Class<?> testClass = getTestClass();
        
        if (logger.isDebugEnabled()) {
            logger.debug("Loading ApplicationContext for merged context configuration [{}].", mergedConfig);
        }
        
        // Pre CamelContext(s) instantiation setup
        CamelAnnotationsHandler.handleDisableJmx(null, testClass);

        try {
            SpringCamelContext.setNoStart(true);
            System.setProperty("skipStartingCamelContext", "true");
            ConfigurableApplicationContext context = (ConfigurableApplicationContext) super.loadContext(mergedConfig);
            SpringCamelContext.setNoStart(false);
            System.clearProperty("skipStartingCamelContext");
            return loadContext(context, testClass);
        } finally {
            cleanup(testClass);
        }
    }

    /**
     * Performs the bulk of the Spring application context loading/customization.
     *
     * @param context the partially configured context.  The context should have the bean definitions loaded, but nothing else.
     * @param testClass the test class being executed
     * @return the initialized (refreshed) Spring application context
     *
     * @throws Exception if there is an error during initialization/customization
     */
    public ApplicationContext loadContext(ConfigurableApplicationContext context, Class<?> testClass)
        throws Exception {
            
        AnnotationConfigUtils.registerAnnotationConfigProcessors((BeanDefinitionRegistry) context);

        // Post CamelContext(s) instantiation but pre CamelContext(s) start setup
        CamelAnnotationsHandler.handleRouteCoverage(context, testClass, s -> getTestMethod().getName());
        CamelAnnotationsHandler.handleProvidesBreakpoint(context, testClass);
        CamelAnnotationsHandler.handleShutdownTimeout(context, testClass);
        CamelAnnotationsHandler.handleMockEndpoints(context, testClass);
        CamelAnnotationsHandler.handleMockEndpointsAndSkip(context, testClass);
        CamelAnnotationsHandler.handleUseOverridePropertiesWithPropertiesComponent(context, testClass);
        
        // CamelContext(s) startup
        CamelAnnotationsHandler.handleCamelContextStartup(context, testClass);
        
        return context;
    }
    
    /**
     * Cleanup/restore global state to defaults / pre-test values after the test setup
     * is complete. 
     * 
     * @param testClass the test class being executed
     */
    protected void cleanup(Class<?> testClass) {
        SpringCamelContext.setNoStart(false);
        
        if (testClass.isAnnotationPresent(DisableJmx.class)) {
            if (CamelSpringTestHelper.getOriginalJmxDisabled() == null) {
                System.clearProperty(JmxSystemPropertyKeys.DISABLED);
            } else {
                System.setProperty(JmxSystemPropertyKeys.DISABLED,
                    CamelSpringTestHelper.getOriginalJmxDisabled());
            }
        }
    }

    /**
     * Returns the class under test in order to enable inspection of annotations while the
     * Spring context is being created.
     * 
     * @return the test class that is being executed
     * @see CamelSpringTestHelper
     */
    protected Class<?> getTestClass() {
        return CamelSpringTestHelper.getTestClass();
    }

    /**
     * Returns the test method under test.
     *
     * @return the method that is being executed
     * @see CamelSpringTestHelper
     */
    protected Method getTestMethod() {
        return CamelSpringTestHelper.getTestMethod();
    }

}
