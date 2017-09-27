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
package org.apache.camel.api.management.mbean;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import javax.management.openmbean.TabularData;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;

public interface ManagedCamelContextMBean extends ManagedPerformanceCounterMBean {

    @ManagedAttribute(description = "Camel ID")
    String getCamelId();

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

    @Deprecated
    @ManagedAttribute(description = "Camel Properties")
    Map<String, String> getProperties();

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

    @Deprecated
    @ManagedOperation(description = "Gets the value of a Camel global option")
    String getProperty(String key) throws Exception;

    /**
     * Gets the value of a CamelContext global option
     *
     * @param key the global option key
     * @return the global option value
     * @throws Exception when an error occurred
     */
    @ManagedOperation(description = "Gets the value of a Camel global option")
    String getGlobalOption(String key) throws Exception;

    @Deprecated
    @ManagedOperation(description = "Sets the value of a Camel global option")
    void setProperty(String key, String value) throws Exception;

    /**
     * Sets the value of a CamelContext property name
     *
     * @param key the global option key
     * @param value the global option value
     * @throws Exception when an error occurred
     */
    @ManagedOperation(description = "Sets the value of a Camel global option")
    void setGlobalOption(String key, String value) throws Exception;

    @ManagedAttribute(description = "Tracing")
    Boolean getTracing();

    @ManagedAttribute(description = "Tracing")
    void setTracing(Boolean tracing);

    /**
     * @deprecated use {@link #getExchangesInflight()}
     */
    @ManagedAttribute(description = "Current number of inflight Exchanges")
    @Deprecated
    Integer getInflightExchanges();

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

    @ManagedOperation(description = "Adds or updates existing routes from XML")
    void addOrUpdateRoutesFromXml(String xml) throws Exception;

    @ManagedOperation(description = "Adds or updates existing routes from XML")
    void addOrUpdateRoutesFromXml(String xml, boolean urlDecode) throws Exception;

    @ManagedOperation(description = "Dumps the CamelContext and routes stats as XML")
    String dumpRoutesStatsAsXml(boolean fullStats, boolean includeProcessors) throws Exception;

    @ManagedOperation(description = "Dumps the routes coverage as XML")
    String dumpRoutesCoverageAsXml() throws Exception;

    /**
     * Creates the endpoint by the given uri
     *
     * @param uri uri of endpoint to create
     * @return <tt>true</tt> if a new endpoint was created, <tt>false</tt> if the endpoint already existed
     * @throws Exception is thrown if error occurred
     */
    @ManagedOperation(description = "Creates the endpoint by the given URI")
    boolean createEndpoint(String uri) throws Exception;

    /**
     * Removes the endpoint by the given pattern
     *
     * @param pattern the pattern
     * @return number of endpoints removed
     * @throws Exception is thrown if error occurred
     * @see org.apache.camel.CamelContext#removeEndpoints(String)
     */
    @ManagedOperation(description = "Removes endpoints by the given pattern")
    int removeEndpoints(String pattern) throws Exception;

    /**
     * Find information about all the Camel components available in the classpath and {@link org.apache.camel.spi.Registry}.
     *
     * @return a map with the component name, and value with component details.
     * @throws Exception is thrown if error occurred
     */
    @ManagedOperation(description = "Find all Camel components available in the classpath")
    Map<String, Properties> findComponents() throws Exception;

    /**
     * Find information about all the EIPs from camel-core.
     *
     * @return a map with node id, and value with EIP details.
     * @throws Exception is thrown if error occurred
     */
    @ManagedOperation(description = "Find all Camel EIPs from camel-core")
    Map<String, Properties> findEips() throws Exception;

    /**
     * Find the names of all the EIPs from camel-core.
     *
     * @return a list with the names of the camel EIPs
     * @throws Exception is thrown if error occurred
     */
    @ManagedOperation(description = "Find all Camel EIP names from camel-core")
    List<String> findEipNames() throws Exception;

    /**
     * Find the names of all the Camel components available in the classpath and {@link org.apache.camel.spi.Registry}.
     *
     * @return a list with the names of the camel components
     * @throws Exception is thrown if error occurred
     */
    @ManagedOperation(description = "Find all Camel components names available in the classpath")
    List<String> findComponentNames() throws Exception;

    /**
     * Find information about all the Camel components available in the classpath and {@link org.apache.camel.spi.Registry}.
     *
     * @return a list with the data
     * @throws Exception is thrown if error occurred
     */
    @ManagedOperation(description = "List all Camel components available in the classpath")
    TabularData listComponents() throws Exception;

