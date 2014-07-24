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

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.management.MBeanServer;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.ComponentConfiguration;
import org.apache.camel.Endpoint;
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.TimerListener;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.apache.camel.api.management.mbean.ManagedProcessorMBean;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ModelHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;

/**
 * @version 
 */
@ManagedResource(description = "Managed CamelContext")
public class ManagedCamelContext extends ManagedPerformanceCounter implements TimerListener, ManagedCamelContextMBean {
    private final ModelCamelContext context;   
    private final LoadTriplet load = new LoadTriplet();

    public ManagedCamelContext(ModelCamelContext context) {
        this.context = context;
        boolean enabled = context.getManagementStrategy().getStatisticsLevel() != ManagementStatisticsLevel.Off;
        setStatisticsEnabled(enabled);
    }

    public CamelContext getContext() {
        return context;
    }

    public String getCamelId() {
        return context.getName();
    }

    public String getManagementName() {
        return context.getManagementName();
    }

    public String getCamelVersion() {
        return context.getVersion();
    }

    public String getState() {
        return context.getStatus().name();
    }

    public String getUptime() {
        return context.getUptime();
    }

    public String getClassResolver() {
        return context.getClassResolver().getClass().getName();
    }

    public String getPackageScanClassResolver() {
        return context.getPackageScanClassResolver().getClass().getName();
    }

    public String getApplicationContextClassName() {
        if (context.getApplicationContextClassLoader() != null) {
            return context.getApplicationContextClassLoader().toString();
        } else {
            return null;
        }
    }

    public Map<String, String> getProperties() {
        if (context.getProperties().isEmpty()) {
            return null;
        }
        return context.getProperties();
    }
    
    public String getProperty(String name) throws Exception {
        return context.getProperty(name);
    }

