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

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Service;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.support.EndpointHelper;

/**
 * Base test class, mostly made of legacy setup methods. This is intended for internal use.
 */
public abstract class AbstractTestSupport implements CommonTestSupport {
    protected final TestExecutionConfiguration testConfigurationBuilder;
    protected final CamelContextConfiguration camelContextConfiguration;
    protected volatile ModelCamelContext context;
    protected volatile ProducerTemplate template;
    protected volatile FluentProducerTemplate fluentTemplate;
    protected volatile ConsumerTemplate consumer;

    protected AbstractTestSupport() {
        testConfigurationBuilder = new TestExecutionConfiguration();
        camelContextConfiguration = new CamelContextConfiguration();
    }

    protected AbstractTestSupport(TestExecutionConfiguration testConfigurationBuilder,
                                  CamelContextConfiguration camelContextConfiguration) {
        this.testConfigurationBuilder = testConfigurationBuilder;
        this.camelContextConfiguration = camelContextConfiguration;
    }

    /**
     * Strategy to set up resources, before {@link CamelContext} is created. This is meant to be used by resources that
     * must be available before the context is created. Do not use this as a replacement for tasks that can be handled
     * using JUnit's annotations.
     */
    protected void setupResources() throws Exception {
        // noop
    }

    /**
     * Strategy to cleanup resources, after {@link CamelContext} is stopped
     */
    protected void cleanupResources() throws Exception {
        // noop
    }

    /**
     * Use the RouteBuilder or not
     *
     * @deprecated Use the accessors from {@link #testConfiguration()} method
     * @return     <tt>true</tt> then {@link CamelContext} will be auto started, <tt>false</tt> then
     *             {@link CamelContext} will <b>not</b> be auto started (you will have to start it manually)
     */
    @Deprecated(since = "4.7.0")
    public boolean isUseRouteBuilder() {
        return testConfigurationBuilder.useRouteBuilder();
    }

