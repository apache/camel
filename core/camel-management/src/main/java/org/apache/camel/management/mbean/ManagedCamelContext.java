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
package org.apache.camel.management.mbean;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.w3c.dom.Document;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.Producer;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.TimerListener;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.apache.camel.api.management.mbean.ManagedProcessorMBean;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.api.management.mbean.ManagedStepMBean;
import org.apache.camel.model.Model;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RouteTemplatesDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.PluginHelper;

@ManagedResource(description = "Managed CamelContext")
public class ManagedCamelContext extends ManagedPerformanceCounter implements TimerListener, ManagedCamelContextMBean {

    private final CamelContext context;
    private final LoadTriplet load = new LoadTriplet();
    private final LoadThroughput thp = new LoadThroughput();
    private final String jmxDomain;

    public ManagedCamelContext(CamelContext context) {
        this.context = context;
        this.jmxDomain = context.getManagementStrategy().getManagementAgent().getMBeanObjectDomainName();
    }

    @Override
    public void init(ManagementStrategy strategy) {
        super.init(strategy);
        boolean enabled = context.getManagementStrategy().getManagementAgent() != null
                && context.getManagementStrategy().getManagementAgent().getStatisticsLevel() != ManagementStatisticsLevel.Off;
        setStatisticsEnabled(enabled);
    }

    @Override
    public void completedExchange(Exchange exchange, long time) {
        // the camel-context mbean is triggered for every route mbean
        // so we must only trigger on the root level, otherwise the context mbean
        // total counter will be incorrect. For example if an exchange is routed via 3 routes
        // we should only count this as 1 instead of 3.
        UnitOfWork uow = exchange.getUnitOfWork();
        if (uow != null) {
            int level = uow.routeStackLevel();
            if (level <= 1) {
                super.completedExchange(exchange, time);
            }
        } else {
            super.completedExchange(exchange, time);
        }
    }

    @Override
    public void failedExchange(Exchange exchange) {
        // the camel-context mbean is triggered for every route mbean
        // so we must only trigger on the root level, otherwise the context mbean
        // total counter will be incorrect. For example if an exchange is routed via 3 routes
        // we should only count this as 1 instead of 3.
        UnitOfWork uow = exchange.getUnitOfWork();
        if (uow != null) {
            int level = uow.routeStackLevel();
            if (level <= 1) {
                super.failedExchange(exchange);
            }
        } else {
            super.failedExchange(exchange);
        }
    }

    @Override
    public void processExchange(Exchange exchange, String type) {
        // the camel-context mbean is triggered for every route mbean
        // so we must only trigger on the root level, otherwise the context mbean
        // total counter will be incorrect. For example if an exchange is routed via 3 routes
        // we should only count this as 1 instead of 3.
        UnitOfWork uow = exchange.getUnitOfWork();
        if (uow != null) {
            int level = uow.routeStackLevel();
            if (level <= 1) {
                super.processExchange(exchange, type);
            }
        } else {
            super.processExchange(exchange, type);
        }
    }

    public CamelContext getContext() {
        return context;
    }

    @Override
    public String getCamelId() {
        return context.getName();
    }

    @Override
    public String getCamelDescription() {
        return context.getDescription();
    }

    @Override
    public String getManagementName() {
        return context.getManagementName();
    }

    @Override
    public String getCamelVersion() {
        return context.getVersion();
    }

    @Override
    public String getState() {
        return context.getStatus().name();
    }

    @Override
    public String getUptime() {
        return context.getUptime();
    }

    @Override
    public long getUptimeMillis() {
        return context.getUptimeMillis();
    }

    @Override
    public String getManagementStatisticsLevel() {
        if (context.getManagementStrategy().getManagementAgent() != null) {
            return context.getManagementStrategy().getManagementAgent().getStatisticsLevel().name();
        } else {
            return null;
        }
    }

    @Override
    public String getClassResolver() {
        return context.getClassResolver().getClass().getName();
    }

