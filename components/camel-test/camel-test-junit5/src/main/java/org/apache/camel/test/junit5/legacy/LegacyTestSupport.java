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

package org.apache.camel.test.junit5.legacy;

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.test.junit5.CamelContextConfiguration;
import org.apache.camel.test.junit5.ConfigurableContext;
import org.apache.camel.test.junit5.ConfigurableTest;
import org.apache.camel.test.junit5.TestExecutionConfiguration;
import org.apache.camel.test.junit5.util.CamelContextTestHelper;

public interface LegacyTestSupport extends ConfigurableTest, ConfigurableContext {

    /**
     * Use the RouteBuilder or not
     *
     * @return <tt>true</tt> then {@link CamelContext} will be auto started, <tt>false</tt> then {@link CamelContext}
     *         will <b>not</b> be auto started (you will have to start it manually)
     */
    @Deprecated(since = "4.7.0")
    default boolean isUseRouteBuilder() {
        return TestExecutionConfiguration.DEFAULT_USE_ROUTE_BUILDER;
    }

    @Deprecated(since = "4.7.0")
    default void setUseRouteBuilder(boolean useRouteBuilder) {
        testConfiguration().withUseRouteBuilder(useRouteBuilder);
    }

    /**
     * Whether to dump route coverage stats at the end of the test.
     * <p/>
     * This allows tooling or manual inspection of the stats, so you can generate a route trace diagram of which EIPs
     * have been in use and which have not. Similar concepts as a code coverage report.
     * <p/>
     * You can also turn on route coverage globally via setting JVM system property
     * <tt>CamelTestRouteCoverage=true</tt>.
     *
     * @return <tt>true</tt> to write route coverage status in an xml file in the <tt>target/camel-route-coverage</tt>
     *         directory after the test has finished.
     */
    @Deprecated(since = "4.7.0")
    default boolean isDumpRouteCoverage() {
        return TestExecutionConfiguration.DEFAULT_DUMP_ROUTE_COVERAGE;
    }

    /**
     * Override when using <a href="http://camel.apache.org/advicewith.html">advice with</a> and return <tt>true</tt>.
     * This helps to know advice with is to be used, and {@link CamelContext} will not be started before the advice with
     * takes place. This helps by ensuring the advice with has been property setup before the {@link CamelContext} is
     * started
     * <p/>
     * <b>Important:</b> It's important to start {@link CamelContext} manually from the unit test after you are done
     * doing all the advice with.
     *
     * @return <tt>true</tt> if you use advice with in your unit tests.
     */
    @Deprecated(since = "4.7.0")
    default boolean isUseAdviceWith() {
        return TestExecutionConfiguration.DEFAULT_USE_ADVICE_WITH;
    }

    /**
     * Tells whether {@link CamelContext} should be setup per test or per class. DO NOT USE.
     * <p/>
     * By default, it will be setup/teardown per test method. This method returns <code>true</code> when the camel test
     * class is annotated with @TestInstance(TestInstance.Lifecycle.PER_CLASS).
     * <p/>
     * <b>Important:</b> Use this with care as the {@link CamelContext} will carry over state from previous tests, such
     * as endpoints, components, etc. So you cannot use this in all your tests.
     * <p/>
     *
     * @return <tt>true</tt> per class, <tt>false</tt> per test.
     */
    @Deprecated(since = "4.7.0")
    default boolean isCreateCamelContextPerClass() {
        return TestExecutionConfiguration.DEFAULT_CREATE_CONTEXT_PER_CLASS;
    }

    /**
     * Override to enable auto mocking endpoints based on the pattern.
     * <p/>
     * Return <tt>*</tt> to mock all endpoints.
     *
     * @see EndpointHelper#matchEndpoint(CamelContext, String, String)
     */
    @Deprecated(since = "4.7.0")
    default String isMockEndpoints() {
        return CamelContextConfiguration.DEFAULT_MOCK_ENDPOINTS;
    }

    /**
     * Override to enable auto mocking endpoints based on the pattern, and <b>skip</b> sending to original endpoint.
     * <p/>
     * Return <tt>*</tt> to mock all endpoints.
     *
     * @see EndpointHelper#matchEndpoint(CamelContext, String, String)
     */
    @Deprecated(since = "4.7.0")
    default String isMockEndpointsAndSkip() {
        return CamelContextConfiguration.DEFAULT_MOCK_ENDPOINTS_AND_SKIP;
    }

    /**
     * To replace the 'from' routes
     *
     * @param routeId
     * @param fromEndpoint
     */
    @Deprecated(since = "4.7.0")
    default void replaceRouteFromWith(String routeId, String fromEndpoint) {
        camelContextConfiguration().replaceRouteFromWith(routeId, fromEndpoint);
    }

