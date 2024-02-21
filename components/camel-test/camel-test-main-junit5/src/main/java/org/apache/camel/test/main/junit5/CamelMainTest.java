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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.camel.CamelConfiguration;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.support.EndpointHelper;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * {@code @CamelMainTest} is an annotation allowing to mark a test class as a test of a Camel Main application. A
 * specific Camel context is created, initialized and started under the same conditions as a Camel Main application. The
 * annotation can be inherited from a parent class.
 * <p/>
 * In the next example, the annotation {@code CamelMainTest} on the test class {@code SomeTest} indicates that the main
 * class of the Camel Main application to simulate is {@code SomeMainClass} and has one additional configuration class
 * to consider which is {@code SomeConfiguration}.
 *
 * <pre>
 * <code>
 *
 * &#64;CamelMainTest(mainClass = SomeMainClass.class, configurationClasses = SomeConfiguration.class)
 * class SomeTest {
 *     // The rest of the test class
 * }
 * </code>
 * </pre>
 *
 * @see AdviceRouteMapping
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ExtendWith(CamelMainExtension.class)
public @interface CamelMainTest {

    /**
     * Allows to specify the main class of the application to test if needed in order to simulate the same behavior as
     * with {@link org.apache.camel.main.Main#Main(Class)}.
     * <p/>
     * <b>Note:</b> This attribute can be set from a {@code @Nested} test classes. The value of this attribute set on
     * the innermost class is used.
     *
     * @return the main class of the application to test if any. {@code void.class} by default indicating that there is
     *         no specific main class.
     */
    Class<?> mainClass() default void.class;

    /**
     * Gives the list of properties to override with the Camel {@link PropertiesComponent}.
     * <p/>
     * In the next example, the annotation {@code CamelMainTest} on the test class {@code SomeTest} indicates the
     * existing value of the property {@code host} is replaced with {@code localhost} and the existing value of the
     * property {@code port} is replaced with {@code 8080}.
     *
     * <pre>
     * <code>
     *
     * &#64;CamelMainTest(properties = { "host=localhost", "port=8080" })
     * class SomeTest {
     *     // The rest of the test class
     * }
     * </code>
     * </pre>
     * <p/>
     * <b>Note:</b> This attribute can be set from a {@code @Nested} test classes. The values of this attribute are
     * added to the values of the outer classes, knowing that the values are ordered from outermost to innermost.
     *
     * @return an array of {@code String} in the following format
     *         {@code "property-key-1=property-value-1", "property-key-2=property-value-2", ...}
     */
    String[] properties() default {};

    /**
     * Gives the identifier of routes to advice by replacing their from endpoint with the corresponding URI.
     * <p/>
     * In the next example, the annotation {@code CamelMainTest} on the test class {@code SomeTest} indicates the value
     * of the route whose identifier {@code main-route} is advised to replace its current from endpoint with a
     * {@code direct:main} endpoint.
     *
     * <pre>
     * <code>
     *
     * &#64;CamelMainTest(replaceRouteFromWith = { "main-route=direct:main" })
     * class SomeTest {
     *     // The rest of the test class
     * }
     * </code>
     * </pre>
     * <p/>
     * <b>Note:</b> This attribute can be set from a {@code @Nested} test classes. The values of this attribute are
     * added to the values of the outer classes, knowing that the values are ordered from outermost to innermost.
     *
     * @return an array of {@code String} in the following format
     *         {@code "route-id-1=new-uri-1", "route-id-2=new-uri-2", ...}
     */
    String[] replaceRouteFromWith() default {};

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
     * <b>Note:</b> If {@link #propertyPlaceholderLocations()} is set, the value of this attribute is ignored.
     * <p/>
     * <b>Note:</b> This attribute can be set from a {@code @Nested} test classes. The value of this attribute set on
     * the innermost class is used.
     *
     * @return the file name of the property placeholder located in the same package as the test class or directly in
     *         the default package. Not set by default.
     */
    String propertyPlaceholderFileName() default "";

    /**
     * Gives the property placeholder locations to use for the test. In case a property is defined at several property
     * placeholder locations, the value of this property in the first property placeholder location according to the
     * order in the list is used. In other words, the value of the properties defined in the first location of the
     * property placeholder in the list takes precedence over the value of the properties of the following location of
     * the property placeholder and so on.
     * <p>
     * <b>Note:</b> If this attribute is set, the value of {@link #propertyPlaceholderFileName()} is ignored.
     * <p/>
     * <b>Note:</b> This attribute can be set from a {@code @Nested} test classes. The value of this attribute set on
     * the innermost class is used.
     *
     * @return the property placeholder locations to use for the test.
     */
    String[] propertyPlaceholderLocations() default {};

    /**
     * Gives the additional camel configuration classes to add to the global configuration.
     * <p/>
     * <b>Note:</b> This attribute can be set from a {@code @Nested} test classes. The values of this attribute are
     * added to the values of the outer classes, knowing that the values are ordered from outermost to innermost.
     *
     * @return an array of camel configuration classes.
     */
    Class<? extends CamelConfiguration>[] configurationClasses() default {};

    /**
     * Gives the mappings between the routes to advice and the corresponding route builders to call to advice the
     * routes.
     * <p/>
     * <b>Note:</b> This attribute can be set from a {@code @Nested} test classes. The values of this attribute are
     * added to the values of the outer classes, knowing that the values are ordered from outermost to innermost.
     *
     * @return an array of mapping between route and route builder
     */
    AdviceRouteMapping[] advices() default {};

    /**
     * Enable auto mocking endpoints based on the pattern.
     * <p/>
     * Return <tt>*</tt> to mock all endpoints.
     * <p/>
     * <b>Note:</b> This attribute can be set from a {@code @Nested} test classes. The value of this attribute set on
     * the innermost class is used.
     *
     * @see EndpointHelper#matchEndpoint(CamelContext, String, String)
     */
    String mockEndpoints() default "";

    /**
     * Enable auto mocking endpoints based on the pattern, and <b>skip</b> sending to original endpoint.
     * <p/>
     * Return <tt>*</tt> to mock all endpoints.
     * <p/>
     * <b>Note:</b> This attribute can be set from a {@code @Nested} test classes. The value of this attribute set on
     * the innermost class is used.
     *
     * @see EndpointHelper#matchEndpoint(CamelContext, String, String)
     */
    String mockEndpointsAndSkip() default "";

    /**
     * Whether to dump route coverage stats at the end of the test.
     * <p/>
     * This allows tooling or manual inspection of the stats, so you can generate a route trace diagram of which EIPs
     * have been in use and which have not. Similar concepts as a code coverage report.
     * <p/>
     * You can also turn on route coverage globally via setting JVM system property
     * <tt>CamelTestRouteCoverage=true</tt>.
     * <p/>
     * <b>Note:</b> This attribute can only be set on the outer class, values set on a {@code @Nested} test classes are
     * ignored.
     *
     * @return <tt>true</tt> to write route coverage status in a xml file in the <tt>target/camel-route-coverage</tt>
     *         directory after the test has finished.
     */
    boolean dumpRouteCoverage() default false;

    /**
     * Whether JMX should be used during testing.
     *
     * @return <tt>false</tt> by default.
     */
    boolean useJmx() default false;

    /**
     * Returns the timeout to use when shutting down (unit in seconds).
     * <p/>
     * Will default use 10 seconds.
     * <p/>
     * <b>Note:</b> This attribute can only be set on the outer class, values set on a {@code @Nested} test classes are
     * ignored.
     *
     * @return the timeout to use
     */
    int shutdownTimeout() default 10;
}
