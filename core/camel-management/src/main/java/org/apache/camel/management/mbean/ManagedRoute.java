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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.management.AttributeValueExp;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.Query;
import javax.management.QueryExp;
import javax.management.StringValueExp;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ServiceStatus;
import org.apache.camel.TimerListener;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.api.management.mbean.ManagedProcessorMBean;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.api.management.mbean.ManagedStepMBean;
import org.apache.camel.api.management.mbean.RouteError;
import org.apache.camel.model.Model;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "Managed Route")
public class ManagedRoute extends ManagedPerformanceCounter implements TimerListener, ManagedRouteMBean {

    public static final String VALUE_UNKNOWN = "Unknown";

    private static final Logger LOG = LoggerFactory.getLogger(ManagedRoute.class);

    protected final Route route;
    protected final String description;
    protected final CamelContext context;
    private final LoadTriplet load = new LoadTriplet();
    private final String jmxDomain;

    public ManagedRoute(CamelContext context, Route route) {
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

    @Override
    public String getRouteId() {
        String id = route.getId();
        if (id == null) {
            id = VALUE_UNKNOWN;
        }
        return id;
    }

    @Override
    public String getRouteGroup() {
        return route.getGroup();
    }

    @Override
    public TabularData getRouteProperties() {
        try {
            final Map<String, Object> properties = route.getProperties();
            final TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.camelRoutePropertiesTabularType());
            final CompositeType ct = CamelOpenMBeanTypes.camelRoutePropertiesCompositeType();

            // gather route properties
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                final String key = entry.getKey();
                final String val = context.getTypeConverter().convertTo(String.class, entry.getValue());

                CompositeData data = new CompositeDataSupport(
                        ct,
                        new String[]{"key", "value"},
                        new Object[]{key, val}
                );

                answer.put(data);
            }
            return answer;
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    @Override
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

    @Override
    public String getState() {
        // must use String type to be sure remote JMX can read the attribute without requiring Camel classes.
        ServiceStatus status = context.getRouteController().getRouteStatus(route.getId());
        // if no status exists then its stopped
        if (status == null) {
            status = ServiceStatus.Stopped;
        }
        return status.name();
    }

    @Override
    public String getUptime() {
        return route.getUptime();
    }

    @Override
    public long getUptimeMillis() {
        return route.getUptimeMillis();
    }

    public Integer getInflightExchanges() {
        return (int) super.getExchangesInflight();
    }

    @Override
    public String getCamelId() {
        return context.getName();
    }

    @Override
    public String getCamelManagementName() {
        return context.getManagementName();
    }

    @Override
    public Boolean getTracing() {
        return route.isTracing();
    }

    @Override
    public void setTracing(Boolean tracing) {
        route.setTracing(tracing);
    }

    @Override
    public Boolean getMessageHistory() {
        return route.isMessageHistory();
    }

    @Override
    public Boolean getLogMask() {
        return route.isLogMask();
    }

    @Override
    public String getRoutePolicyList() {
        List<RoutePolicy> policyList = route.getRoutePolicyList();

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
    public void onTimer() {
        load.update(getInflightExchanges());
    }

    @Override
    public void start() throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        context.getRouteController().startRoute(getRouteId());
    }

    @Override
    public void stop() throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        context.getRouteController().stopRoute(getRouteId());
    }

