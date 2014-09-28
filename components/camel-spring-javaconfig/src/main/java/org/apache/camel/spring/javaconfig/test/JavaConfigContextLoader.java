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
package org.apache.camel.spring.javaconfig.test;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.context.ContextLoader;

/**
 * Implementation of the {@link ContextLoader} strategy for creating a
 * {@link org.springframework.context.annotation.AnnotationConfigApplicationContext} for a test's
 * {@link org.springframework.test.context.ContextConfiguration &#064;ContextConfiguration}
 * <p/>
 *
 * Example usage: <p/>
 * <pre class="code">
 * &#064;RunWith(SpringJUnit4ClassRunner.class)
 * &#064;ContextConfiguration(locations = {"com.myco.TestDatabaseConfiguration", "com.myco.config"},
 *                       loader = JavaConfigContextLoader.class)
 * public MyTests { ... }
 * </pre>
 * <p/>
 *
 * Implementation note: At this time, due to restrictions in Java annotations and Spring's
 * TestContext framework, locations of classes / packages must be specified as strings to
 * the ContextConfiguration annotation.  It is understood that this has a detrimental effect
 * on type safety, discoverability and refactoring, and for these reasons may change in
 * future revisions, possibly with a customized version of the ContextConfiguration annotation
 * that accepts an array of class literals to load.
 *
 * @see org.springframework.test.context.ContextConfiguration
 * @deprecated Use org.apache.camel.test.spring.CamelSpringDelegatingTestContextLoader from
 * camel-test-spring jar instead.
 */
@Deprecated
public class JavaConfigContextLoader implements ContextLoader {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Simply returns the supplied <var>locations</var> unchanged.
     * <p/>
     *
     * @param clazz the class with which the locations are associated: used to determine how to
     *            process the supplied locations.
     * @param locations the unmodified locations to use for loading the application context; can be
     *            {@code null} or empty.
     * @return an array of application context resource locations
     * @see org.springframework.test.context.ContextLoader#processLocations(Class, String[])
     */
    public String[] processLocations(Class<?> clazz, String... locations) {
        return locations;
    }

    /**
     * Loads a new {@link ApplicationContext context} based on the supplied {@code locations},
     * configures the context, and finally returns the context in fully <em>refreshed</em> state.
     * <p/>
     *
     * Configuration locations are either fully-qualified class names or base package names. These
     * locations will be given to a {@link AnnotationConfigApplicationContext} for configuration via the
     * {@link AnnotationConfigApplicationContext#register(Class[])} and
     * {@link AnnotationConfigApplicationContext#scan(String...)} methods.
     *
     * @param locations the locations to use to load the application context
     * @return a new application context
     * @throws IllegalArgumentException if any of <var>locations</var> are not valid fully-qualified
     * Class or Package names
     */
    public ApplicationContext loadContext(String... locations) {
        if (logger.isDebugEnabled()) {
            logger.debug("Creating a JavaConfigApplicationContext for {}", Arrays.asList(locations));
        }

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

        List<Class<?>> configClasses = new ArrayList<Class<?>>();
        List<String> basePackages = new ArrayList<String>();
        for (String location : locations) {
            // if the location refers to a class, use it. Otherwise assume it's a base package name
            try {
                final Class<?> aClass = this.getClass().getClassLoader().loadClass(location);
                configClasses.add(aClass);
            } catch (ClassNotFoundException e) {
                if (Package.getPackage(location) == null) {
                    throw new IllegalArgumentException(
                            String.format("A non-existent class or package name was specified: [%s]", location));
                }
                basePackages.add(location);
            }
        }

        logger.debug("Setting config classes to {}", configClasses);
        logger.debug("Setting base packages to {}", basePackages);

        for (Class<?> configClass : configClasses) {
            context.register(configClass);
        }
        
        for (String basePackage : basePackages) {
            context.scan(basePackage);
        }
        
        context.refresh();
        
        return context;
    }

}
