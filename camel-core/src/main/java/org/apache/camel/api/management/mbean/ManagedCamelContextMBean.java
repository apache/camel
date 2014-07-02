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

    @ManagedAttribute(description = "Uptime")
    String getUptime();

    @ManagedAttribute(description = "Camel Properties")
    Map<String, String> getProperties();

    @ManagedAttribute(description = "ClassResolver class name")
    String getClassResolver();

    @ManagedAttribute(description = "PackageScanClassResolver class name")
    String getPackageScanClassResolver();

    @ManagedAttribute(description = "ApplicationContext class name")
    String getApplicationContextClassName();

    /**
     * Gets the value of a CamelContext property name
     *
     * @param name the name of the property
     * @return String the value of the property
     * @throws Exception is thrown if error occurred
     */
    @ManagedOperation(description = "Get the value of a Camel property")
    String getProperty(String name) throws Exception;
    
    /**
     * Sets the value of a CamelContext property name
     *
     * @param name the name of the property
     * @param value the new value of the property
     * @throws Exception is thrown if error occurred
     */
    @ManagedOperation(description = "Set the value of a Camel property")
    void setProperty(String name, String value) throws Exception;
    
    @ManagedAttribute(description = "Tracing")
    Boolean getTracing();

    @ManagedAttribute(description = "Tracing")
    void setTracing(Boolean tracing);

    @ManagedAttribute(description = "Message History")
    Boolean getMessageHistory();

    @ManagedAttribute(description = "Current number of inflight Exchanges")
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

    @ManagedAttribute(description = "Whether MDC logging is supported")
    boolean isUseMDCLogging();

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

    @ManagedOperation(description = "Dumps the routes as XML")
    String dumpRoutesAsXml() throws Exception;

    @ManagedOperation(description = "Adds or updates existing routes from XML")
    void addOrUpdateRoutesFromXml(String xml) throws Exception;

    @ManagedOperation(description = "Adds or updates existing routes from XML")
    void addOrUpdateRoutesFromXml(String xml, boolean urlDecode) throws Exception;

    @ManagedOperation(description = "Dumps the routes stats as XML")
    String dumpRoutesStatsAsXml(boolean fullStats, boolean includeProcessors) throws Exception;

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
     * Find the names of all the Camel components available in the classpath and {@link org.apache.camel.spi.Registry}.
     *
     * @return a list with the names of the camel components
     * @throws Exception is thrown if error occurred
     */
    @ManagedOperation(description = "Find all Camel components names available in the classpath")
    List<String> findComponentNames() throws Exception;


    /**
     * Returns the JSON schema representation of the endpoint parameters for the given component name
     *
     * @param componentName the name of the component to lookup
     * @throws Exception is thrown if error occurred
     */
    @ManagedOperation(description = "Returns the JSON schema representation of the endpoint parameters for the given component name")
    String componentParameterJsonSchema(String componentName) throws Exception;

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
    List<String> completeEndpointPath(String componentName, Map<String, Object> endpointParameters, String completionText) throws Exception;

    /**
     * Returns the HTML documentation for the given camel component
     *
     * @param componentName  the component name
     */
    @ManagedOperation(description = "Returns the HTML documentation for the given camel component")
    String getComponentDocumentation(String componentName) throws IOException;

    @ManagedOperation(description = "Returns the JSON representation of all the static and dynamic endpoints defined in all the routes")
    String createRouteStaticEndpointJson();

    @ManagedOperation(description = "Returns the JSON representation of all the static endpoints (and possible dynamic) defined in all the routes")
    String createRouteStaticEndpointJson(boolean includeDynamic);

}