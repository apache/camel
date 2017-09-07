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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.management.AttributeValueExp;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.Query;
import javax.management.QueryExp;
import javax.management.StringValueExp;

import org.w3c.dom.Document;

import org.apache.camel.CamelContext;
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.TimerListener;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedProcessorMBean;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ModelHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.RouteError;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.XmlLineNumberParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "Managed Route")
public class ManagedRoute extends ManagedPerformanceCounter implements TimerListener, ManagedRouteMBean {

    public static final String VALUE_UNKNOWN = "Unknown";

    private static final Logger LOG = LoggerFactory.getLogger(ManagedRoute.class);

    protected final Route route;
    protected final String description;
    protected final ModelCamelContext context;
    private final LoadTriplet load = new LoadTriplet();
    private final String jmxDomain;

    public ManagedRoute(ModelCamelContext context, Route route) {
        this.route = route;
        this.context = context;
        this.description = route.getDescription();
        this.jmxDomain = context.getManagementStrategy().getManagementAgent().getMBeanObjectDomainName();
    }

    @Override
    public void init(ManagementStrategy strategy) {
        super.init(strategy);
        boolean enabled = context.getManagementStrategy().getManagementAgent().getStatisticsLevel() != ManagementStatisticsLevel.Off;
        setStatisticsEnabled(enabled);
    }

    public Route getRoute() {
        return route;
    }

    public CamelContext getContext() {
        return context;
    }

    public String getRouteId() {
        String id = route.getId();
        if (id == null) {
            id = VALUE_UNKNOWN;
        }
        return id;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String getEndpointUri() {
        if (route.getEndpoint() != null) {
            return route.getEndpoint().getEndpointUri();
        }
        return VALUE_UNKNOWN;
    }

    public String getState() {
        // must use String type to be sure remote JMX can read the attribute without requiring Camel classes.
        ServiceStatus status = context.getRouteStatus(route.getId());
        // if no status exists then its stopped
        if (status == null) {
            status = ServiceStatus.Stopped;
        }
        return status.name();
    }

    public String getUptime() {
        return route.getUptime();
    }

    public long getUptimeMillis() {
        return route.getUptimeMillis();
    }

    public Integer getInflightExchanges() {
        return (int) super.getExchangesInflight();
    }

    public String getCamelId() {
        return context.getName();
    }

    public String getCamelManagementName() {
        return context.getManagementName();
    }

    public Boolean getTracing() {
        return route.getRouteContext().isTracing();
    }

    public void setTracing(Boolean tracing) {
        route.getRouteContext().setTracing(tracing);
    }

    public Boolean getMessageHistory() {
        return route.getRouteContext().isMessageHistory();
    }

    public Boolean getLogMask() {
        return route.getRouteContext().isLogMask();
    }

    public String getRoutePolicyList() {
        List<RoutePolicy> policyList = route.getRouteContext().getRoutePolicyList();

        if (policyList == null || policyList.isEmpty()) {
            // return an empty string to have it displayed nicely in JMX consoles
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < policyList.size(); i++) {
            RoutePolicy policy = policyList.get(i);
            sb.append(policy.getClass().getSimpleName());
            sb.append("(").append(ObjectHelper.getIdentityHashCode(policy)).append(")");
            if (i < policyList.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
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

    @Override
    public void onTimer() {
        load.update(getInflightExchanges());
    }

    public void start() throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        context.getRouteController().startRoute(getRouteId());
    }

    public void stop() throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        context.getRouteController().stopRoute(getRouteId());
    }

    public void stop(long timeout) throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        context.getRouteController().stopRoute(getRouteId(), timeout, TimeUnit.SECONDS);
    }

    public boolean stop(Long timeout, Boolean abortAfterTimeout) throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        return context.getRouteController().stopRoute(getRouteId(), timeout, TimeUnit.SECONDS, abortAfterTimeout);
    }