    /**
     * Find information about all the EIPs from camel-core.
     *
     * @return a list with the data
     * @throws Exception is thrown if error occurred
     */
    @ManagedOperation(description = "List all Camel EIPs from camel-core")
    TabularData listEips() throws Exception;

    /**
     * Returns the JSON schema representation with information about the component and the endpoint parameters it supports
     *
     * @param componentName the name of the component to lookup
     * @throws Exception is thrown if error occurred
     */
    @ManagedOperation(description = "Returns the JSON schema representation of the endpoint parameters for the given component name")
    @Deprecated
    String componentParameterJsonSchema(String componentName) throws Exception;

    /**
     * Returns the JSON schema representation with information about the data format and the parameters it supports
     *
     * @param dataFormatName the name of the data format to lookup
     * @throws Exception is thrown if error occurred
     */
    @ManagedOperation(description = "Returns the JSON schema representation of the data format parameters for the given data format name")
    String dataFormatParameterJsonSchema(String dataFormatName) throws Exception;

    /**
     * Returns the JSON schema representation with information about the language and the parameters it supports
     *
     * @param languageName the name of the language to lookup
     * @throws Exception is thrown if error occurred
     */
    @ManagedOperation(description = "Returns the JSON schema representation of the language parameters for the given language name")
    String languageParameterJsonSchema(String languageName) throws Exception;

    /**
     * Returns the JSON schema representation with information about the EIP and the parameters it supports
     *
     * @param eipName the name of the EIP to lookup
     * @throws Exception is thrown if error occurred
     */
    @ManagedOperation(description = "Returns the JSON schema representation of the EIP parameters for the given EIP name")
    String eipParameterJsonSchema(String eipName) throws Exception;

    /**
     * Returns a JSON schema representation of the EIP parameters for the given EIP by its id.
     *
     * @param nameOrId the name of the EIP ({@link org.apache.camel.NamedNode#getShortName()} or a node id to refer to a specific node from the routes.
     * @param includeAllOptions whether to include non configured options also (eg default options)
     * @return the json or <tt>null</tt> if the eipName or the id was not found
     */
    @ManagedOperation(description = "Returns a JSON schema representation of the EIP parameters for the given EIP by its id")
    String explainEipJson(String nameOrId, boolean includeAllOptions);

    /**
     * Returns a JSON schema representation of the component parameters (not endpoint parameters) for the given component by its id.
     *
     * @param componentName the id of the component
     * @param includeAllOptions whether to include non configured options also (eg default options)
     */
    @ManagedOperation(description = " Returns a JSON schema representation of the component parameters for the given component by its id")
    String explainComponentJson(String componentName, boolean includeAllOptions) throws Exception;

    /**
     * Returns a JSON schema representation of the endpoint parameters for the given endpoint uri
     *
     * @param uri the endpoint uri
     * @param includeAllOptions whether to include non configured options also (eg default options)
     */
    @ManagedOperation(description = " Returns a JSON schema representation of the endpoint parameters for the given endpoint uri")
    String explainEndpointJson(String uri, boolean includeAllOptions) throws Exception;

    /**
     * Resets all the performance counters.
     *
     * @param includeRoutes  whether to reset all routes as well.
     * @throws Exception is thrown if error occurred
     */
    @ManagedOperation(description = "Reset counters")
    void reset(boolean includeRoutes) throws Exception;

    /**
     * Helper method for tooling which returns the completion list of the endpoint path
     * from the given endpoint name, properties and current path expression.
     * <p/>
     * For example if using the file endpoint, this should complete a list of files (rather like bash completion)
     * or for an ActiveMQ component this should complete the list of queues or topics.
     *
     * @param componentName  the component name
     * @param endpointParameters  parameters of the endpoint
     * @param completionText  the entered text which we want to have completion suggestions for
     * @throws Exception is thrown if error occurred
     */
    @ManagedOperation(description = "Returns the list of available endpoint paths for the given component name, endpoint properties and completion text")
    @Deprecated
    List<String> completeEndpointPath(String componentName, Map<String, Object> endpointParameters, String completionText) throws Exception;

    /**
     * Returns the HTML documentation for the given camel component
     *
     * @param componentName  the component name
     * @deprecated use camel-catalog instead
     */
    @ManagedOperation(description = "Returns the HTML documentation for the given camel component")
    @Deprecated
    String getComponentDocumentation(String componentName) throws IOException;

    @ManagedOperation(description = "Returns the JSON representation of all the static and dynamic endpoints defined in all the routes")
    String createRouteStaticEndpointJson();

    @ManagedOperation(description = "Returns the JSON representation of all the static endpoints (and possible dynamic) defined in all the routes")
    String createRouteStaticEndpointJson(boolean includeDynamic);

}