    @Override
    public String getPackageScanClassResolver() {
        return PluginHelper.getPackageScanClassResolver(context).getClass().getName();
    }

    @Override
    public String getApplicationContextClassName() {
        if (context.getApplicationContextClassLoader() != null) {
            return context.getApplicationContextClassLoader().getClass().getName();
        } else {
            return null;
        }
    }

    @Override
    public String getHeadersMapFactoryClassName() {
        return context.getCamelContextExtension().getHeadersMapFactory().getClass().getName();
    }

    @Override
    public Map<String, String> getGlobalOptions() {
        if (context.getGlobalOptions().isEmpty()) {
            return null;
        }
        return context.getGlobalOptions();
    }

    @Override
    public String getGlobalOption(String key) throws Exception {
        return context.getGlobalOption(key);
    }

    @Override
    public void setGlobalOption(String key, String value) throws Exception {
        context.getGlobalOptions().put(key, value);
    }

    @Override
    public Boolean getTracing() {
        return context.isTracing();
    }

    @Override
    public void setTracing(Boolean tracing) {
        context.setTracing(tracing);
    }

    public Integer getInflightExchanges() {
        return (int) super.getExchangesInflight();
    }

    @Override
    public Integer getTotalRoutes() {
        return context.getRoutesSize();
    }

    @Override
    public Integer getStartedRoutes() {
        int started = 0;
        for (Route route : context.getRoutes()) {
            if (context.getRouteController().getRouteStatus(route.getId()).isStarted()) {
                started++;
            }
        }
        return started;
    }

    @Override
    public void setTimeout(long timeout) {
        context.getShutdownStrategy().setTimeout(timeout);
    }

    @Override
    public long getTimeout() {
        return context.getShutdownStrategy().getTimeout();
    }

    @Override
    public void setTimeUnit(TimeUnit timeUnit) {
        context.getShutdownStrategy().setTimeUnit(timeUnit);
    }

    @Override
    public TimeUnit getTimeUnit() {
        return context.getShutdownStrategy().getTimeUnit();
    }

    @Override
    public void setShutdownNowOnTimeout(boolean shutdownNowOnTimeout) {
        context.getShutdownStrategy().setShutdownNowOnTimeout(shutdownNowOnTimeout);
    }

    @Override
    public boolean isShutdownNowOnTimeout() {
        return context.getShutdownStrategy().isShutdownNowOnTimeout();
    }

    @Override
    public String getLoad01() {
        double load1 = load.getLoad1();
        if (Double.isNaN(load1)) {
            // empty string if load statistics is disabled
            return "";
        } else {
            return String.format("%.2f", load1);
        }
    }

    @Override
    public String getLoad05() {
        double load5 = load.getLoad5();
        if (Double.isNaN(load5)) {
            // empty string if load statistics is disabled
            return "";
        } else {
            return String.format("%.2f", load5);
        }
    }

    @Override
    public String getLoad15() {
        double load15 = load.getLoad15();
        if (Double.isNaN(load15)) {
            // empty string if load statistics is disabled
            return "";
        } else {
            return String.format("%.2f", load15);
        }
    }

    @Override
    public String getThroughput() {
        double d = thp.getThroughput();
        if (Double.isNaN(d)) {
            // empty string if load statistics is disabled
            return "";
        } else {
            return String.format("%.2f", d);
        }
    }

    @Override
    public boolean isUseBreadcrumb() {
        return context.isUseBreadcrumb();
    }

    @Override
    public boolean isAllowUseOriginalMessage() {
        return context.isAllowUseOriginalMessage();
    }

    @Override
    public boolean isMessageHistory() {
        return context.isMessageHistory() != null ? context.isMessageHistory() : false;
    }

    @Override
    public boolean isLogMask() {
        return context.isLogMask() != null ? context.isLogMask() : false;
    }

    @Override
    public boolean isUseMDCLogging() {
        return context.isUseMDCLogging();
    }