    public void setProperty(String name, String value) throws Exception {
        context.getProperties().put(name, value);
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

    public Integer getTotalRoutes() {
        return context.getRoutes().size();
    }

    public Integer getStartedRoutes() {
        int started = 0;
        for (Route route : context.getRoutes()) {
            if (context.getRouteStatus(route.getId()).isStarted()) {
                started++;
            }
        }
        return started;
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
        double load1 = load.getLoad1();
        if (Double.isNaN(load1)) {
            // empty string if load statistics is disabled
            return "";
        } else {
            return String.format("%.2f", load1);
        }
    }

    public String getLoad05() {
        double load5 = load.getLoad5();
        if (Double.isNaN(load5)) {
            // empty string if load statistics is disabled
            return "";
        } else {
            return String.format("%.2f", load5);
        }
    }

    public String getLoad15() {
        double load15 = load.getLoad15();
        if (Double.isNaN(load15)) {
            // empty string if load statistics is disabled
            return "";
        } else {
            return String.format("%.2f", load15);
        }
    }

    public boolean isUseBreadcrumb() {
        return context.isUseBreadcrumb();
    }

    public boolean isAllowUseOriginalMessage() {
        return context.isAllowUseOriginalMessage();
    }

    public boolean isMessageHistory() {
        return context.isMessageHistory() != null ? context.isMessageHistory() : false;
    }

    public boolean isUseMDCLogging() {
        return context.isUseMDCLogging();
    }

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

    public void restart() throws Exception {
        context.stop();
        context.start();
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

    public String dumpRestsAsXml() throws Exception {
        List<RestDefinition> rests = context.getRestDefinitions();
        if (rests.isEmpty()) {
            return null;
        }

        // use a routes definition to dump the rests
        RestsDefinition def = new RestsDefinition();
        def.setRests(rests);
        return ModelHelper.dumpModelAsXml(def);
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
        // do not decode so we function as before
        addOrUpdateRoutesFromXml(xml, false);
    }

    public void addOrUpdateRoutesFromXml(String xml, boolean urlDecode) throws Exception {
        // decode String as it may have been encoded, from its xml source
        if (urlDecode) {
            xml = URLDecoder.decode(xml, "UTF-8");
        }

        InputStream is = context.getTypeConverter().mandatoryConvertTo(InputStream.class, xml);
        RoutesDefinition def = context.loadRoutesDefinition(is);
        if (def == null) {
            return;
        }

        // add will remove existing route first
        context.addRouteDefinitions(def.getRoutes());
    }

    public String dumpRoutesStatsAsXml(boolean fullStats, boolean includeProcessors) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("<camelContextStat").append(String.format(" id=\"%s\"", getCamelId()));
        // use substring as we only want the attributes
        String stat = dumpStatsAsXml(fullStats);
        sb.append(" ").append(stat.substring(7, stat.length() - 2)).append(">\n");

        MBeanServer server = getContext().getManagementStrategy().getManagementAgent().getMBeanServer();
        if (server != null) {
            // gather all the routes for this CamelContext, which requires JMX
            String prefix = getContext().getManagementStrategy().getManagementAgent().getIncludeHostName() ? "*/" : "";
            ObjectName query = ObjectName.getInstance("org.apache.camel:context=" + prefix + getContext().getManagementName() + ",type=routes,*");
            Set<ObjectName> routes = server.queryNames(query, null);

            Set<ManagedProcessorMBean> processors = new LinkedHashSet<ManagedProcessorMBean>();
            if (includeProcessors) {
                // gather all the processors for this CamelContext, which requires JMX
                query = ObjectName.getInstance("org.apache.camel:context=" + prefix + getContext().getManagementName() + ",type=processors,*");
                Set<ObjectName> names = server.queryNames(query, null);
                for (ObjectName on : names) {
                    ManagedProcessorMBean processor = MBeanServerInvocationHandler.newProxyInstance(server, on, ManagedProcessorMBean.class, true);
                    processors.add(processor);
                }
            }
            
            // loop the routes, and append the processor stats if needed
            sb.append("  <routeStats>\n");
            for (ObjectName on : routes) {
                ManagedRouteMBean route = MBeanServerInvocationHandler.newProxyInstance(server, on, ManagedRouteMBean.class, true);
                sb.append("    <routeStat").append(String.format(" id=\"%s\"", route.getRouteId()));
                // use substring as we only want the attributes
                stat = route.dumpStatsAsXml(fullStats);
                sb.append(" ").append(stat.substring(7, stat.length() - 2)).append(">\n");

                // add processor details if needed
                if (includeProcessors) {
                    sb.append("      <processorStats>\n");
                    for (ManagedProcessorMBean processor : processors) {
                        // the processor must belong to this route
                        if (route.getRouteId().equals(processor.getRouteId())) {
                            sb.append("        <processorStat").append(String.format(" id=\"%s\"", processor.getProcessorId()));
                            // use substring as we only want the attributes
                            sb.append(" ").append(processor.dumpStatsAsXml(fullStats).substring(7)).append("\n");
                        }
                    }
                    sb.append("      </processorStats>\n");
                }
                sb.append("    </routeStat>\n");
            }
            sb.append("  </routeStats>\n");
        }

        sb.append("</camelContextStat>");
        return sb.toString();
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
        return removed.size();
    }

    public Map<String, Properties> findComponents() throws Exception {
        Map<String, Properties> answer = context.findComponents();
        for (Map.Entry<String, Properties> entry : answer.entrySet()) {
            if (entry.getValue() != null) {
                // remove component as its not serializable over JMX
                entry.getValue().remove("component");
                // .. and components which just list all the components in the JAR/bundle and that is verbose and not needed
                entry.getValue().remove("components");
            }
        }
        return answer;
    }

    public String getComponentDocumentation(String componentName) throws IOException {
        return context.getComponentDocumentation(componentName);
    }

    public String createRouteStaticEndpointJson() {
        return createRouteStaticEndpointJson(true);
    }

    public String createRouteStaticEndpointJson(boolean includeDynamic) {
        return context.createRouteStaticEndpointJson(null, includeDynamic);
    }

    public List<String> findComponentNames() throws Exception {
        Map<String, Properties> map = findComponents();
        return new ArrayList<String>(map.keySet());
    }

    public List<String> completeEndpointPath(String componentName, Map<String, Object> endpointParameters,
                                             String completionText) throws Exception {
        if (completionText == null) {
            completionText = "";
        }
        Component component = context.getComponent(componentName, false);
        if (component != null) {
            ComponentConfiguration configuration = component.createComponentConfiguration();
            configuration.setParameters(endpointParameters);
            return configuration.completeEndpointPath(completionText);
        } else {
            return new ArrayList<String>();
        }
    }

    public String componentParameterJsonSchema(String componentName) throws Exception {
        Component component = context.getComponent(componentName);
        if (component != null) {
            ComponentConfiguration configuration = component.createComponentConfiguration();
            return configuration.createParameterJsonSchema();
        } else {
            return null;
        }
    }

    public void reset(boolean includeRoutes) throws Exception {
        reset();

        // and now reset all routes for this route
        if (includeRoutes) {
            MBeanServer server = getContext().getManagementStrategy().getManagementAgent().getMBeanServer();
            if (server != null) {
                String prefix = getContext().getManagementStrategy().getManagementAgent().getIncludeHostName() ? "*/" : "";
                ObjectName query = ObjectName.getInstance("org.apache.camel:context=" + prefix + getContext().getManagementName() + ",type=routes,*");
                Set<ObjectName> names = server.queryNames(query, null);
                for (ObjectName name : names) {
                    server.invoke(name, "reset", new Object[]{true}, new String[]{"boolean"});
                }
            }
        }
    }

}
