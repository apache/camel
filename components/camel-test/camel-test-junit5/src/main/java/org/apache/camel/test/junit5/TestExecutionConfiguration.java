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

import org.apache.camel.CamelContext;
import org.apache.camel.test.junit5.util.CamelContextTestHelper;

/**
 * This configuration class allows tweaking how the test itself configured and enable/disable features that affect its
 * execution environment.
 */
public class TestExecutionConfiguration {
    private boolean jmx;
    private boolean dumpRouteCoverage = false;
    private String dumpRoute;
    private boolean useAdviceWith = false;
    private boolean createCamelContextPerClass = false;
    private boolean useRouteBuilder = true;
    private boolean autoStartContext = true;

    public boolean isJmxEnabled() {
        return jmx;
    }

    /**
     * Enables the JMX agent. Must be called before the setUp method.
     */
    public TestExecutionConfiguration withEnableJMX() {
        return withJMX(true);
    }

    /**
     * Disables the JMX agent. Must be called before the setUp method.
     */
    public TestExecutionConfiguration withDisableJMX() {
        return withJMX(false);
    }

    public TestExecutionConfiguration withJMX(boolean enableJMX) {
        this.jmx = enableJMX;
        return this;
    }

    public String getDumpRoute() {
        return dumpRoute;
    }

    /**
     * Whether to dump the routes loaded into Camel for each test (dumped into files in target/camel-route-dump).
     * <p/>
     * The routes can either be dumped into XML or YAML format.
     * <p/>
     * This allows tooling or manual inspection of the routes.
     * <p/>
     * You can also turn on route dump globally via setting JVM system property <tt>CamelTestRouteDump=xml</tt>.
     *
     * @param dumpRoute <tt>xml</tt> or <tt>yaml</tt> format. The dumped routes are stored in
     *                  <tt>target/camel-route-dump</tt> directory after the test has finished.
     */
    public TestExecutionConfiguration withDumpRoute(String dumpRoute) {
        this.dumpRoute = dumpRoute;
        return this;
    }

    /**
     * Whether route dump is enabled
     *
     * @return true if enabled or false otherwise
     */
    public boolean isRouteDumpEnabled() {
        String dump = CamelContextTestHelper.getRouteDump(getDumpRoute());
        return dump == null || dump.isBlank();
    }

    /**
     * Whether route coverage is enabled
     *
     * @return true if enabled or false otherwise
     */
    public boolean isDumpRouteCoverage() {
        return dumpRouteCoverage;
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
     * @param dumpRouteCoverage <tt>true</tt> to write route coverage status in an xml file in the
     *                          <tt>target/camel-route-coverage</tt> directory after the test has finished.
     */
    public TestExecutionConfiguration withDumpRouteCoverage(boolean dumpRouteCoverage) {
        this.dumpRouteCoverage = dumpRouteCoverage;
        return this;
    }

    /**
     * Whether route coverage is enabled
     *
     * @return true if enabled or false otherwise
     */
    public boolean isRouteCoverageEnabled() {
        return CamelContextTestHelper.isRouteCoverageEnabled(isDumpRouteCoverage());
    }

    /**
     * Whether to use advice with
     */
    public boolean isUseAdviceWith() {
        return useAdviceWith;
    }

    /**
     * Set when using <a href="http://camel.apache.org/advicewith.html">advice with</a> and return <tt>true</tt>. This
     * helps to know that advice with is to be used, and {@link CamelContext} will not be started before the advice with
     * takes place. This helps by ensuring the advice with has been property setup before the {@link CamelContext} is
     * started
     * <p/>
     * <b>Important:</b> It's important to start {@link CamelContext} manually from the unit test after you are done
     * doing all the advice with.
     *
     * @return <tt>true</tt> if you use advice with in your unit tests.
     */
    public TestExecutionConfiguration withUseAdviceWith(boolean useAdviceWith) {
        this.useAdviceWith = useAdviceWith;
        return this;
    }

    public boolean isCreateCamelContextPerClass() {
        return createCamelContextPerClass;
    }

    /**
     * Tells whether {@link CamelContext} should be setup per test or per class.
     * <p/>
     * By default, it will be setup/teardown per test method. This method returns <code>true</code> when the camel test
     * class is annotated with @TestInstance(TestInstance.Lifecycle.PER_CLASS).
     * <p/>
     * <b>Important:</b> Use this with care as the {@link CamelContext} will carry over state from previous tests, such
     * as endpoints, components etc. So you cannot use this in all your tests.
     * <p/>
     *
     * @deprecated Do not use
     * @return     <tt>true</tt> per class, <tt>false</tt> per test.
     */
    @Deprecated(since = "4.7.0")
    protected TestExecutionConfiguration withCreateCamelContextPerClass(boolean createCamelContextPerClass) {
        this.createCamelContextPerClass = createCamelContextPerClass;
        return this;
    }

    public boolean useRouteBuilder() {
        return useRouteBuilder;
    }

    /**
     * Whether to use the RouteBuilder or not
     *
     * @return <tt>true</tt> then {@link CamelContext} will be auto started, <tt>false</tt> then {@link CamelContext}
     *         will <b>not</b> be auto started (you will have to start it manually)
     */
    public TestExecutionConfiguration withUseRouteBuilder(boolean useRouteBuilder) {
        this.useRouteBuilder = useRouteBuilder;
        return this;
    }

    public boolean autoStartContext() {
        return autoStartContext;
    }

    /**
     * Sets to auto-start the context of not.
     *
     * @param      autoStartContext
     * @deprecated                  Do not use
     */
    @Deprecated(since = "4.7.0")
    public TestExecutionConfiguration withAutoStartContext(boolean autoStartContext) {
        this.autoStartContext = autoStartContext;
        return this;
    }
}