    @Override
    public boolean isUseDataType() {
        return context.isUseDataType();
    }

    @Override
    public void onTimer() {
        load.update(getInflightExchanges());
        thp.update(getExchangesTotal());
    }

    @Override
    public void start() throws Exception {
        if (context.isSuspended()) {
            context.resume();
        } else {
            context.start();
        }
    }

    @Override
    public void stop() throws Exception {
        context.stop();
    }

    @Override
    public void restart() throws Exception {
        context.stop();
        context.start();
    }

    @Override
    public void suspend() throws Exception {
        context.suspend();
    }

    @Override
    public void resume() throws Exception {
        if (context.isSuspended()) {
            context.resume();
        } else {
            throw new IllegalStateException("CamelContext is not suspended");
        }
    }

    @Override
    public void startAllRoutes() throws Exception {
        context.getRouteController().startAllRoutes();
    }

    @Override
    public boolean canSendToEndpoint(String endpointUri) {
        try {
            Endpoint endpoint = context.getEndpoint(endpointUri);
            if (endpoint != null) {
                try (Producer producer = endpoint.createProducer()) {
                    return producer != null;
                }
            }
        } catch (Exception e) {
            // ignore
        }

        return false;
    }

    @Override
    public void sendBody(String endpointUri, Object body) throws Exception {
        try (ProducerTemplate template = context.createProducerTemplate()) {
            template.sendBody(endpointUri, body);
        }
    }

    @Override
    public void sendStringBody(String endpointUri, String body) throws Exception {
        sendBody(endpointUri, body);
    }

