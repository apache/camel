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

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.test.CamelRouteCoverageDumper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit5.CamelTestSupport.ROUTE_COVERAGE_ENABLED;
import static org.apache.camel.test.junit5.TestSupport.isCamelDebugPresent;
import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.create;

/**
 * {@code CamelMainExtension} is a JUnit 5 extension meant to manage the lifecycle of a Camel context simulating a Camel
 * Main application. This extension should not be called explicitly, a test class should only be annotated with the
 * annotation {@link CamelMainTest} to trigger this extension.
 *
 * @see CamelMainTest
 * @see AdviceRouteMapping
 * @see Configure
 * @see ReplaceInRegistry
 * @see DebuggerCallback
 */
final class CamelMainExtension
        implements Extension, BeforeEachCallback, BeforeTestExecutionCallback, AfterTestExecutionCallback {

    /**
     * The logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger(CamelMainExtension.class);
    /**
     * The namespace used to store the state of the test.
     */
    private static final ExtensionContext.Namespace NAMESPACE = create(CamelMainExtension.class);
    /**
     * The name of the key used to store the context of the test.
     */
    private static final String CONTEXT = "context";
    /**
     * The name of the key used to store the watch instance.
     */
    private static final String WATCH = "watch";
    public static final String SEPARATOR = "********************************************************************************";
    /**
     * The utility class allowing to dump the route coverage of a given test.
     */
    private final CamelRouteCoverageDumper routeCoverageDumper = new CamelRouteCoverageDumper();

    @Override
    public void beforeEach(ExtensionContext context) {
        getContextStore(context).getOrComputeIfAbsent(CONTEXT, k -> createCamelMainContextAndStart(context));
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        context.getStore(NAMESPACE).put(WATCH, new StopWatch());
        if (LOG.isInfoEnabled()) {
            final Class<?> requiredTestClass = context.getRequiredTestClass();
            final String currentTestName = context.getDisplayName();
            LOG.info(SEPARATOR);
            LOG.info("Testing: {} ({})", currentTestName, requiredTestClass.getName());
            LOG.info(SEPARATOR);
        }
    }

    @Override
    public void afterTestExecution(ExtensionContext context) throws Exception {
        final long time = context.getStore(NAMESPACE).remove(WATCH, StopWatch.class).taken();
        final String currentTestName = context.getDisplayName();
        if (LOG.isInfoEnabled()) {
            final Class<?> requiredTestClass = context.getRequiredTestClass();
            LOG.info(SEPARATOR);
            LOG.info("Testing done: {} ({})", currentTestName, requiredTestClass.getName());
            LOG.info("Took: {} ({} millis)", TimeUtils.printDuration(time, true), time);
            LOG.info(SEPARATOR);
        }
        dumpRouteCoverageIfNeeded(context, time, currentTestName);
    }

    /**
     * Create and start the Camel context simulating a Camel Main application based on what can be extracted from the
     * given extension context.
     */
    private CamelMainContext createCamelMainContextAndStart(ExtensionContext context) {
        try {
            final CamelMainContext camelMainContext = CamelMainContext.builder(context)
                    .useJmx(useJmx(context) || isRouteCoverageEnabled(context) || isCamelDebugPresent())
                    .build();
            camelMainContext.start();
            return camelMainContext;
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * @return the {@link ExtensionContext.Store} in which the {@link CamelMainContext} should be stored.
     */
    private ExtensionContext.Store getContextStore(ExtensionContext context) {
        ExtensionContext sourceContext = context;
        if (context.getTestInstanceLifecycle().stream()
                .anyMatch(lifecycle -> lifecycle.equals(TestInstance.Lifecycle.PER_CLASS))) {
            // In case it is per class get it from the parent context corresponding to the class context
            sourceContext = context.getParent().orElseThrow();
        }
        return sourceContext.getStore(NAMESPACE);
    }

    /**
     * Dump the route coverage for the given test if it is enabled.
     */
    private void dumpRouteCoverageIfNeeded(ExtensionContext context, long time, String currentTestName) throws Exception {
        // if we should dump route stats, then write that to a file
        if (isRouteCoverageEnabled(context)) {
            final Class<?> requiredTestClass = context.getRequiredTestClass();
            // In case of a {@code @Nested} test class, its name will be prefixed by the name of its outer classes
            String className = requiredTestClass.getName().substring(requiredTestClass.getPackageName().length() + 1);
            String dir = "target/camel-route-coverage";
            String name = String.format("%s-%s.xml", className, StringHelper.before(currentTestName, "("));

            final ModelCamelContext camelContext = getContextStore(context).get(CONTEXT, CamelMainContext.class).context();
            ManagedCamelContext mc = camelContext == null
                    ? null : camelContext.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
            ManagedCamelContextMBean managedCamelContext = mc == null ? null : mc.getManagedCamelContext();
            if (managedCamelContext == null) {
                LOG.warn("Cannot dump route coverage to file as JMX is not enabled. "
                         + "Add camel-management JAR as dependency to enable JMX in the unit test classes.");
            } else {
                routeCoverageDumper.dump(managedCamelContext, camelContext, dir, name, requiredTestClass.getName(),
                        currentTestName,
                        time);
            }
        }
    }

    /**
     * Indicates whether the route coverage is enabled according to the given extension context and the value of the
     * system property {@link org.apache.camel.test.junit5.CamelTestSupport#ROUTE_COVERAGE_ENABLED}.
     * <p/>
     * In case of {@code @Nested} test classes, the value is always extracted from the annotation of the outer class.
     *
     * @return {@code true} if the route coverage is enabled, {@code false} otherwise.
     */
    private boolean isRouteCoverageEnabled(ExtensionContext context) {
        return "true".equalsIgnoreCase(System.getProperty(ROUTE_COVERAGE_ENABLED, "false"))
                || context.getRequiredTestInstances().getAllInstances().get(0).getClass()
                        .getAnnotation(CamelMainTest.class).dumpRouteCoverage();
    }

    /**
     * Indicates whether JMX should be used during testing according to the given extension context.
     * <p/>
     * In case of {@code @Nested} test classes, the value is always extracted from the annotation of the outer class.
     *
     * @return {@code true} if JMX should be used, {@code false} otherwise.
     */
    private boolean useJmx(ExtensionContext context) {
        return context.getRequiredTestInstances().getAllInstances().get(0).getClass()
                .getAnnotation(CamelMainTest.class).useJmx();
    }
}