    public void shutdown() throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        String routeId = getRouteId();
        context.stopRoute(routeId);
        context.removeRoute(routeId);
    }

    public void shutdown(long timeout) throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        String routeId = getRouteId();
        context.stopRoute(routeId, timeout, TimeUnit.SECONDS);
        context.removeRoute(routeId);
    }

    public boolean remove() throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        return context.removeRoute(getRouteId());
    }

    public String dumpRouteAsXml() throws Exception {
        return dumpRouteAsXml(false);
    }

    @Override
    public String dumpRouteAsXml(boolean resolvePlaceholders) throws Exception {
        String id = route.getId();
        RouteDefinition def = context.getRouteDefinition(id);
        if (def != null) {
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
                    RouteDefinition copy = ModelHelper.createModelFromXml(context, xml, RouteDefinition.class);
                    xml = ModelHelper.dumpModelAsXml(context, copy);
                }
            }
            return xml;
        }
        return null;
    }

    public void updateRouteFromXml(String xml) throws Exception {
        // convert to model from xml
        RouteDefinition def = ModelHelper.createModelFromXml(context, xml, RouteDefinition.class);
        if (def == null) {
            return;
        }

        // if the xml does not contain the route-id then we fix this by adding the actual route id
        // this may be needed if the route-id was auto-generated, as the intend is to update this route
        // and not add a new route, adding a new route, use the MBean operation on ManagedCamelContext instead.
        if (ObjectHelper.isEmpty(def.getId())) {
            def.setId(getRouteId());
        } else if (!def.getId().equals(getRouteId())) {
            throw new IllegalArgumentException("Cannot update route from XML as routeIds does not match. routeId: "
                    + getRouteId() + ", routeId from XML: " + def.getId());
        }

        LOG.debug("Updating route: {} from xml: {}", def.getId(), xml);

        try {
            // add will remove existing route first
            context.addRouteDefinition(def);
        } catch (Exception e) {
            // log the error as warn as the management api may be invoked remotely over JMX which does not propagate such exception
            String msg = "Error updating route: " + def.getId() + " from xml: " + xml + " due: " + e.getMessage();
            LOG.warn(msg, e);
            throw e;
        }
    }

    public String dumpRouteStatsAsXml(boolean fullStats, boolean includeProcessors) throws Exception {
        // in this logic we need to calculate the accumulated processing time for the processor in the route
        // and hence why the logic is a bit more complicated to do this, as we need to calculate that from
        // the bottom -> top of the route but this information is valuable for profiling routes
        StringBuilder sb = new StringBuilder();

        // need to calculate this value first, as we need that value for the route stat
        Long processorAccumulatedTime = 0L;

        // gather all the processors for this route, which requires JMX
        if (includeProcessors) {
            sb.append("  <processorStats>\n");
            MBeanServer server = getContext().getManagementStrategy().getManagementAgent().getMBeanServer();
            if (server != null) {
                // get all the processor mbeans and sort them accordingly to their index
                String prefix = getContext().getManagementStrategy().getManagementAgent().getIncludeHostName() ? "*/" : "";
                ObjectName query = ObjectName.getInstance(jmxDomain + ":context=" + prefix + getContext().getManagementName() + ",type=processors,*");
                Set<ObjectName> names = server.queryNames(query, null);
                List<ManagedProcessorMBean> mps = new ArrayList<ManagedProcessorMBean>();
                for (ObjectName on : names) {
                    ManagedProcessorMBean processor = context.getManagementStrategy().getManagementAgent().newProxyClient(on, ManagedProcessorMBean.class);

                    // the processor must belong to this route
                    if (getRouteId().equals(processor.getRouteId())) {
                        mps.add(processor);
                    }
                }
                mps.sort(new OrderProcessorMBeans());

                // walk the processors in reverse order, and calculate the accumulated total time
                Map<String, Long> accumulatedTimes = new HashMap<String, Long>();
                Collections.reverse(mps);
                for (ManagedProcessorMBean processor : mps) {
                    processorAccumulatedTime += processor.getTotalProcessingTime();
                    accumulatedTimes.put(processor.getProcessorId(), processorAccumulatedTime);
                }
                // and reverse back again
                Collections.reverse(mps);

                // and now add the sorted list of processors to the xml output
                for (ManagedProcessorMBean processor : mps) {
                    sb.append("    <processorStat").append(String.format(" id=\"%s\" index=\"%s\" state=\"%s\"", processor.getProcessorId(), processor.getIndex(), processor.getState()));
                    // do we have an accumulated time then append that
                    Long accTime = accumulatedTimes.get(processor.getProcessorId());
                    if (accTime != null) {
                        sb.append(" accumulatedProcessingTime=\"").append(accTime).append("\"");
                    }
                    // use substring as we only want the attributes
                    sb.append(" ").append(processor.dumpStatsAsXml(fullStats).substring(7)).append("\n");
                }
            }
            sb.append("  </processorStats>\n");
        }

        // route self time is route total - processor accumulated total)
        long routeSelfTime = getTotalProcessingTime() - processorAccumulatedTime;
        if (routeSelfTime < 0) {
            // ensure we don't calculate that as negative
            routeSelfTime = 0;
        }

        StringBuilder answer = new StringBuilder();
        answer.append("<routeStat").append(String.format(" id=\"%s\"", route.getId())).append(String.format(" state=\"%s\"", getState()));
        // use substring as we only want the attributes
        String stat = dumpStatsAsXml(fullStats);
        answer.append(" exchangesInflight=\"").append(getInflightExchanges()).append("\"");
        answer.append(" selfProcessingTime=\"").append(routeSelfTime).append("\"");
        InflightRepository.InflightExchange oldest = getOldestInflightEntry();
        if (oldest == null) {
            answer.append(" oldestInflightExchangeId=\"\"");
            answer.append(" oldestInflightDuration=\"\"");
        } else {
            answer.append(" oldestInflightExchangeId=\"").append(oldest.getExchange().getExchangeId()).append("\"");
            answer.append(" oldestInflightDuration=\"").append(oldest.getDuration()).append("\"");
        }
        answer.append(" ").append(stat.substring(7, stat.length() - 2)).append(">\n");

        if (includeProcessors) {
            answer.append(sb);
        }

        answer.append("</routeStat>");
        return answer.toString();
    }

    public void reset(boolean includeProcessors) throws Exception {
        reset();

        // and now reset all processors for this route
        if (includeProcessors) {
            MBeanServer server = getContext().getManagementStrategy().getManagementAgent().getMBeanServer();
            if (server != null) {
                // get all the processor mbeans and sort them accordingly to their index
                String prefix = getContext().getManagementStrategy().getManagementAgent().getIncludeHostName() ? "*/" : "";
                ObjectName query = ObjectName.getInstance(jmxDomain + ":context=" + prefix + getContext().getManagementName() + ",type=processors,*");
                QueryExp queryExp = Query.match(new AttributeValueExp("RouteId"), new StringValueExp(getRouteId()));
                Set<ObjectName> names = server.queryNames(query, queryExp);
                for (ObjectName name : names) {
                    server.invoke(name, "reset", null, null);
                }
            }
        }
    }

    public String createRouteStaticEndpointJson() {
        return getContext().createRouteStaticEndpointJson(getRouteId());
    }

    @Override
    public String createRouteStaticEndpointJson(boolean includeDynamic) {
        return getContext().createRouteStaticEndpointJson(getRouteId(), includeDynamic);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o != null && getClass() == o.getClass() && route.equals(((ManagedRoute) o).route));
    }

    @Override
    public int hashCode() {
        return route.hashCode();
    }

    private InflightRepository.InflightExchange getOldestInflightEntry() {
        return getContext().getInflightRepository().oldest(getRouteId());
    }

    public Long getOldestInflightDuration() {
        InflightRepository.InflightExchange oldest = getOldestInflightEntry();
        if (oldest == null) {
            return null;
        } else {
            return oldest.getDuration();
        }
    }

    public String getOldestInflightExchangeId() {
        InflightRepository.InflightExchange oldest = getOldestInflightEntry();
        if (oldest == null) {
            return null;
        } else {
            return oldest.getExchange().getExchangeId();
        }
    }

    @Override
    public Boolean getHasRouteController() {
        return route.getRouteContext().getRouteController() != null;
    }

    @Override
    public RouteError getLastError() {
        return route.getRouteContext().getLastError();
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