    @Override
    public void sendBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers) throws Exception {
        try (ProducerTemplate template = context.createProducerTemplate()) {
            template.sendBodyAndHeaders(endpointUri, body, headers);
        }
    }

    @Override
    public Object requestBody(String endpointUri, Object body) throws Exception {
        try (ProducerTemplate template = context.createProducerTemplate()) {
            return template.requestBody(endpointUri, body);
        }
    }

    @Override
    public Object requestStringBody(String endpointUri, String body) throws Exception {
        return requestBody(endpointUri, body);
    }

    @Override
    public Object requestBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers) throws Exception {
        try (ProducerTemplate template = context.createProducerTemplate()) {
            return template.requestBodyAndHeaders(endpointUri, body, headers);
        }
    }

    @Override
    public String dumpRestsAsXml() throws Exception {
        return dumpRestsAsXml(false);
    }

    @Override
    public String dumpRestsAsXml(boolean resolvePlaceholders) throws Exception {
        List<RestDefinition> rests = context.getCamelContextExtension().getContextPlugin(Model.class).getRestDefinitions();
        if (rests.isEmpty()) {
            return null;
        }

        RestsDefinition def = new RestsDefinition();
        def.setRests(rests);

        return PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context, def, resolvePlaceholders, true);
    }

    @Override
    public String dumpRoutesAsXml() throws Exception {
        return dumpRoutesAsXml(false, true);
    }

    @Override
    public String dumpRoutesAsXml(boolean resolvePlaceholders) throws Exception {
        return dumpRoutesAsXml(resolvePlaceholders, true);
    }

    @Override
    public String dumpRoutesAsXml(boolean resolvePlaceholders, boolean generatedIds) throws Exception {
        List<RouteDefinition> routes = context.getCamelContextExtension().getContextPlugin(Model.class).getRouteDefinitions();
        if (routes.isEmpty()) {
            return null;
        }

        // use routes definition to dump the routes
        RoutesDefinition def = new RoutesDefinition();
        def.setRoutes(routes);

        return PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context, def, resolvePlaceholders, generatedIds);
    }

    @Override
    public String dumpRoutesAsYaml() throws Exception {
        return dumpRoutesAsYaml(false, false);
    }

    @Override
    public String dumpRoutesAsYaml(boolean resolvePlaceholders) throws Exception {
        return dumpRoutesAsYaml(resolvePlaceholders, false, true);
    }

    @Override
    public String dumpRoutesAsYaml(boolean resolvePlaceholders, boolean uriAsParameters) throws Exception {
        return dumpRoutesAsYaml(resolvePlaceholders, uriAsParameters, true);
    }

    @Override
    public String dumpRoutesAsYaml(boolean resolvePlaceholders, boolean uriAsParameters, boolean generatedIds)
            throws Exception {
        List<RouteDefinition> routes = context.getCamelContextExtension().getContextPlugin(Model.class).getRouteDefinitions();
        if (routes.isEmpty()) {
            return null;
        }

        // use routes definition to dump the routes
        RoutesDefinition def = new RoutesDefinition();
        def.setRoutes(routes);

        return PluginHelper.getModelToYAMLDumper(context).dumpModelAsYaml(context, def, resolvePlaceholders, uriAsParameters,
                generatedIds);
    }

    @Override
    public String dumpRouteTemplatesAsXml() throws Exception {
        List<RouteTemplateDefinition> templates
                = context.getCamelContextExtension().getContextPlugin(Model.class).getRouteTemplateDefinitions();
        if (templates.isEmpty()) {
            return null;
        }

        // use a route templates definition to dump the templates
        RouteTemplatesDefinition def = new RouteTemplatesDefinition();
        def.setRouteTemplates(templates);

        return PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context, def);
    }

    @Override
    public String dumpRoutesStatsAsXml(boolean fullStats, boolean includeProcessors) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("<camelContextStat").append(String.format(" id=\"%s\" state=\"%s\"", getCamelId(), getState()));
        // use substring as we only want the attributes
        String stat = dumpStatsAsXml(fullStats);
        sb.append(" exchangesInflight=\"").append(getInflightExchanges()).append("\"");
        sb.append(" ").append(stat, 7, stat.length() - 2).append(">\n");

        MBeanServer server = getContext().getManagementStrategy().getManagementAgent().getMBeanServer();
        if (server != null) {
            // gather all the routes for this CamelContext, which requires JMX
            String prefix = getContext().getManagementStrategy().getManagementAgent().getIncludeHostName() ? "*/" : "";
            ObjectName query = ObjectName
                    .getInstance(jmxDomain + ":context=" + prefix + getContext().getManagementName() + ",type=routes,*");
            Set<ObjectName> routes = server.queryNames(query, null);

            List<ManagedProcessorMBean> processors = new ArrayList<>();
            if (includeProcessors) {
                // gather all the processors for this CamelContext, which requires JMX
                query = ObjectName.getInstance(
                        jmxDomain + ":context=" + prefix + getContext().getManagementName() + ",type=processors,*");
                Set<ObjectName> names = server.queryNames(query, null);
                for (ObjectName on : names) {
                    ManagedProcessorMBean processor = context.getManagementStrategy().getManagementAgent().newProxyClient(on,
                            ManagedProcessorMBean.class);
                    processors.add(processor);
                }
            }
            processors.sort(new OrderProcessorMBeans());

            // loop the routes, and append the processor stats if needed
            sb.append("  <routeStats>\n");
            for (ObjectName on : routes) {
                ManagedRouteMBean route
                        = context.getManagementStrategy().getManagementAgent().newProxyClient(on, ManagedRouteMBean.class);
                sb.append("    <routeStat")
                        .append(String.format(" id=\"%s\" state=\"%s\"", route.getRouteId(), route.getState()));
                if (route.getSourceLocation() != null) {
                    sb.append(String.format(" sourceLocation=\"%s\"", route.getSourceLocation()));
                }

                // use substring as we only want the attributes
                stat = route.dumpStatsAsXml(fullStats);
                sb.append(" exchangesInflight=\"").append(route.getExchangesInflight()).append("\"");
                sb.append(" ").append(stat, 7, stat.length() - 2).append(">\n");

                // add processor details if needed
                if (includeProcessors) {
                    sb.append("      <processorStats>\n");
                    for (ManagedProcessorMBean processor : processors) {
                        int line = processor.getSourceLineNumber() != null ? processor.getSourceLineNumber() : -1;
                        // the processor must belong to this route
                        if (route.getRouteId().equals(processor.getRouteId())) {
                            sb.append("        <processorStat")
                                    .append(String.format(" id=\"%s\" index=\"%s\" state=\"%s\" sourceLineNumber=\"%s\"",
                                            processor.getProcessorId(), processor.getIndex(), processor.getState(), line));
                            // use substring as we only want the attributes
                            stat = processor.dumpStatsAsXml(fullStats);
                            sb.append(" exchangesInflight=\"").append(processor.getExchangesInflight()).append("\"");
                            sb.append(" ").append(stat, 7, stat.length()).append("\n");
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

    @Override
    public String dumpStepStatsAsXml(boolean fullStats) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("<camelContextStat").append(String.format(" id=\"%s\" state=\"%s\"", getCamelId(), getState()));
        // use substring as we only want the attributes
        String stat = dumpStatsAsXml(fullStats);
        sb.append(" exchangesInflight=\"").append(getInflightExchanges()).append("\"");
        sb.append(" ").append(stat, 7, stat.length() - 2).append(">\n");

        MBeanServer server = getContext().getManagementStrategy().getManagementAgent().getMBeanServer();
        if (server != null) {
            // gather all the routes for this CamelContext, which requires JMX
            String prefix = getContext().getManagementStrategy().getManagementAgent().getIncludeHostName() ? "*/" : "";
            ObjectName query = ObjectName
                    .getInstance(jmxDomain + ":context=" + prefix + getContext().getManagementName() + ",type=routes,*");
            Set<ObjectName> routes = server.queryNames(query, null);

            List<ManagedProcessorMBean> steps = new ArrayList<>();
            // gather all the steps for this CamelContext, which requires JMX
            query = ObjectName
                    .getInstance(jmxDomain + ":context=" + prefix + getContext().getManagementName() + ",type=steps,*");
            Set<ObjectName> names = server.queryNames(query, null);
            for (ObjectName on : names) {
                ManagedStepMBean step
                        = context.getManagementStrategy().getManagementAgent().newProxyClient(on, ManagedStepMBean.class);
                steps.add(step);
            }
            steps.sort(new OrderProcessorMBeans());

            // loop the routes, and append the processor stats if needed
            sb.append("  <routeStats>\n");
            for (ObjectName on : routes) {
                ManagedRouteMBean route
                        = context.getManagementStrategy().getManagementAgent().newProxyClient(on, ManagedRouteMBean.class);
                sb.append("    <routeStat")
                        .append(String.format(" id=\"%s\" state=\"%s\"", route.getRouteId(), route.getState()));
                if (route.getSourceLocation() != null) {
                    sb.append(String.format(" sourceLocation=\"%s\"", route.getSourceLocation()));
                }

                // use substring as we only want the attributes
                stat = route.dumpStatsAsXml(fullStats);
                sb.append(" exchangesInflight=\"").append(route.getExchangesInflight()).append("\"");
                sb.append(" ").append(stat, 7, stat.length() - 2).append(">\n");

                // add steps details if needed
                sb.append("      <stepStats>\n");
                for (ManagedProcessorMBean step : steps) {
                    // the step must belong to this route
                    if (route.getRouteId().equals(step.getRouteId())) {
                        int line = step.getSourceLineNumber() != null ? step.getSourceLineNumber() : -1;
                        sb.append("        <stepStat")
                                .append(String.format(" id=\"%s\" index=\"%s\" state=\"%s\" sourceLineNumber=\"%s\"",
                                        step.getProcessorId(), step.getIndex(), step.getState(), line));
                        // use substring as we only want the attributes
                        stat = step.dumpStatsAsXml(fullStats);
                        sb.append(" exchangesInflight=\"").append(step.getExchangesInflight()).append("\"");
                        sb.append(" ").append(stat, 7, stat.length()).append("\n");
                    }
                    sb.append("      </stepStats>\n");
                }
                sb.append("    </stepStat>\n");
            }
            sb.append("  </routeStats>\n");
        }

        sb.append("</camelContextStat>");
        return sb.toString();
    }

    @Override
    public String dumpRoutesCoverageAsXml() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("<camelContextRouteCoverage")
                .append(String.format(" id=\"%s\" exchangesTotal=\"%s\" totalProcessingTime=\"%s\"", getCamelId(),
                        getExchangesTotal(), getTotalProcessingTime()))
                .append(">\n");

        String xml = dumpRoutesAsXml();
        if (xml != null) {
            // use the coverage xml parser to dump the routes and enrich with coverage stats
            Document dom = RouteCoverageXmlParser.parseXml(context, new ByteArrayInputStream(xml.getBytes()));
            // convert dom back to xml
            String converted = context.getTypeConverter().convertTo(String.class, dom);
            sb.append(converted);
        }

        sb.append("\n</camelContextRouteCoverage>");
        return sb.toString();
    }

    @Override
    public boolean createEndpoint(String uri) throws Exception {
        if (context.hasEndpoint(uri) != null) {
            // endpoint already exists
            return false;
        }

        Endpoint endpoint = context.getEndpoint(uri);
        if (endpoint != null) {
            // ensure endpoint is registered, as the management strategy could have been configured to not always
            // register new endpoints in JMX, so we need to check if its registered, and if not register it manually
            ObjectName on
                    = context.getManagementStrategy().getManagementObjectNameStrategy().getObjectNameForEndpoint(endpoint);
            if (on != null && !context.getManagementStrategy().getManagementAgent().isRegistered(on)) {
                // register endpoint as mbean
                Object me = context.getManagementStrategy().getManagementObjectStrategy().getManagedObjectForEndpoint(context,
                        endpoint);
                context.getManagementStrategy().getManagementAgent().register(me, on);
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int removeEndpoints(String pattern) throws Exception {
        // endpoints is always removed from JMX if removed from context
        Collection<Endpoint> removed = context.removeEndpoints(pattern);
        return removed.size();
    }

    @Override
    public void reset(boolean includeRoutes) throws Exception {
        reset();
        load.reset();
        thp.reset();

        // and now reset all routes for this route
        if (includeRoutes) {
            MBeanServer server = getContext().getManagementStrategy().getManagementAgent().getMBeanServer();
            if (server != null) {
                String prefix = getContext().getManagementStrategy().getManagementAgent().getIncludeHostName() ? "*/" : "";
                ObjectName query = ObjectName
                        .getInstance(jmxDomain + ":context=" + prefix + getContext().getManagementName() + ",type=routes,*");
                Set<ObjectName> names = server.queryNames(query, null);
                for (ObjectName name : names) {
                    server.invoke(name, "reset", new Object[] { true }, new String[] { "boolean" });
                }
            }
        }
    }

    @Override
    public Set<String> componentNames() throws Exception {
        return context.getComponentNames();
    }

    @Override
    public Set<String> languageNames() throws Exception {
        return context.getLanguageNames();
    }

    @Override
    public Set<String> dataFormatNames() throws Exception {
        return context.getDataFormatNames();
    }

    /**
     * Used for sorting the processor mbeans accordingly to their index.
     */
    private static final class OrderProcessorMBeans implements Comparator<ManagedProcessorMBean> {

        @Override
        public int compare(ManagedProcessorMBean o1, ManagedProcessorMBean o2) {
            return o1.getIndex().compareTo(o2.getIndex());
        }
    }

    /**
     * Used for sorting the routes mbeans accordingly to their ids.
     */
    private static final class RouteMBeans implements Comparator<ManagedRouteMBean> {

        @Override
        public int compare(ManagedRouteMBean o1, ManagedRouteMBean o2) {
            return o1.getRouteId().compareToIgnoreCase(o2.getRouteId());
        }
    }

}