    @Override
    public void stop(long timeout) throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        context.getRouteController().stopRoute(getRouteId(), timeout, TimeUnit.SECONDS);
    }

    @Override
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
        context.getRouteController().stopRoute(routeId);
        context.removeRoute(routeId);
    }

    public void shutdown(long timeout) throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        String routeId = getRouteId();
        context.getRouteController().stopRoute(routeId, timeout, TimeUnit.SECONDS);
        context.removeRoute(routeId);
    }

    @Override
    public boolean remove() throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        return context.removeRoute(getRouteId());
    }

    @Override
    public void restart() throws Exception {
        restart(1);
    }

    @Override
    public void restart(long delay) throws Exception {
        stop();
        if (delay > 0) {
            try {
                LOG.debug("Sleeping {} seconds before starting route: {}", delay, getRouteId());
                Thread.sleep(delay * 1000);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        start();
    }

    @Override
    public String dumpRouteAsXml() throws Exception {
        return dumpRouteAsXml(false, false);
    }

    @Override
    public String dumpRouteAsXml(boolean resolvePlaceholders) throws Exception {
        return dumpRouteAsXml(resolvePlaceholders, false);
    }

    @Override
    public String dumpRouteAsXml(boolean resolvePlaceholders, boolean resolveDelegateEndpoints) throws Exception {
        String id = route.getId();
        RouteDefinition def = context.getExtension(Model.class).getRouteDefinition(id);
        if (def != null) {
            ExtendedCamelContext ecc = context.adapt(ExtendedCamelContext.class);
            return ecc.getModelToXMLDumper().dumpModelAsXml(context, def, resolvePlaceholders, resolveDelegateEndpoints);
        }

        return null;
    }

    @Override
    public void updateRouteFromXml(String xml) throws Exception {
        // convert to model from xml
        ExtendedCamelContext ecc = context.adapt(ExtendedCamelContext.class);
        InputStream is = context.getTypeConverter().convertTo(InputStream.class, xml);
        RoutesDefinition routes = (RoutesDefinition) ecc.getXMLRoutesDefinitionLoader().loadRoutesDefinition(context, is);
        if (routes == null || routes.getRoutes().isEmpty()) {
            return;
        }
        RouteDefinition def = routes.getRoutes().get(0);

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
            context.getExtension(Model.class).addRouteDefinition(def);
        } catch (Exception e) {
            // log the error as warn as the management api may be invoked remotely over JMX which does not propagate such exception
            String msg = "Error updating route: " + def.getId() + " from xml: " + xml + " due: " + e.getMessage();
            LOG.warn(msg, e);
            throw e;
        }
    }

    @Override
    public String dumpRouteStatsAsXml(boolean fullStats, boolean includeProcessors) throws Exception {
        // in this logic we need to calculate the accumulated processing time for the processor in the route
        // and hence why the logic is a bit more complicated to do this, as we need to calculate that from
        // the bottom -> top of the route but this information is valuable for profiling routes
        StringBuilder sb = new StringBuilder();

        // need to calculate this value first, as we need that value for the route stat
        long processorAccumulatedTime = 0L;

        // gather all the processors for this route, which requires JMX
        if (includeProcessors) {
            sb.append("  <processorStats>\n");
            MBeanServer server = getContext().getManagementStrategy().getManagementAgent().getMBeanServer();
            if (server != null) {
                // get all the processor mbeans and sort them accordingly to their index
                String prefix = getContext().getManagementStrategy().getManagementAgent().getIncludeHostName() ? "*/" : "";
                ObjectName query = ObjectName.getInstance(jmxDomain + ":context=" + prefix + getContext().getManagementName() + ",type=processors,*");
                Set<ObjectName> names = server.queryNames(query, null);
                List<ManagedProcessorMBean> mps = new ArrayList<>();
                for (ObjectName on : names) {
                    ManagedProcessorMBean processor = context.getManagementStrategy().getManagementAgent().newProxyClient(on, ManagedProcessorMBean.class);

                    // the processor must belong to this route
                    if (getRouteId().equals(processor.getRouteId())) {
                        mps.add(processor);
                    }
                }
                mps.sort(new OrderProcessorMBeans());

                // walk the processors in reverse order, and calculate the accumulated total time
                Map<String, Long> accumulatedTimes = new HashMap<>();
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
        answer.append(" ").append(stat, 7, stat.length() - 2).append(">\n");

        if (includeProcessors) {
            answer.append(sb);
        }

        answer.append("</routeStat>");
        return answer.toString();
    }

    @Override
    public String dumpStepStatsAsXml(boolean fullStats) throws Exception {
        // in this logic we need to calculate the accumulated processing time for the processor in the route
        // and hence why the logic is a bit more complicated to do this, as we need to calculate that from
        // the bottom -> top of the route but this information is valuable for profiling routes
        StringBuilder sb = new StringBuilder();

        // gather all the steps for this route, which requires JMX
        sb.append("  <stepStats>\n");
        MBeanServer server = getContext().getManagementStrategy().getManagementAgent().getMBeanServer();
        if (server != null) {
            // get all the processor mbeans and sort them accordingly to their index
            String prefix = getContext().getManagementStrategy().getManagementAgent().getIncludeHostName() ? "*/" : "";
            ObjectName query = ObjectName.getInstance(jmxDomain + ":context=" + prefix + getContext().getManagementName() + ",type=steps,*");
            Set<ObjectName> names = server.queryNames(query, null);
            List<ManagedStepMBean> mps = new ArrayList<>();
            for (ObjectName on : names) {
                ManagedStepMBean step = context.getManagementStrategy().getManagementAgent().newProxyClient(on, ManagedStepMBean.class);

                // the step must belong to this route
                if (getRouteId().equals(step.getRouteId())) {
                    mps.add(step);
                }
            }
            mps.sort(new OrderProcessorMBeans());

            // and now add the sorted list of steps to the xml output
            for (ManagedStepMBean step : mps) {
                sb.append("    <stepStat").append(String.format(" id=\"%s\" index=\"%s\" state=\"%s\"", step.getProcessorId(), step.getIndex(), step.getState()));
                // use substring as we only want the attributes
                sb.append(" ").append(step.dumpStatsAsXml(fullStats).substring(7)).append("\n");
            }
        }
        sb.append("  </stepStats>\n");

        StringBuilder answer = new StringBuilder();
        answer.append("<routeStat").append(String.format(" id=\"%s\"", route.getId())).append(String.format(" state=\"%s\"", getState()));
        // use substring as we only want the attributes
        String stat = dumpStatsAsXml(fullStats);
        answer.append(" exchangesInflight=\"").append(getInflightExchanges()).append("\"");
        InflightRepository.InflightExchange oldest = getOldestInflightEntry();
        if (oldest == null) {
            answer.append(" oldestInflightExchangeId=\"\"");
            answer.append(" oldestInflightDuration=\"\"");
        } else {
            answer.append(" oldestInflightExchangeId=\"").append(oldest.getExchange().getExchangeId()).append("\"");
            answer.append(" oldestInflightDuration=\"").append(oldest.getDuration()).append("\"");
        }
        answer.append(" ").append(stat, 7, stat.length() - 2).append(">\n");

        answer.append(sb);

        answer.append("</routeStat>");
        return answer.toString();
    }

    @Override
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

    @Override
    public Long getOldestInflightDuration() {
        InflightRepository.InflightExchange oldest = getOldestInflightEntry();
        if (oldest == null) {
            return null;
        } else {
            return oldest.getDuration();
        }
    }

    @Override
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
        return route.getRouteController() != null;
    }

    @Override
    public RouteError getLastError() {
        org.apache.camel.spi.RouteError error = route.getLastError();
        if (error == null) {
            return null;
        } else {
            return new RouteError() {
                @Override
                public Phase getPhase() {
                    if (error.getPhase() != null) {
                        switch (error.getPhase()) {
                            case START: return Phase.START;
                            case STOP: return Phase.STOP;
                            case SUSPEND: return Phase.SUSPEND;
                            case RESUME: return Phase.RESUME;
                            case SHUTDOWN: return Phase.SHUTDOWN;
                            case REMOVE: return Phase.REMOVE;
                            default: throw new IllegalStateException();
                        }
                    }
                    return null;
                }

                @Override
                public Throwable getException() {
                    return error.getException();
                }
            };
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