    /**
     * Used for filtering routes matching the given pattern, which follows the following rules:
     * <p>
     * - Match by route id - Match by route input endpoint uri
     * <p>
     * The matching is using exact match, by wildcard and regular expression.
     * <p>
     * For example, to only include routes which start with 'foo' in their route id's, use: include=foo&#42; And to
     * exclude routes which start from JMS endpoints, use: exclude=jms:&#42;
     * <p>
     * Multiple patterns can be separated by comma, for example, to exclude both foo and bar routes, use:
     * exclude=foo&#42;,bar&#42;
     * <p>
     * Exclude takes precedence over include.
     */
    @Deprecated(since = "4.7.0")
    default String getRouteFilterIncludePattern() {
        return CamelContextConfiguration.DEFAULT_ROUTE_FILTER_INCLUDE_PATTERN;
    }

    /**
     * Used for filtering routes matching the given pattern, which follows the following rules:
     * <p>
     * - Match by route id - Match by route input endpoint uri
     * <p>
     * The matching is using exact match, by wildcard and regular expression.
     * <p>
     * For example, to only include routes which start with foo in their route id's, use: include=foo&#42; And to
     * exclude routes which start from JMS endpoints, use: exclude=jms:&#42;
     * <p>
     * Multiple patterns can be separated by comma, for example, to exclude both foo and bar routes, use:
     * exclude=foo&#42;,bar&#42;
     * <p>
     * Exclude takes precedence over include.
     */
    @Deprecated(since = "4.7.0")
    default String getRouteFilterExcludePattern() {
        return CamelContextConfiguration.DEFAULT_ROUTE_FILTER_EXCLUDE_PATTERN;
    }

    /**
     * Whether the debugger is enabled.
     *
     * @return true if the debugger is enabled or false otherwise
     */
    @Deprecated(since = "4.7.0")
    default boolean isUseDebugger() {
        return CamelContextConfiguration.DEFAULT_USE_DEBUGGER;
    }

    /**
     * Whether JMX should be used during testing.
     *
     * @return <tt>false</tt> by default.
     */
    @Deprecated(since = "4.7.0")
    default boolean useJmx() {
        return TestExecutionConfiguration.DEFAULT_USE_JMX;
    }

    /**
     * Disables the JMX agent. Must be called before the setup method.
     */
    @Deprecated(since = "4.7.0")
    default void disableJMX() {
        testConfiguration().withDisableJMX();
    }

    /**
     * Enables the JMX agent. Must be called before the setup method.
     */
    @Deprecated(since = "4.7.0")
    default void enableJMX() {
        testConfiguration().withEnableJMX();
    }

    /**
     * Whether route coverage is enabled
     *
     * @return true if enabled or false otherwise
     */
    @Deprecated(since = "4.7.0")
    default boolean isRouteCoverageEnabled() {
        return CamelContextTestHelper.isRouteCoverageEnabled(isDumpRouteCoverage());
    }

    /**
     * Override this method to include and override properties with the Camel {@link PropertiesComponent}.
     *
     * @return additional properties to add/override.
     */
    @Deprecated(since = "4.7.0")
    default Properties useOverridePropertiesWithPropertiesComponent() {
        return CamelContextConfiguration.DEFAULT_USE_OVERRIDE_PROPERTIES_WITH_PROPERTIES_COMPONENT;
    }

    /**
     * Whether to ignore missing locations with the {@link PropertiesComponent}. For example when unit testing you may
     * want to ignore locations that are not available in the environment used for testing.
     *
     * @return <tt>true</tt> to ignore, <tt>false</tt> to not ignore, and <tt>null</tt> to leave as configured on the
     *         {@link PropertiesComponent}
     */
    @Deprecated(since = "4.7.0")
    default Boolean ignoreMissingLocationWithPropertiesComponent() {
        return CamelContextConfiguration.DEFAULT_IGNORE_MISSING_LOCATION_WITH_PROPERTIES_COMPONENT;
    }

    @Override
    default void configureTest(TestExecutionConfiguration testExecutionConfiguration) {
        testExecutionConfiguration.withJMX(useJmx())
                .withUseRouteBuilder(isUseRouteBuilder())
                .withUseAdviceWith(isUseAdviceWith())
                .withDumpRouteCoverage(isDumpRouteCoverage());
    }

    @Override
    default void configureContext(CamelContextConfiguration camelContextConfiguration) {
        camelContextConfiguration
                .withUseOverridePropertiesWithPropertiesComponent(useOverridePropertiesWithPropertiesComponent())
                .withRouteFilterExcludePattern(getRouteFilterExcludePattern())
                .withRouteFilterIncludePattern(getRouteFilterIncludePattern())
                .withMockEndpoints(isMockEndpoints())
                .withMockEndpointsAndSkip(isMockEndpointsAndSkip());
    }
}
