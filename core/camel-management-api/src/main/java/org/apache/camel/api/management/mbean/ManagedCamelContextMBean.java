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
package org.apache.camel.api.management.mbean;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;

public interface ManagedCamelContextMBean extends ManagedPerformanceCounterMBean {

    @ManagedAttribute(description = "Camel ID")
    String getCamelId();

    @ManagedAttribute(description = "Camel Description")
    String getCamelDescription();

    @ManagedAttribute(description = "Camel ManagementName")
    String getManagementName();

    @ManagedAttribute(description = "Camel Version")
    String getCamelVersion();

    @ManagedAttribute(description = "Camel State")
    String getState();

    @ManagedAttribute(description = "Uptime [human readable text]")
    String getUptime();

    @ManagedAttribute(description = "Uptime [milliseconds]")
    long getUptimeMillis();

    @ManagedAttribute(description = "Camel Management StatisticsLevel")
    String getManagementStatisticsLevel();

    @ManagedAttribute(description = "Camel Global Options")
    Map<String, String> getGlobalOptions();

    @ManagedAttribute(description = "ClassResolver class name")
    String getClassResolver();

    @ManagedAttribute(description = "PackageScanClassResolver class name")
    String getPackageScanClassResolver();

    @ManagedAttribute(description = "ApplicationContext class name")
    String getApplicationContextClassName();

    @ManagedAttribute(description = "HeadersMapFactory class name")
    String getHeadersMapFactoryClassName();

    /**
     * Gets the value of a CamelContext global option
     *
     * @param  key       the global option key
     * @return           the global option value
     * @throws Exception when an error occurred
     */
    @ManagedOperation(description = "Gets the value of a Camel global option")
    String getGlobalOption(String key) throws Exception;

    /**
     * Sets the value of a CamelContext property name
     *
     * @param  key       the global option key
     * @param  value     the global option value
     * @throws Exception when an error occurred
     */
    @ManagedOperation(description = "Sets the value of a Camel global option")
    void setGlobalOption(String key, String value) throws Exception;

    @ManagedAttribute(description = "Tracing")
    Boolean getTracing();

    @ManagedAttribute(description = "Tracing")
    void setTracing(Boolean tracing);

    @ManagedAttribute(description = "Total number of routes")
    Integer getTotalRoutes();

    @ManagedAttribute(description = "Current number of started routes")
    Integer getStartedRoutes();

    @ManagedAttribute(description = "Shutdown timeout")
    void setTimeout(long timeout);

    @ManagedAttribute(description = "Shutdown timeout")
    long getTimeout();

    @ManagedAttribute(description = "Shutdown timeout time unit")
    void setTimeUnit(TimeUnit timeUnit);

    @ManagedAttribute(description = "Shutdown timeout time unit")
    TimeUnit getTimeUnit();

    @ManagedAttribute(description = "Whether to force shutdown now when a timeout occurred")
    void setShutdownNowOnTimeout(boolean shutdownNowOnTimeout);

    @ManagedAttribute(description = "Whether to force shutdown now when a timeout occurred")
    boolean isShutdownNowOnTimeout();

    @ManagedAttribute(description = "Average load over the last minute")
    String getLoad01();

    @ManagedAttribute(description = "Average load over the last five minutes")
    String getLoad05();

    @ManagedAttribute(description = "Average load over the last fifteen minutes")
    String getLoad15();

    @ManagedAttribute(description = "Throughput message/second")
    String getThroughput();

    @ManagedAttribute(description = "Whether breadcrumbs is in use")
    boolean isUseBreadcrumb();

    @ManagedAttribute(description = "Whether allowing access to the original message during routing")
    boolean isAllowUseOriginalMessage();

    @ManagedAttribute(description = "Whether message history is enabled")
    boolean isMessageHistory();

    @ManagedAttribute(description = "Whether security mask for Logging is enabled")
    boolean isLogMask();

    @ManagedAttribute(description = "Whether MDC logging is supported")
    boolean isUseMDCLogging();

    @ManagedAttribute(description = "Whether Message DataType is enabled")
    boolean isUseDataType();

    @ManagedOperation(description = "Start Camel")
    void start() throws Exception;

    @ManagedOperation(description = "Stop Camel (shutdown)")
    void stop() throws Exception;

    @ManagedOperation(description = "Restart Camel (stop and then start)")
    void restart() throws Exception;

    @ManagedOperation(description = "Suspend Camel")
    void suspend() throws Exception;

    @ManagedOperation(description = "Resume Camel")
    void resume() throws Exception;

