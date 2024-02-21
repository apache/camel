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
package org.apache.camel.test.main.junit5;

import org.apache.camel.CamelContext;
import org.apache.camel.main.MainConfigurationProperties;
import org.apache.camel.main.MainConstants;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The base class of all the test classes that are meant to test a Camel Main application.
 */
public abstract class CamelMainTestSupport extends CamelTestSupport {

    /**
     * The logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger(CamelMainTestSupport.class);

    /**
     * Override this method to be able to configure the Camel for the test like a Camel Main application.
     *
     * @param configuration the Global configuration for Camel Main.
     */
    protected void configure(MainConfigurationProperties configuration) {
        // Nothing to do by default
    }

    /**
     * Override this method to bind custom beans to the Camel {@link Registry} like the method
     * {@link #bindToRegistry(Registry)} but after all possible injections and automatic binding have been done which
     * for example allows to create the custom beans based on injected objects like properties but also allows to
     * replace a bean automatically created and bound by Camel.
     * <p>
     * In the next example, an instance of a custom bean of type {@code CustomGreetings} could be created from the value
     * of property {@code name} and is used to replace the bean of type {@code Greetings} automatically bound by Camel
     * with the name <i>myGreetings</i>.
     *
     * <pre>
     * <code>
     *
     * class ReplaceBeanTest extends CamelMainTestSupport {
     *
     *     &#64;PropertyInject("name")
     *     String name;
     *
     *     &#64;Override
     *     protected void bindToRegistryAfterInjections(Registry registry) throws Exception {
     *         registry.bind("myGreetings", Greetings.class, new CustomGreetings(name));
     *     }
     *
     *     // Rest of the test class
     * }
     * </code>
     * </pre>
     *
     * @param  registry  the registry in which the custom beans are bound.
     * @throws Exception if an error occurs while binding a custom bean.
     */
    protected void bindToRegistryAfterInjections(Registry registry) throws Exception {
        // Nothing to do by default
    }

    /**
     * Gives a comma separated list of the property placeholder locations to use for the test. In case a property is
     * defined at several property placeholder locations, the value of this property in the first property placeholder
     * location according to the order in the list is used. In other words, the value of the properties defined in the
     * first location of the property placeholder in the list takes precedence over the value of the properties of the
     * following location of the property placeholder and so on.
     * <p>
     * By default, it tries to get the inferred locations from the method {@link #getPropertyPlaceholderFileName()} in
     * case it returns a non {@code null} value otherwise it uses the default property placeholder location
     * corresponding to the file {@code application.properties} available from the default package.
     *
     * @return the property placeholder locations to use for the test.
     */
    protected String getPropertyPlaceholderLocations() {
        final String locations = getPropertyPlaceholderLocationsFromFileName();
        if (locations == null) {
            LOG.debug("Use the default property placeholder location");
            return MainConstants.DEFAULT_PROPERTY_PLACEHOLDER_LOCATION;
        }
        LOG.debug("Use the following property placeholder locations: {}", locations);
        return locations;
    }

    /**
     * Gives the file name of the property placeholder to use for the test. This method assumes that the file is located
     * either in the package of the test class or directly in the default package. In other words, if the test class is
     * {@code com.company.SomeTest} and this method has been overridden to return {@code some-app.properties}, then it
     * assumes that the actual possible locations of the property placeholder are
     * {@code classpath:com/company/some-app.properties;optional=true,classpath:some-app.properties;optional=true} which
     * means that for each property to find, it tries to get it first from the properties file of the same package if it
     * exists and if it cannot be found, it tries to get it from the properties file with the same name but in the
     * default package if it exists.
     * <p>
     * <b>Note:</b> Since the properties files are declared as optional, no exception is raised if they are both absent.
     *
     * @return the file name of the property placeholder located in the same package as the test class or directly in
     *         the default package. {@code null} by default.
     */
    protected String getPropertyPlaceholderFileName() {
        return null;
    }

    /**
     * Allows to specify the main class of the application to test if needed in order to simulate the same behavior as
     * with {@link org.apache.camel.main.Main#Main(Class)}.
     *
     * @return the main class of the application to test if any. {@code null} by default indicating that there is no
     *         specific main class.
     */
    protected Class<?> getMainClass() {
        return null;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = super.createCamelContext();
        LOG.debug("Initialize the camel context as a Camel Main application");
        final MainForTest main = new MainForTest();
        final Class<?> mainClass = getMainClass();
        if (mainClass != null) {
            main.configure().withBasePackageScan(mainClass.getPackageName());
        }
        configure(main.configure());
        main.setPropertyPlaceholderLocations(getPropertyPlaceholderLocations());
        main.setOverrideProperties(useOverridePropertiesWithPropertiesComponent());
        main.init(context);
        return context;
    }

    @Override
    protected void applyCamelPostProcessor() throws Exception {
        super.applyCamelPostProcessor();
        bindToRegistryAfterInjections(context.getRegistry());
    }

    /**
     * @return {@code null} if {@link #getPropertyPlaceholderFileName()} returns {@code null}, otherwise it generates a
     *         list of locations assuming that the file is either in the package of the test class or directly in the
     *         default package.
     */
    private String getPropertyPlaceholderLocationsFromFileName() {
        final String location = getPropertyPlaceholderFileName();
        if (location == null) {
            return null;
        }
        return String.format("classpath:%s/%s;optional=true,classpath:%s;optional=true",
                this.getClass().getPackageName().replace('.', '/'), location, location);
    }
}
