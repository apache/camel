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
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ServiceStatus;
import org.apache.camel.TimerListener;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ModelHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.spi.ManagementStrategy;

/**
 * @version 
 */
@ManagedResource(description = "Managed CamelContext")
public class ManagedCamelContext implements TimerListener, ManagedCamelContextMBean {
    private final ModelCamelContext context;   
    private final LoadTriplet load = new LoadTriplet();

    public ManagedCamelContext(ModelCamelContext context) {
        this.context = context;
    }

    public void init(ManagementStrategy strategy) {
        // do nothing
    }

    public CamelContext getContext() {
        return context;
    }

    public String getCamelId() {
        return context.getName();
    }

    public String getCamelVersion() {
        return context.getVersion();
    }

    public String getState() {
        // must use String type to be sure remote JMX can read the attribute without requiring Camel classes.
        ServiceStatus status = context.getStatus();
        // if no status exists then its stopped
        if (status == null) {
            status = ServiceStatus.Stopped;
        }
        return status.name();
    }

    public String getUptime() {
        return context.getUptime();
    }

    public Map<String, String> getProperties() {
        if (context.getProperties().isEmpty()) {
            return null;
        }
        return context.getProperties();
    }

    public Boolean getTracing() {
        return context.isTracing();
    }

    public void setTracing(Boolean tracing) {
        context.setTracing(tracing);
    }

    public Integer getInflightExchanges() {
        return context.getInflightRepository().size();
    }

    public void setTimeout(long timeout) {
        context.getShutdownStrategy().setTimeout(timeout);
    }

    public long getTimeout() {
        return context.getShutdownStrategy().getTimeout();
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        context.getShutdownStrategy().setTimeUnit(timeUnit);
    }

    public TimeUnit getTimeUnit() {
        return context.getShutdownStrategy().getTimeUnit();
    }

    public void setShutdownNowOnTimeout(boolean shutdownNowOnTimeout) {
        context.getShutdownStrategy().setShutdownNowOnTimeout(shutdownNowOnTimeout);
    }

    public boolean isShutdownNowOnTimeout() {
        return context.getShutdownStrategy().isShutdownNowOnTimeout();
    }
    
    public String getLoad01() {
        return String.format("%.2f", load.getLoad1());
    }

    public String getLoad05() {
        return String.format("%.2f", load.getLoad5());
    }

    public String getLoad15() {
        return String.format("%.2f", load.getLoad15());
    }

    @Override
    public void onTimer() {
        load.update(getInflightExchanges());
    }

    public void start() throws Exception {
        if (context.isSuspended()) {
            context.resume();
        } else {
            context.start();
        }
    }

    public void stop() throws Exception {
        context.stop();
    }

    public void suspend() throws Exception {
        context.suspend();
    }

    public void resume() throws Exception {
        if (context.isSuspended()) {
            context.resume();
        } else {
            throw new IllegalStateException("CamelContext is not suspended");
        }
    }

    public void sendBody(String endpointUri, Object body) throws Exception {
        ProducerTemplate template = context.createProducerTemplate();
        try {
            template.sendBody(endpointUri, body);
        } finally {
            template.stop();
        }
    }

    public void sendStringBody(String endpointUri, String body) throws Exception {
        sendBody(endpointUri, body);
    }

    public void sendBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers) throws Exception {
        ProducerTemplate template = context.createProducerTemplate();
        try {
            template.sendBodyAndHeaders(endpointUri, body, headers);
        } finally {
            template.stop();
        }
    }

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

    public Object requestStringBody(String endpointUri, String body) throws Exception {
        return requestBody(endpointUri, body);
    }

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

    public boolean createEndpoint(String uri) throws Exception {
        if (context.hasEndpoint(uri) != null) {
            // endpoint already exists
            return false;
        }

        Endpoint endpoint = context.getEndpoint(uri);
        if (endpoint != null) {
            // ensure endpoint is registered, as the management strategy could have been configured to not always
            // register new endpoints in JMX, so we need to check if its registered, and if not register it manually
            ObjectName on = context.getManagementStrategy().getManagementNamingStrategy().getObjectNameForEndpoint(endpoint);
            if (on != null && !context.getManagementStrategy().getManagementAgent().isRegistered(on)) {
                // register endpoint as mbean
                Object me = context.getManagementStrategy().getManagementObjectStrategy().getManagedObjectForEndpoint(context, endpoint);
                context.getManagementStrategy().getManagementAgent().register(me, on);
            }
            return true;
        } else {
            return false;
        }
    }

    public int removeEndpoints(String pattern) throws Exception {
        // endpoints is always removed from JMX if removed from context
        Collection<Endpoint> removed = context.removeEndpoints(pattern);
        if (removed == null) {
            return 0;
        } else {
            return removed.size();
        }
    }

}