    @Deprecated(since = "4.7.0")
    public void setUseRouteBuilder(boolean useRouteBuilder) {
        testConfigurationBuilder.withUseRouteBuilder(useRouteBuilder);
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
     * @deprecated Use the accessors from {@link #testConfiguration()} method
     * @return     <tt>true</tt> to write route coverage status in an xml file in the
     *             <tt>target/camel-route-coverage</tt> directory after the test has finished.
     */
    @Deprecated(since = "4.7.0")
    public boolean isDumpRouteCoverage() {
        return testConfigurationBuilder.isDumpRouteCoverage();
    }

    /**
     * Whether to dump route as XML or YAML
     * <p/>
     * This allows tooling or manual inspection of the tested routes.
     * <p/>
     * You can also turn on route dump globally via setting JVM system property <tt>CamelTestRouteDump=xml</tt>.
     *
     * @deprecated Use the accessors from {@link #testConfiguration()} method
     * @return     <tt>xml</tt> or <tt>yaml</tt> to write route dump to the log
     */
    @Deprecated(since = "4.10.0")
    public String getDumpRoute() {
        return testConfigurationBuilder.getDumpRoute();
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
     * @deprecated Use the accessors from {@link #testConfiguration()} method
     * @return     <tt>true</tt> if you use advice with in your unit tests.
     */
    @Deprecated(since = "4.7.0")
    public boolean isUseAdviceWith() {
        return testConfigurationBuilder.isUseAdviceWith();
    }

    /**
     * Tells whether {@link CamelContext} should be setup per test or per class. DO NOT USE.
     * <p/>
     * By default it will be setup/teardown per test method. This method returns <code>true</code> when the camel test
     * class is annotated with @TestInstance(TestInstance.Lifecycle.PER_CLASS).
     * <p/>
     * <b>Important:</b> Use this with care as the {@link CamelContext} will carry over state from previous tests, such
     * as endpoints, components etc. So you cannot use this in all your tests.
     * <p/>
     *
     * @deprecated Use the accessors from {@link #testConfiguration()} method
     * @return     <tt>true</tt> per class, <tt>false</tt> per test.
     */
    @Deprecated(since = "4.7.0")
    protected final boolean isCreateCamelContextPerClass() {
        return testConfigurationBuilder.isCreateCamelContextPerClass();
    }

    /**
     * Override to enable auto mocking endpoints based on the pattern.
     * <p/>
     * Return <tt>*</tt> to mock all endpoints.
     *
     * @see        EndpointHelper#matchEndpoint(CamelContext, String, String)
     * @deprecated Use the accessors from {@link #camelContextConfiguration()} method
     */
    @Deprecated(since = "4.7.0")
    public String isMockEndpoints() {
        return camelContextConfiguration().mockEndpoints();
    }

    /**
     * Override to enable auto mocking endpoints based on the pattern, and <b>skip</b> sending to original endpoint.
     * <p/>
     * Return <tt>*</tt> to mock all endpoints.
     *
     * @see        EndpointHelper#matchEndpoint(CamelContext, String, String)
     * @deprecated Use the accessors from {@link #camelContextConfiguration()} method
     */
    @Deprecated(since = "4.7.0")
    public String isMockEndpointsAndSkip() {
        return camelContextConfiguration().mockEndpointsAndSkip();
    }

    /**
     * Override to enable auto stub endpoints based on the pattern.
     * <p/>
     * Return <tt>*</tt> to mock all endpoints.
     *
     * @see        EndpointHelper#matchEndpoint(CamelContext, String, String)
     * @deprecated Use the accessors from {@link #camelContextConfiguration()} method
     */
    @Deprecated(since = "4.11.0")
    public String isStubEndpoints() {
        return camelContextConfiguration().stubEndpoints();
    }

    /**
     * Override to exclusive filtering of routes to not automatically start with Camel starts.
     *
     * The pattern support matching by route id or endpoint urls.
     *
     * Multiple patterns can be specified separated by comma, as example, to exclude all the routes starting from kafka
     * or jms use: kafka,jms.
     *
     * @see        EndpointHelper#matchEndpoint(CamelContext, String, String)
     * @deprecated Use the accessors from {@link #camelContextConfiguration()} method
     */
    @Deprecated(since = "4.11.0")
    public String isAutoStartupExcludePatterns() {
        return camelContextConfiguration().autoStartupExcludePatterns();
    }

    /**
     * To replace from routes
     *
     * @param      routeId
     * @param      fromEndpoint
     * @deprecated              Use the accessors from {@link #camelContextConfiguration()} method
     */
    @Deprecated(since = "4.7.0")
    public void replaceRouteFromWith(String routeId, String fromEndpoint) {
        camelContextConfiguration.replaceRouteFromWith(routeId, fromEndpoint);
    }

    /**
     * Used for filtering routes matching the given pattern, which follows the following rules:
     * <p>
     * - Match by route id - Match by route input endpoint uri
     * <p>
     * The matching is using exact match, by wildcard and regular expression.
     * <p>
     * For example to only include routes which starts with foo in their route id's, use: include=foo&#42; And to
     * exclude routes which starts from JMS endpoints, use: exclude=jms:&#42;
     * <p>
     * Multiple patterns can be separated by comma, for example to exclude both foo and bar routes, use:
     * exclude=foo&#42;,bar&#42;
     * <p>
     * Exclude takes precedence over include.
     */
    @Deprecated(since = "4.7.0")
    public String getRouteFilterIncludePattern() {
        return camelContextConfiguration.routeFilterIncludePattern();
    }

    /**
     * Used for filtering routes matching the given pattern, which follows the following rules:
     * <p>
     * - Match by route id - Match by route input endpoint uri
     * <p>
     * The matching is using exact match, by wildcard and regular expression.
     * <p>
     * For example to only include routes which starts with foo in their route id's, use: include=foo&#42; And to
     * exclude routes which starts from JMS endpoints, use: exclude=jms:&#42;
     * <p>
     * Multiple patterns can be separated by comma, for example to exclude both foo and bar routes, use:
     * exclude=foo&#42;,bar&#42;
     * <p>
     * Exclude takes precedence over include.
     */
    @Deprecated(since = "4.7.0")
    public String getRouteFilterExcludePattern() {
        return camelContextConfiguration.routeFilterExcludePattern();
    }

    /**
     * Override to enable debugger
     * <p/>
     * Is default <tt>false</tt>
     *
     * @deprecated Use the accessors from {@link #testConfiguration()} method
     */
    @Deprecated(since = "4.7.0")
    public boolean isUseDebugger() {
        return camelContextConfiguration.useDebugger();
    }

    @Deprecated(since = "4.7.0")
    public Service getCamelContextService() {
        return camelContextConfiguration.camelContextService();
    }

    @Deprecated(since = "4.7.0")
    public Service camelContextService() {
        return camelContextConfiguration.camelContextService();
    }

    /**
     * Gets a reference to the CamelContext. Must not be used during test setup.
     *
     * @return A reference to the CamelContext
     */
    public CamelContext context() {
        return context;
    }

    /**
     * Sets the CamelContext. Used by the manager to override tests that try to access the context during setup. DO NOT
     * USE.
     *
     * @param context
     */
    @Deprecated(since = "4.7.0")
    public void setContext(ModelCamelContext context) {
        this.context = context;
    }

    public ProducerTemplate template() {
        return template;
    }

    public FluentProducerTemplate fluentTemplate() {
        return fluentTemplate;
    }

    public ConsumerTemplate consumer() {
        return consumer;
    }

    /**
     * Allows a service to be registered a separate lifecycle service to start and stop the context; such as for Spring
     * when the ApplicationContext is started and stopped, rather than directly stopping the CamelContext
     */
    public void setCamelContextService(Service service) {
        camelContextConfiguration.withCamelContextService(service);
    }

    /**
     * Whether JMX should be used during testing.
     *
     * @deprecated Use the methods {@link #testConfiguration()} to enable, disable or check JMX state.
     * @return     <tt>false</tt> by default.
     */
    @Deprecated(since = "4.7.0")
    protected boolean useJmx() {
        return testConfigurationBuilder.isJmxEnabled();
    }

    /**
     * Override this method to include and override properties with the Camel {@link PropertiesComponent}.
     *
     * @deprecated Use the accessors from {@link #camelContextConfiguration()} method
     * @return     additional properties to add/override.
     */
    @Deprecated(since = "4.7.0")
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        return camelContextConfiguration.useOverridePropertiesWithPropertiesComponent();
    }

    /**
     * Whether to ignore missing locations with the {@link PropertiesComponent}. For example when unit testing you may
     * want to ignore locations that are not available in the environment used for testing.
     *
     * @deprecated Use the accessors from {@link #camelContextConfiguration()} method
     * @return     <tt>true</tt> to ignore, <tt>false</tt> to not ignore, and <tt>null</tt> to leave as configured on
     *             the {@link PropertiesComponent}
     */
    @Deprecated(since = "4.7.0")
    protected Boolean ignoreMissingLocationWithPropertiesComponent() {
        return camelContextConfiguration.ignoreMissingLocationWithPropertiesComponent();
    }

    /**
     * Gets the {@link CamelContextConfiguration} for the test
     *
     * @return the camel context configuration
     */
    @Override
    public final CamelContextConfiguration camelContextConfiguration() {
        return camelContextConfiguration;
    }

    /**
     * Gets the {@link TestExecutionConfiguration} test execution configuration instance for the test
     *
     * @return the configuration instance for the test
     */
    @Override
    public final TestExecutionConfiguration testConfiguration() {
        return testConfigurationBuilder;
    }

    /**
     * Disables the JMX agent. Must be called before the setup method.
     *
     * @deprecated Use the methods {@link #testConfiguration()} to enable, disable or check JMX state.
     */
    @Deprecated(since = "4.7.0")
    protected void disableJMX() {
        testConfigurationBuilder.withDisableJMX();
    }

    /**
     * Enables the JMX agent. Must be called before the setup method.
     *
     * @deprecated Use the methods {@link #testConfiguration()} to enable, disable or check JMX state.
     */
    @Deprecated(since = "4.7.0")
    protected void enableJMX() {
        testConfigurationBuilder.withEnableJMX();
    }

    /**
     * Whether route coverage is enabled
     *
     * @deprecated Use the methods {@link #testConfiguration()} to enable or disable the route converage dumper
     * @return     true if enabled or false otherwise
     */
    @Deprecated(since = "4.7.0")
    protected boolean isRouteCoverageEnabled() {
        return testConfigurationBuilder.isRouteCoverageEnabled();
    }
}