    @ManagedOperation(description = "Starts all the routes which currently is not started")
    void startAllRoutes() throws Exception;

    @ManagedOperation(description = "Whether its possible to send to the endpoint (eg the endpoint has a producer)")
    boolean canSendToEndpoint(String endpointUri);

    @ManagedOperation(description = "Send body (in only)")
    void sendBody(String endpointUri, Object body) throws Exception;

    @ManagedOperation(description = "Send body (String type) (in only)")
    void sendStringBody(String endpointUri, String body) throws Exception;

    @ManagedOperation(description = "Send body and headers (in only)")
    void sendBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers) throws Exception;

    @ManagedOperation(description = "Request body (in out)")
    Object requestBody(String endpointUri, Object body) throws Exception;

    @ManagedOperation(description = "Request body (String type) (in out)")
    Object requestStringBody(String endpointUri, String body) throws Exception;

    @ManagedOperation(description = "Request body and headers (in out)")
    Object requestBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers) throws Exception;

    @ManagedOperation(description = "Dumps the rests as XML")
    String dumpRestsAsXml() throws Exception;

    @ManagedOperation(description = "Dumps the rests as XML")
    String dumpRestsAsXml(boolean resolvePlaceholders) throws Exception;

    @ManagedOperation(description = "Dumps the routes as XML")
    String dumpRoutesAsXml() throws Exception;

    @ManagedOperation(description = "Dumps the routes as XML")
    String dumpRoutesAsXml(boolean resolvePlaceholders) throws Exception;

    @ManagedOperation(description = "Dumps the routes as XML")
    String dumpRoutesAsXml(boolean resolvePlaceholders, boolean generatedIds) throws Exception;

    @ManagedOperation(description = "Dumps the CamelContext and routes stats as XML")
    String dumpRoutesStatsAsXml(boolean fullStats, boolean includeProcessors) throws Exception;

    @ManagedOperation(description = "Dumps the CamelContext and routes and steps stats as XML")
    String dumpStepStatsAsXml(boolean fullStats) throws Exception;

    @ManagedOperation(description = "Dumps the routes coverage as XML")
    String dumpRoutesCoverageAsXml() throws Exception;

    @ManagedOperation(description = "Dumps the route templates as XML")
    String dumpRouteTemplatesAsXml() throws Exception;

    @ManagedOperation(description = "Dumps the routes as YAML")
    String dumpRoutesAsYaml() throws Exception;

    @ManagedOperation(description = "Dumps the routes as YAML")
    String dumpRoutesAsYaml(boolean resolvePlaceholders) throws Exception;

    @ManagedOperation(description = "Dumps the routes as YAML")
    String dumpRoutesAsYaml(boolean resolvePlaceholders, boolean uriAsParameters) throws Exception;

    @ManagedOperation(description = "Dumps the routes as YAML")
    String dumpRoutesAsYaml(boolean resolvePlaceholders, boolean uriAsParameters, boolean generatedIds) throws Exception;

    /**
     * Creates the endpoint by the given uri
     *
     * @param  uri       uri of endpoint to create
     * @return           <tt>true</tt> if a new endpoint was created, <tt>false</tt> if the endpoint already existed
     * @throws Exception is thrown if error occurred
     */
    @ManagedOperation(description = "Creates the endpoint by the given URI")
    boolean createEndpoint(String uri) throws Exception;

    /**
     * Removes the endpoint by the given pattern
     *
     * @param  pattern   the pattern
     * @return           number of endpoints removed
     * @throws Exception is thrown if error occurred
     * @see              org.apache.camel.CamelContext#removeEndpoints(String)
     */
    @ManagedOperation(description = "Removes endpoints by the given pattern")
    int removeEndpoints(String pattern) throws Exception;

    /**
     * Resets all the performance counters.
     *
     * @param  includeRoutes whether to reset all routes as well.
     * @throws Exception     is thrown if error occurred
     */
    @ManagedOperation(description = "Reset counters")
    void reset(boolean includeRoutes) throws Exception;

    /**
     * The names of the components currently registered
     */
    @ManagedOperation(description = "The names of the components currently registered")
    Set<String> componentNames() throws Exception;

    /**
     * The names of the languages currently registered
     */
    @ManagedOperation(description = "The names of the languages currently registered")
    Set<String> languageNames() throws Exception;

    /**
     * The names of the data formats currently registered
     */
    @ManagedOperation(description = "The names of the data formats currently registered")
    Set<String> dataFormatNames() throws Exception;

}
