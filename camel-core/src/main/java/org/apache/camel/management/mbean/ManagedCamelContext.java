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
package org.apache.camel.management.mbean;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ServiceStatus;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.ModelHelper;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;

/**
 * @version 
 */
@ManagedResource(description = "Managed CamelContext")
public class ManagedCamelContext {
    private final CamelContext context;

    public ManagedCamelContext(CamelContext context) {
        this.context = context;
    }

    public void init(ManagementStrategy strategy) {
        // do nothing
    }

    public CamelContext getContext() {
        return context;
    }

    @ManagedAttribute(description = "Camel id")
    public String getCamelId() {
        return context.getName();
    }

    @ManagedAttribute(description = "Camel Version")
    public String getCamelVersion() {
        return context.getVersion();
    }

    @ManagedAttribute(description = "Camel State")
    public String getState() {
        // must use String type to be sure remote JMX can read the attribute without requiring Camel classes.
        ServiceStatus status = context.getStatus();
        // if no status exists then its stopped
        if (status == null) {
            status = ServiceStatus.Stopped;
        }
        return status.name();
    }

    @ManagedAttribute(description = "Uptime")
    public String getUptime() {
        return context.getUptime();
    }

    @ManagedAttribute(description = "Camel Properties")
    public Map<String, String> getProperties() {
        if (context.getProperties().isEmpty()) {
            return null;
        }
        return context.getProperties();
    }

    @ManagedAttribute(description = "Tracing")
    public Boolean getTracing() {
        return context.isTracing();
    }

    @ManagedAttribute(description = "Tracing")
    public void setTracing(Boolean tracing) {
        context.setTracing(tracing);
    }

    @ManagedAttribute(description = "Current number of inflight Exchanges")
    public Integer getInflightExchanges() {
        return context.getInflightRepository().size();
    }

    @ManagedAttribute(description = "Shutdown timeout")
    public void setTimeout(long timeout) {
        context.getShutdownStrategy().setTimeout(timeout);
    }

    @ManagedAttribute(description = "Shutdown timeout")
    public long getTimeout() {
        return context.getShutdownStrategy().getTimeout();
    }

    @ManagedAttribute(description = "Shutdown timeout time unit")
    public void setTimeUnit(TimeUnit timeUnit) {
        context.getShutdownStrategy().setTimeUnit(timeUnit);
    }

    @ManagedAttribute(description = "Shutdown timeout time unit")
    public TimeUnit getTimeUnit() {
        return context.getShutdownStrategy().getTimeUnit();
    }

    @ManagedAttribute(description = "Whether to force shutdown now when a timeout occurred")
    public void setShutdownNowOnTimeout(boolean shutdownNowOnTimeout) {
        context.getShutdownStrategy().setShutdownNowOnTimeout(shutdownNowOnTimeout);
    }

    @ManagedAttribute(description = "Whether to force shutdown now when a timeout occurred")
    public boolean isShutdownNowOnTimeout() {
        return context.getShutdownStrategy().isShutdownNowOnTimeout();
    }

    @ManagedOperation(description = "Start Camel")
    public void start() throws Exception {
        if (context.isSuspended()) {
            context.resume();
        } else {
            context.start();
        }
    }

    @ManagedOperation(description = "Stop Camel (shutdown)")
    public void stop() throws Exception {
        context.stop();
    }

    @ManagedOperation(description = "Suspend Camel")
    public void suspend() throws Exception {
        context.suspend();
    }

    @ManagedOperation(description = "Resume Camel")
    public void resume() throws Exception {
        if (context.isSuspended()) {
            context.resume();
        } else {
            throw new IllegalStateException("CamelContext is not suspended");
        }
    }

    @ManagedOperation(description = "Send body (in only)")
    public void sendBody(String endpointUri, Object body) throws Exception {
        ProducerTemplate template = context.createProducerTemplate();
        try {
            template.sendBody(endpointUri, body);
        } finally {
            template.stop();
        }
    }

    @ManagedOperation(description = "Send body (String type) (in only)")
    public void sendStringBody(String endpointUri, String body) throws Exception {
        sendBody(endpointUri, body);
    }

    @ManagedOperation(description = "Send body and headers (in only)")
    public void sendBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers) throws Exception {
        ProducerTemplate template = context.createProducerTemplate();
        try {
            template.sendBodyAndHeaders(endpointUri, body, headers);
        } finally {
            template.stop();
        }
    }

    @ManagedOperation(description = "Request body (in out)")
    public Object requestBody(String endpointUri, Object body) throws Exception {
        ProducerTemplate template = context.createProducerTemplate();
        Object answer = null;
        try {
            answer = template.requestBody(endpointUri, body);
        } finally {
            template.stop();
        }
        return answer;
    }

    @ManagedOperation(description = "Request body (String type) (in out)")
    public Object requestStringBody(String endpointUri, String body) throws Exception {
        return requestBody(endpointUri, body);
    }

    @ManagedOperation(description = "Request body and headers (in out)")
    public Object requestBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers) throws Exception {
        ProducerTemplate template = context.createProducerTemplate();
        Object answer = null;
        try {
            answer = template.requestBodyAndHeaders(endpointUri, body, headers);
        } finally {
            template.stop();
        }
        return answer;
    }

    @ManagedOperation(description = "Dumps the routes as XML")
    public String dumpRoutesAsXml() throws Exception {
        List<RouteDefinition> routes = context.getRouteDefinitions();
        if (routes.isEmpty()) {
            return null;
        }

        // use a routes definition to dump the routes
        RoutesDefinition def = new RoutesDefinition();
        def.setRoutes(routes);
        return ModelHelper.dumpModelAsXml(def);
    }

    @ManagedOperation(description = "Adds or updates existing routes from XML")
    public void addOrUpdateRoutesFromXml(String xml) throws Exception {
        // convert to model from xml
        InputStream is = context.getTypeConverter().mandatoryConvertTo(InputStream.class, xml);
        RoutesDefinition def = context.loadRoutesDefinition(is);
        if (def == null) {
            return;
        }

        // add will remove existing route first
        context.addRouteDefinitions(def.getRoutes());
    }

    /**
     * Creates the endpoint by the given uri
     *
     * @param uri uri of endpoint to create
     * @return <tt>true</tt> if a new endpoint was created, <tt>false</tt> if the endpoint already existed
     * @throws Exception is thrown if error occurred
     */
    @ManagedOperation(description = "Creates the endpoint by the given uri")
    public boolean createEndpoint(String uri) throws Exception {
        if (context.hasEndpoint(uri) != null) {
            // endpoint already exists
            return false;
        }
        // create new endpoint by just getting it
        context.getEndpoint(uri);
        return true;
    }

    /**
     * Removes the endpoint by the given pattern
     *
     * @param pattern the pattern
     * @return number of endpoints removed
     * @throws Exception is thrown if error occurred
     * @see CamelContext#removeEndpoints(String)
     */
    @ManagedOperation(description = "Removes endpoints by the given pattern")
    public int removeEndpoints(String pattern) throws Exception {
        Collection<Endpoint> removed = context.removeEndpoints(pattern);
        if (removed == null) {
            return 0;
        } else {
            return removed.size();
        }
    }

}
