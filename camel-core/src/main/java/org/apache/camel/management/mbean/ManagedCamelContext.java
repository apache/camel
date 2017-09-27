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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

import org.w3c.dom.Document;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.ComponentConfiguration;
import org.apache.camel.Endpoint;
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.Producer;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.TimerListener;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.apache.camel.api.management.mbean.ManagedProcessorMBean;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ModelHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.JsonSchemaHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.XmlLineNumberParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version
 */
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

    @Deprecated
    public Map<String, String> getProperties() {
        return getGlobalOptions();
    }

    @Override
    public Map<String, String> getGlobalOptions() {
        if (context.getGlobalOptions().isEmpty()) {
            return null;
        }
        return context.getGlobalOptions();
    }

    @Deprecated
    public String getProperty(String key) throws Exception {
        return getGlobalOption(key);
    }

    @Override
    public String getGlobalOption(String key) throws Exception {
        return context.getGlobalOption(key);
    }

    @Deprecated
    public void setProperty(String key, String value) throws Exception {
        setGlobalOption(key, value);
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
        context.startAllRoutes();
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
            Document dom = XmlLineNumberParser.parseXml(is, new XmlLineNumberParser.XmlTextTransformer() {
                @Override
                public String transform(String text) {
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
        return dumpRoutesAsXml(false);
    }

    @Override
    public String dumpRoutesAsXml(boolean resolvePlaceholders) throws Exception {
        List<RouteDefinition> routes = context.getRouteDefinitions();
        if (routes.isEmpty()) {
            return null;
        }

        // use a routes definition to dump the routes
        RoutesDefinition def = new RoutesDefinition();
        def.setRoutes(routes);
        String xml = ModelHelper.dumpModelAsXml(context, def);

        // if resolving placeholders we parse the xml, and resolve the property placeholders during parsing
        if (resolvePlaceholders) {
            final AtomicBoolean changed = new AtomicBoolean();
            InputStream is = new ByteArrayInputStream(xml.getBytes("UTF-8"));
            Document dom = XmlLineNumberParser.parseXml(is, new XmlLineNumberParser.XmlTextTransformer() {
                @Override
                public String transform(String text) {
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
                }
            });
            // okay there were some property placeholder replaced so re-create the model
            if (changed.get()) {
                xml = context.getTypeConverter().mandatoryConvertTo(String.class, dom);
                RoutesDefinition copy = ModelHelper.createModelFromXml(context, xml, RoutesDefinition.class);
                xml = ModelHelper.dumpModelAsXml(context, copy);
            }
        }

        return xml;
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

        try {
            // add will remove existing route first
            context.addRouteDefinitions(def.getRoutes());
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

            List<ManagedProcessorMBean> processors = new ArrayList<ManagedProcessorMBean>();
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

    public Map<String, Properties> findEips() throws Exception {
        return context.findEips();
    }

    public List<String> findEipNames() throws Exception {
        Map<String, Properties> map = findEips();
        return new ArrayList<String>(map.keySet());
    }

    public TabularData listEips() throws Exception {
        try {
            // find all EIPs
            Map<String, Properties> eips = context.findEips();

            TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.listEipsTabularType());

            // gather EIP detail for each eip
            for (Map.Entry<String, Properties> entry : eips.entrySet()) {
                String name = entry.getKey();
                String title = (String) entry.getValue().get("title");
                String description = (String) entry.getValue().get("description");
                String label = (String) entry.getValue().get("label");
                String type = (String) entry.getValue().get("class");
                String status = CamelContextHelper.isEipInUse(context, name) ? "in use" : "on classpath";
                CompositeType ct = CamelOpenMBeanTypes.listEipsCompositeType();
                CompositeData data = new CompositeDataSupport(ct, new String[]{"name", "title", "description", "label", "status", "type"},
                        new Object[]{name, title, description, label, status, type});
                answer.put(data);
            }
            return answer;
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
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
        return null;
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

    @Override
    public TabularData listComponents() throws Exception {
        try {
            // find all components
            Map<String, Properties> components = context.findComponents();

            TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.listComponentsTabularType());

            // gather component detail for each component
            for (Map.Entry<String, Properties> entry : components.entrySet()) {
                String name = entry.getKey();
                String title = null;
                String syntax = null;
                String description = null;
                String label = null;
                String deprecated = null;
                String secret = null;
                String status = context.hasComponent(name) != null ? "in use" : "on classpath";
                String type = (String) entry.getValue().get("class");
                String groupId = null;
                String artifactId = null;
                String version = null;

                // a component may have been given a different name, so resolve its default name by its java type
                // as we can find the component json information from the default component name
                String defaultName = context.resolveComponentDefaultName(type);
                String target = defaultName != null ? defaultName : name;

                // load component json data, and parse it to gather the component meta-data
                String json = context.getComponentParameterJsonSchema(target);
                List<Map<String, String>> rows = JsonSchemaHelper.parseJsonSchema("component", json, false);
                for (Map<String, String> row : rows) {
                    if (row.containsKey("title")) {
                        title = row.get("title");
                    } else if (row.containsKey("syntax")) {
                        syntax = row.get("syntax");
                    } else if (row.containsKey("description")) {
                        description = row.get("description");
                    } else if (row.containsKey("label")) {
                        label = row.get("label");
                    } else if (row.containsKey("deprecated")) {
                        deprecated = row.get("deprecated");
                    } else if (row.containsKey("secret")) {
                        secret = row.get("secret");
                    } else if (row.containsKey("javaType")) {
                        type = row.get("javaType");
                    } else if (row.containsKey("groupId")) {
                        groupId = row.get("groupId");
                    } else if (row.containsKey("artifactId")) {
                        artifactId = row.get("artifactId");
                    } else if (row.containsKey("version")) {
                        version = row.get("version");
                    }
                }

                CompositeType ct = CamelOpenMBeanTypes.listComponentsCompositeType();
                CompositeData data = new CompositeDataSupport(ct,
                        new String[]{"name", "title", "syntax", "description", "label", "deprecated", "secret", "status", "type", "groupId", "artifactId", "version"},
                        new Object[]{name, title, syntax, description, label, deprecated, secret, status, type, groupId, artifactId, version});
                answer.put(data);
            }
            return answer;
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
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
        // favor using pre generated schema if component has that
        String json = context.getComponentParameterJsonSchema(componentName);
        if (json == null) {
            // okay this requires having the component on the classpath and being instantiated
            Component component = context.getComponent(componentName);
            if (component != null) {
                ComponentConfiguration configuration = component.createComponentConfiguration();
                json = configuration.createParameterJsonSchema();
            }
        }
        return json;
    }

    public String dataFormatParameterJsonSchema(String dataFormatName) throws Exception {
        return context.getDataFormatParameterJsonSchema(dataFormatName);
    }

    public String languageParameterJsonSchema(String languageName) throws Exception {
        return context.getLanguageParameterJsonSchema(languageName);
    }

    public String eipParameterJsonSchema(String eipName) throws Exception {
        return context.getEipParameterJsonSchema(eipName);
    }

    public String explainEipJson(String nameOrId, boolean includeAllOptions) {
        return context.explainEipJson(nameOrId, includeAllOptions);
    }

    public String explainComponentJson(String componentName, boolean includeAllOptions) throws Exception {
        return context.explainComponentJson(componentName, includeAllOptions);
    }

    public String explainEndpointJson(String uri, boolean includeAllOptions) throws Exception {
        return context.explainEndpointJson(uri, includeAllOptions);
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
