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
import java.io.InputStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.w3c.dom.Document;

import org.apache.camel.CamelContext;
import org.apache.camel.CatalogCamelContext;
import org.apache.camel.Endpoint;
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
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ModelHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.XmlLineNumberParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "Managed CamelContext")
public class ManagedCamelContext extends ManagedPerformanceCounter implements TimerListener, ManagedCamelContextMBean {

    private static final Logger LOG = LoggerFactory.getLogger(ManagedCamelContext.class);

    private final ModelCamelContext context;
    private final LoadTriplet load = new LoadTriplet();
    private final String jmxDomain;

    public ManagedCamelContext(ModelCamelContext context) {
        this.context = context;
        this.jmxDomain = context.getManagementStrategy().getManagementAgent().getMBeanObjectDomainName();
    }

    @Override
    public void init(ManagementStrategy strategy) {
        super.init(strategy);
        boolean enabled = context.getManagementStrategy().getManagementAgent() != null && context.getManagementStrategy().getManagementAgent().getStatisticsLevel() != ManagementStatisticsLevel.Off;
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

    public long getUptimeMillis() {
        return context.getUptimeMillis();
    }

    public String getManagementStatisticsLevel() {
        if (context.getManagementStrategy().getManagementAgent() != null) {
            return context.getManagementStrategy().getManagementAgent().getStatisticsLevel().name();
        } else {
            return null;
        }
    }

    public String getClassResolver() {
        return context.getClassResolver().getClass().getName();
    }

    public String getPackageScanClassResolver() {
        return context.getPackageScanClassResolver().getClass().getName();
    }

    public String getApplicationContextClassName() {
        if (context.getApplicationContextClassLoader() != null) {
            return context.getApplicationContextClassLoader().getClass().getName();
        } else {
            return null;
        }
    }

    @Override
    public String getHeadersMapFactoryClassName() {
        return context.getHeadersMapFactory().getClass().getName();
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

    public Boolean getTracing() {
        return context.isTracing();
    }

    public void setTracing(Boolean tracing) {
        context.setTracing(tracing);
    }

    public Integer getInflightExchanges() {
        return (int) super.getExchangesInflight();
    }

    public Integer getTotalRoutes() {
        return context.getRoutes().size();
    }

    public Integer getStartedRoutes() {
        int started = 0;
        for (Route route : context.getRoutes()) {
            if (context.getRouteController().getRouteStatus(route.getId()).isStarted()) {
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

    public boolean isLogMask() {
        return context.isLogMask() != null ? context.isLogMask() : false;
    }

    public boolean isUseMDCLogging() {
        return context.isUseMDCLogging();
    }

    public boolean isUseDataType() {
        return context.isUseDataType();
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

    public void startAllRoutes() throws Exception {
        context.getRouteController().startAllRoutes();
    }

    public boolean canSendToEndpoint(String endpointUri) {
        try {
            Endpoint endpoint = context.getEndpoint(endpointUri);
            if (endpoint != null) {
                Producer producer = endpoint.createProducer();
                return producer != null;
            }
        } catch (Exception e) {
            // ignore
        }

        return false;
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
        return dumpRestsAsXml(false);
    }

    @Override
    public String dumpRestsAsXml(boolean resolvePlaceholders) throws Exception {
        List<RestDefinition> rests = context.getRestDefinitions();
        if (rests.isEmpty()) {
            return null;
        }

        // use a routes definition to dump the rests
        RestsDefinition def = new RestsDefinition();
        def.setRests(rests);
        String xml = ModelHelper.dumpModelAsXml(context, def);

        // if resolving placeholders we parse the xml, and resolve the property placeholders during parsing
        if (resolvePlaceholders) {
            final AtomicBoolean changed = new AtomicBoolean();
            InputStream is = new ByteArrayInputStream(xml.getBytes("UTF-8"));
            Document dom = XmlLineNumberParser.parseXml(is, text -> {
                try {
                    String after = getContext().resolvePropertyPlaceholders(text);
                    if (!changed.get()) {
                        changed.set(!text.equals(after));
                    }
                    return after;
                } catch (Exception e) {
                    // ignore
                    return text;
                }
            });
            // okay there were some property placeholder replaced so re-create the model
            if (changed.get()) {
                xml = context.getTypeConverter().mandatoryConvertTo(String.class, dom);
                RestsDefinition copy = ModelHelper.createModelFromXml(context, xml, RestsDefinition.class);
                xml = ModelHelper.dumpModelAsXml(context, copy);
            }
        }

        return xml;
    }

    public String dumpRoutesAsXml() throws Exception {
        return dumpRoutesAsXml(false, false);
    }

    @Override
    public String dumpRoutesAsXml(boolean resolvePlaceholders) throws Exception {
        return dumpRoutesAsXml(resolvePlaceholders, false);
    }

    @Override
    public String dumpRoutesAsXml(boolean resolvePlaceholders, boolean resolveDelegateEndpoints) throws Exception {
        List<RouteDefinition> routes = context.getRouteDefinitions();
        if (routes.isEmpty()) {
            return null;
        }

        // use a routes definition to dump the routes
        RoutesDefinition def = new RoutesDefinition();
        def.setRoutes(routes);

        return ModelHelper.dumpModelAsXml(context, def, resolvePlaceholders, resolveDelegateEndpoints);
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
        try {
            // add will remove existing route first
            context.addRouteDefinitions(is);
        } catch (Exception e) {
            // log the error as warn as the management api may be invoked remotely over JMX which does not propagate such exception
            String msg = "Error updating routes from xml: " + xml + " due: " + e.getMessage();
            LOG.warn(msg, e);
            throw e;
        }
    }

    public String dumpRoutesStatsAsXml(boolean fullStats, boolean includeProcessors) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("<camelContextStat").append(String.format(" id=\"%s\" state=\"%s\"", getCamelId(), getState()));
        // use substring as we only want the attributes
        String stat = dumpStatsAsXml(fullStats);
        sb.append(" exchangesInflight=\"").append(getInflightExchanges()).append("\"");
        sb.append(" ").append(stat.substring(7, stat.length() - 2)).append(">\n");

        MBeanServer server = getContext().getManagementStrategy().getManagementAgent().getMBeanServer();
        if (server != null) {
            // gather all the routes for this CamelContext, which requires JMX
            String prefix = getContext().getManagementStrategy().getManagementAgent().getIncludeHostName() ? "*/" : "";
            ObjectName query = ObjectName.getInstance(jmxDomain + ":context=" + prefix + getContext().getManagementName() + ",type=routes,*");
            Set<ObjectName> routes = server.queryNames(query, null);

            List<ManagedProcessorMBean> processors = new ArrayList<>();
            if (includeProcessors) {
                // gather all the processors for this CamelContext, which requires JMX
                query = ObjectName.getInstance(jmxDomain + ":context=" + prefix + getContext().getManagementName() + ",type=processors,*");
                Set<ObjectName> names = server.queryNames(query, null);
                for (ObjectName on : names) {
                    ManagedProcessorMBean processor = context.getManagementStrategy().getManagementAgent().newProxyClient(on, ManagedProcessorMBean.class);
                    processors.add(processor);
                }
            }
            processors.sort(new OrderProcessorMBeans());

            // loop the routes, and append the processor stats if needed
            sb.append("  <routeStats>\n");
            for (ObjectName on : routes) {
                ManagedRouteMBean route = context.getManagementStrategy().getManagementAgent().newProxyClient(on, ManagedRouteMBean.class);
                sb.append("    <routeStat").append(String.format(" id=\"%s\" state=\"%s\"", route.getRouteId(), route.getState()));
                // use substring as we only want the attributes
                stat = route.dumpStatsAsXml(fullStats);
                sb.append(" exchangesInflight=\"").append(route.getExchangesInflight()).append("\"");
                sb.append(" ").append(stat.substring(7, stat.length() - 2)).append(">\n");

                // add processor details if needed
                if (includeProcessors) {
                    sb.append("      <processorStats>\n");
                    for (ManagedProcessorMBean processor : processors) {
                        // the processor must belong to this route
                        if (route.getRouteId().equals(processor.getRouteId())) {
                            sb.append("        <processorStat").append(String.format(" id=\"%s\" index=\"%s\" state=\"%s\"", processor.getProcessorId(), processor.getIndex(), processor.getState()));
                            // use substring as we only want the attributes
                            stat = processor.dumpStatsAsXml(fullStats);
                            sb.append(" exchangesInflight=\"").append(processor.getExchangesInflight()).append("\"");
                            sb.append(" ").append(stat.substring(7)).append("\n");
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

    public String dumpStepStatsAsXml(boolean fullStats) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("<camelContextStat").append(String.format(" id=\"%s\" state=\"%s\"", getCamelId(), getState()));
        // use substring as we only want the attributes
        String stat = dumpStatsAsXml(fullStats);
        sb.append(" exchangesInflight=\"").append(getInflightExchanges()).append("\"");
        sb.append(" ").append(stat.substring(7, stat.length() - 2)).append(">\n");

        MBeanServer server = getContext().getManagementStrategy().getManagementAgent().getMBeanServer();
        if (server != null) {
            // gather all the routes for this CamelContext, which requires JMX
            String prefix = getContext().getManagementStrategy().getManagementAgent().getIncludeHostName() ? "*/" : "";
            ObjectName query = ObjectName.getInstance(jmxDomain + ":context=" + prefix + getContext().getManagementName() + ",type=routes,*");
            Set<ObjectName> routes = server.queryNames(query, null);

            List<ManagedProcessorMBean> steps = new ArrayList<>();
            // gather all the steps for this CamelContext, which requires JMX
            query = ObjectName.getInstance(jmxDomain + ":context=" + prefix + getContext().getManagementName() + ",type=steps,*");
            Set<ObjectName> names = server.queryNames(query, null);
            for (ObjectName on : names) {
                ManagedStepMBean step = context.getManagementStrategy().getManagementAgent().newProxyClient(on, ManagedStepMBean.class);
                steps.add(step);
            }
            steps.sort(new OrderProcessorMBeans());

            // loop the routes, and append the processor stats if needed
            sb.append("  <routeStats>\n");
            for (ObjectName on : routes) {
                ManagedRouteMBean route = context.getManagementStrategy().getManagementAgent().newProxyClient(on, ManagedRouteMBean.class);
                sb.append("    <routeStat").append(String.format(" id=\"%s\" state=\"%s\"", route.getRouteId(), route.getState()));
                // use substring as we only want the attributes
                stat = route.dumpStatsAsXml(fullStats);
                sb.append(" exchangesInflight=\"").append(route.getExchangesInflight()).append("\"");
                sb.append(" ").append(stat.substring(7, stat.length() - 2)).append(">\n");

                // add steps details if needed
                sb.append("      <stepStats>\n");
                for (ManagedProcessorMBean processor : steps) {
                    // the step must belong to this route
                    if (route.getRouteId().equals(processor.getRouteId())) {
                        sb.append("        <stepStat").append(String.format(" id=\"%s\" index=\"%s\" state=\"%s\"", processor.getProcessorId(), processor.getIndex(), processor.getState()));
                        // use substring as we only want the attributes
                        stat = processor.dumpStatsAsXml(fullStats);
                        sb.append(" exchangesInflight=\"").append(processor.getExchangesInflight()).append("\"");
                        sb.append(" ").append(stat.substring(7)).append("\n");
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

    public String dumpRoutesCoverageAsXml() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("<camelContextRouteCoverage")
                .append(String.format(" id=\"%s\" exchangesTotal=\"%s\" totalProcessingTime=\"%s\"", getCamelId(), getExchangesTotal(), getTotalProcessingTime()))
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

    public boolean createEndpoint(String uri) throws Exception {
        if (context.hasEndpoint(uri) != null) {
            // endpoint already exists
            return false;
        }

        Endpoint endpoint = context.getEndpoint(uri);
        if (endpoint != null) {
            // ensure endpoint is registered, as the management strategy could have been configured to not always
            // register new endpoints in JMX, so we need to check if its registered, and if not register it manually
            ObjectName on = context.getManagementStrategy().getManagementObjectNameStrategy().getObjectNameForEndpoint(endpoint);
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

    public String componentParameterJsonSchema(String componentName) throws Exception {
        return context.adapt(CatalogCamelContext.class).getComponentParameterJsonSchema(componentName);
    }

    public String dataFormatParameterJsonSchema(String dataFormatName) throws Exception {
        return context.adapt(CatalogCamelContext.class).getDataFormatParameterJsonSchema(dataFormatName);
    }

    public String languageParameterJsonSchema(String languageName) throws Exception {
        return context.adapt(CatalogCamelContext.class).getLanguageParameterJsonSchema(languageName);
    }

    public String eipParameterJsonSchema(String eipName) throws Exception {
        return context.adapt(CatalogCamelContext.class).getEipParameterJsonSchema(eipName);
    }

    public void reset(boolean includeRoutes) throws Exception {
        reset();

        // and now reset all routes for this route
        if (includeRoutes) {
            MBeanServer server = getContext().getManagementStrategy().getManagementAgent().getMBeanServer();
            if (server != null) {
                String prefix = getContext().getManagementStrategy().getManagementAgent().getIncludeHostName() ? "*/" : "";
                ObjectName query = ObjectName.getInstance(jmxDomain + ":context=" + prefix + getContext().getManagementName() + ",type=routes,*");
                Set<ObjectName> names = server.queryNames(query, null);
                for (ObjectName name : names) {
                    server.invoke(name, "reset", new Object[]{true}, new String[]{"boolean"});
                }
            }
        }
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

}
