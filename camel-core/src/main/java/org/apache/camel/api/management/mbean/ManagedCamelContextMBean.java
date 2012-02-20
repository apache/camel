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

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;

public interface ManagedCamelContextMBean {

    @ManagedAttribute(description = "Camel ID")
    String getCamelId();

    @ManagedAttribute(description = "Camel Version")
    String getCamelVersion();

    @ManagedAttribute(description = "Camel State")
    String getState();

    @ManagedAttribute(description = "Uptime")
    String getUptime();

    @ManagedAttribute(description = "Camel Properties")
    Map<String, String> getProperties();

    @ManagedAttribute(description = "Tracing")
    Boolean getTracing();

    @ManagedAttribute(description = "Tracing")
    void setTracing(Boolean tracing);

    @ManagedAttribute(description = "Current number of inflight Exchanges")
    Integer getInflightExchanges();

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

    @ManagedOperation(description = "Start Camel")
    void start() throws Exception;

    @ManagedOperation(description = "Stop Camel (shutdown)")
    void stop() throws Exception;

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

    @ManagedOperation(description = "Dumps the routes as XML")
    String dumpRoutesAsXml() throws Exception;

    @ManagedOperation(description = "Adds or updates existing routes from XML")
    void addOrUpdateRoutesFromXml(String xml) throws Exception;

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

}