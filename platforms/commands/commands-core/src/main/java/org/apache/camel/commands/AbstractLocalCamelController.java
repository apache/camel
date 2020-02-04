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
package org.apache.camel.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.StatefulService;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.model.Model;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.EndpointRegistry;
import org.apache.camel.spi.ManagementAgent;
import org.apache.camel.spi.RestRegistry;
import org.apache.camel.spi.RuntimeEndpointRegistry;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.Validator;

/**
 * Abstract {@link org.apache.camel.commands.LocalCamelController} that implementators should extend when implementing
 * a controller that runs locally in the same JVM as Camel.
 */
public abstract class AbstractLocalCamelController extends AbstractCamelController implements LocalCamelController {

    @Override
    public CamelContext getLocalCamelContext(String name) throws Exception {
        for (CamelContext camelContext : this.getLocalCamelContexts()) {
            if (camelContext.getName().equals(name)) {
                return camelContext;
            }
        }
        return null;
    }

    @Override
    public Map<String, Object> getCamelContextInformation(String name) throws Exception {
        Map<String, Object> answer = new LinkedHashMap<>();
        CamelContext context = getLocalCamelContext(name);
        if (context != null) {
            answer.put("name", context.getName());
            answer.put("managementName", context.getManagementName());
            answer.put("version", context.getVersion());
            answer.put("status", context.getStatus().name());
            answer.put("uptime", context.getUptime());
            answer.put("suspended", context.getStatus().isSuspended());
            if (context.getManagementStrategy().getManagementAgent() != null) {
                String level = context.getManagementStrategy().getManagementAgent().getStatisticsLevel().name();
                answer.put("managementStatisticsLevel", level);
            }
            answer.put("allowUseOriginalMessage", context.isAllowUseOriginalMessage());
            answer.put("messageHistory", context.isMessageHistory());
            answer.put("tracing", context.isTracing());
            answer.put("logMask", context.isLogMask());
            answer.put("shutdownTimeout", context.getShutdownStrategy().getTimeUnit().toSeconds(context.getShutdownStrategy().getTimeout()));
            answer.put("classResolver", context.getClassResolver().toString());
            answer.put("packageScanClassResolver", context.adapt(ExtendedCamelContext.class).getPackageScanClassResolver().toString());
            answer.put("applicationContextClassLoader", context.getApplicationContextClassLoader().toString());
            answer.put("headersMapFactory", context.adapt(ExtendedCamelContext.class).getHeadersMapFactory().toString());

            for (Map.Entry<String, String> entry : context.getGlobalOptions().entrySet()) {
                answer.put("property." + entry.getKey(), entry.getValue());
            }

            long activeRoutes = 0;
            long inactiveRoutes = 0;
            List<Route> routeList = context.getRoutes();
            for (Route route : routeList) {
                if (context.getRouteController().getRouteStatus(route.getId()).isStarted()) {
                    activeRoutes++;
                } else {
                    inactiveRoutes++;
                }
            }
            answer.put("startedRoutes", activeRoutes);
            answer.put("totalRoutes", activeRoutes + inactiveRoutes);

            // add type converter details
            answer.put("typeConverter.numberOfTypeConverters", context.getTypeConverterRegistry().size());
            answer.put("typeConverter.statisticsEnabled", context.getTypeConverterRegistry().getStatistics().isStatisticsEnabled());
            answer.put("typeConverter.noopCounter", context.getTypeConverterRegistry().getStatistics().getNoopCounter());
            answer.put("typeConverter.attemptCounter", context.getTypeConverterRegistry().getStatistics().getAttemptCounter());
            answer.put("typeConverter.hitCounter", context.getTypeConverterRegistry().getStatistics().getHitCounter());
            answer.put("typeConverter.missCounter", context.getTypeConverterRegistry().getStatistics().getMissCounter());
            answer.put("typeConverter.failedCounter", context.getTypeConverterRegistry().getStatistics().getFailedCounter());

            // add async processor await manager details
            ExtendedCamelContext ecc = context.adapt(ExtendedCamelContext.class);
            answer.put("asyncProcessorAwaitManager.size", ecc.getAsyncProcessorAwaitManager().size());
            answer.put("asyncProcessorAwaitManager.statisticsEnabled", ecc.getAsyncProcessorAwaitManager().getStatistics().isStatisticsEnabled());
            answer.put("asyncProcessorAwaitManager.threadsBlocked", ecc.getAsyncProcessorAwaitManager().getStatistics().getThreadsBlocked());
            answer.put("asyncProcessorAwaitManager.threadsInterrupted", ecc.getAsyncProcessorAwaitManager().getStatistics().getThreadsInterrupted());
            answer.put("asyncProcessorAwaitManager.totalDuration", ecc.getAsyncProcessorAwaitManager().getStatistics().getTotalDuration());
            answer.put("asyncProcessorAwaitManager.minDuration", ecc.getAsyncProcessorAwaitManager().getStatistics().getMinDuration());
            answer.put("asyncProcessorAwaitManager.maxDuration", ecc.getAsyncProcessorAwaitManager().getStatistics().getMaxDuration());
            answer.put("asyncProcessorAwaitManager.meanDuration", ecc.getAsyncProcessorAwaitManager().getStatistics().getMeanDuration());

            // add stream caching details if enabled
            if (context.getStreamCachingStrategy().isEnabled()) {
                answer.put("streamCachingEnabled", true);
                answer.put("streamCaching.spoolDirectory", context.getStreamCachingStrategy().getSpoolDirectory());
                answer.put("streamCaching.spoolCipher", context.getStreamCachingStrategy().getSpoolCipher());
                answer.put("streamCaching.spoolThreshold", context.getStreamCachingStrategy().getSpoolThreshold());
                answer.put("streamCaching.spoolUsedHeapMemoryThreshold", context.getStreamCachingStrategy().getSpoolUsedHeapMemoryThreshold());
                answer.put("streamCaching.spoolUsedHeapMemoryLimit", context.getStreamCachingStrategy().getSpoolUsedHeapMemoryLimit());
                answer.put("streamCaching.anySpoolRules", context.getStreamCachingStrategy().isAnySpoolRules());
                answer.put("streamCaching.bufferSize", context.getStreamCachingStrategy().getBufferSize());
                answer.put("streamCaching.removeSpoolDirectoryWhenStopping", context.getStreamCachingStrategy().isRemoveSpoolDirectoryWhenStopping());
                answer.put("streamCaching.statisticsEnabled", context.getStreamCachingStrategy().getStatistics().isStatisticsEnabled());

                if (context.getStreamCachingStrategy().getStatistics().isStatisticsEnabled()) {
                    answer.put("streamCaching.cacheMemoryCounter", context.getStreamCachingStrategy().getStatistics().getCacheMemoryCounter());
                    answer.put("streamCaching.cacheMemorySize", context.getStreamCachingStrategy().getStatistics().getCacheMemorySize());
                    answer.put("streamCaching.cacheMemoryAverageSize", context.getStreamCachingStrategy().getStatistics().getCacheMemoryAverageSize());
                    answer.put("streamCaching.cacheSpoolCounter", context.getStreamCachingStrategy().getStatistics().getCacheSpoolCounter());
                    answer.put("streamCaching.cacheSpoolSize", context.getStreamCachingStrategy().getStatistics().getCacheSpoolSize());
                    answer.put("streamCaching.cacheSpoolAverageSize", context.getStreamCachingStrategy().getStatistics().getCacheSpoolAverageSize());
                }
            } else {
                answer.put("streamCachingEnabled", false);
            }
        }

        return answer;
    }

    @Override
    public String getCamelContextStatsAsXml(String camelContextName, boolean fullStats, boolean includeProcessors) throws Exception {
        CamelContext context = this.getLocalCamelContext(camelContextName);
        if (context == null) {
            return null;
        }

        ManagementAgent agent = context.getManagementStrategy().getManagementAgent();
        if (agent != null) {
            MBeanServer mBeanServer = agent.getMBeanServer();
            ObjectName query = ObjectName.getInstance(agent.getMBeanObjectDomainName() + ":type=context,*");
            Set<ObjectName> set = mBeanServer.queryNames(query, null);
            for (ObjectName contextMBean : set) {
                String camelId = (String) mBeanServer.getAttribute(contextMBean, "CamelId");
                if (camelId != null && camelId.equals(context.getName())) {
                    String xml = (String) mBeanServer.invoke(contextMBean, "dumpRoutesStatsAsXml", new Object[]{fullStats, includeProcessors}, new String[]{"boolean", "boolean"});
                    return xml;
                }
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> browseInflightExchanges(String camelContextName, String route, int limit, boolean sortByLongestDuration) throws Exception {
        CamelContext context = this.getLocalCamelContext(camelContextName);
        if (context == null) {
            return null;
        }

        List<Map<String, Object>> answer = new ArrayList<>();

        ManagementAgent agent = context.getManagementStrategy().getManagementAgent();
        if (agent != null) {
            MBeanServer mBeanServer = agent.getMBeanServer();
            ObjectName on = new ObjectName(agent.getMBeanObjectDomainName() + ":type=services,name=DefaultInflightRepository,context=" + context.getManagementName());
            if (mBeanServer.isRegistered(on)) {
                TabularData list = (TabularData) mBeanServer.invoke(on, "browse", new Object[]{route, limit, sortByLongestDuration}, new String[]{"java.lang.String", "int", "boolean"});
                Collection<CompositeData> values = (Collection<CompositeData>) list.values();
                for (CompositeData data : values) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    Object exchangeId = data.get("exchangeId");
                    if (exchangeId != null) {
                        row.put("exchangeId", exchangeId);
                    }
                    Object fromRouteId = data.get("fromRouteId");
                    if (fromRouteId != null) {
                        row.put("fromRouteId", fromRouteId);
                    }
                    Object routeId = data.get("routeId");
                    if (routeId != null) {
                        row.put("routeId", routeId);
                    }
                    Object nodeId = data.get("nodeId");
                    if (nodeId != null) {
                        row.put("nodeId", nodeId);
                    }
                    Object elapsed = data.get("elapsed");
                    if (elapsed != null) {
                        row.put("elapsed", elapsed);
                    }
                    Object duration = data.get("duration");
                    if (duration != null) {
                        row.put("duration", duration);
                    }
                    answer.add(row);
                }
            }
        }

        return answer;
    }

    @Override
    public void startContext(String camelContextName) throws Exception {
        CamelContext context = getLocalCamelContext(camelContextName);
        if (context != null) {
            if (context.getStatus().equals(ServiceStatus.Suspended)) {
                context.resume();
            } else {
                context.start();
            }
        }
    }

    @Override
    public void stopContext(String camelContextName) throws Exception {
        CamelContext context = getLocalCamelContext(camelContextName);
        if (context != null) {
            context.stop();
        }
    }

    @Override
    public void suspendContext(String camelContextName) throws Exception {
        CamelContext context = getLocalCamelContext(camelContextName);
        if (context != null) {
            context.suspend();
        }
    }

    @Override
    public void resumeContext(String camelContextName) throws Exception {
        CamelContext context = getLocalCamelContext(camelContextName);
        if (context != null) {
            context.resume();
        }
    }

    @Override
    public List<Map<String, String>> getRoutes(String camelContextName) throws Exception {
        return getRoutes(camelContextName, null);
    }

    @Override
    public List<Map<String, String>> getRoutes(String camelContextName, String filter) throws Exception {
        List<Map<String, String>> answer = new ArrayList<>();

        if (camelContextName != null) {
            CamelContext context = this.getLocalCamelContext(camelContextName);
            if (context != null) {
                for (Route route : context.getRoutes()) {
                    if (filter == null || route.getId().matches(filter)) {
                        Map<String, String> row = new LinkedHashMap<>();
                        row.put("camelContextName", context.getName());
                        row.put("routeId", route.getId());
                        row.put("state", getRouteState(route));
                        row.put("uptime", route.getUptime());
                        ManagedCamelContext mcc = context.getExtension(ManagedCamelContext.class);
                        if (mcc != null && mcc.getManagedCamelContext() != null) {
                            ManagedRouteMBean mr = mcc.getManagedRoute(route.getId());
                            row.put("exchangesTotal", "" + mr.getExchangesTotal());
                            row.put("exchangesInflight", "" + mr.getExchangesInflight());
                            row.put("exchangesFailed", "" + mr.getExchangesFailed());
                        } else {
                            row.put("exchangesTotal", "0");
                            row.put("exchangesInflight", "0");
                            row.put("exchangesFailed", "0");
                        }
                        answer.add(row);
                    }
                }
            }
        } else {
            List<Map<String, String>> camelContexts = this.getCamelContexts();
            for (Map<String, String> row : camelContexts) {
                List<Map<String, String>> routes = getRoutes(row.get("name"), filter);
                answer.addAll(routes);
            }
        }

        // sort the list
        Collections.sort(answer, new Comparator<Map<String, String>>() {
            @Override
            public int compare(Map<String, String> o1, Map<String, String> o2) {
                // group by camel context first, then by route name
                String c1 = o1.get("camelContextName");
                String c2 = o2.get("camelContextName");

                int answer = c1.compareTo(c2);
                if (answer == 0) {
                    // okay from same camel context, then sort by route id
                    answer = o1.get("routeId").compareTo(o2.get("routeId"));
                }
                return answer;
            }
        });
        return answer;
    }

    @Override
    public void resetRouteStats(String camelContextName) throws Exception {
        CamelContext context = this.getLocalCamelContext(camelContextName);
        if (context == null) {
            return;
        }

        ManagementAgent agent = context.getManagementStrategy().getManagementAgent();
        if (agent != null) {
            MBeanServer mBeanServer = agent.getMBeanServer();

            // reset route mbeans
            ObjectName query = ObjectName.getInstance(agent.getMBeanObjectDomainName() + ":type=routes,*");
            Set<ObjectName> set = mBeanServer.queryNames(query, null);
            for (ObjectName routeMBean : set) {
                String camelId = (String) mBeanServer.getAttribute(routeMBean, "CamelId");
                if (camelId != null && camelId.equals(context.getName())) {
                    mBeanServer.invoke(routeMBean, "reset", new Object[]{true}, new String[]{"boolean"});
                }
            }
        }
    }

    @Override
    public void startRoute(String camelContextName, String routeId) throws Exception {
        CamelContext context = getLocalCamelContext(camelContextName);
        if (context != null) {
            context.getRouteController().startRoute(routeId);
        }
    }

    @Override
    public void stopRoute(String camelContextName, String routeId) throws Exception {
        CamelContext context = getLocalCamelContext(camelContextName);
        if (context != null) {
            context.getRouteController().stopRoute(routeId);
        }
    }

    @Override
    public void suspendRoute(String camelContextName, String routeId) throws Exception {
        CamelContext context = getLocalCamelContext(camelContextName);
        if (context != null) {
            context.getRouteController().suspendRoute(routeId);
        }
    }

    @Override
    public void resumeRoute(String camelContextName, String routeId) throws Exception {
        CamelContext context = getLocalCamelContext(camelContextName);
        if (context != null) {
            context.getRouteController().resumeRoute(routeId);
        }
    }

    @Override
    public String getRouteModelAsXml(String routeId, String camelContextName) throws Exception {
        CamelContext context = this.getLocalCamelContext(camelContextName);
        if (context == null) {
            return null;
        }
        RouteDefinition route = context.getExtension(Model.class).getRouteDefinition(routeId);
        if (route == null) {
            return null;
        }

        ExtendedCamelContext ecc = context.adapt(ExtendedCamelContext.class);
        return ecc.getModelToXMLDumper().dumpModelAsXml(context, route);
    }

    @Override
    public String getRouteStatsAsXml(String routeId, String camelContextName, boolean fullStats, boolean includeProcessors) throws Exception {
        CamelContext context = this.getLocalCamelContext(camelContextName);
        if (context == null) {
            return null;
        }

        ManagementAgent agent = context.getManagementStrategy().getManagementAgent();
        if (agent != null) {
            MBeanServer mBeanServer = agent.getMBeanServer();
            Set<ObjectName> set = mBeanServer.queryNames(new ObjectName(agent.getMBeanObjectDomainName() + ":type=routes,name=\"" + routeId + "\",*"), null);            
            for (ObjectName routeMBean : set) {

                // the route must be part of the camel context
                String camelId = (String) mBeanServer.getAttribute(routeMBean, "CamelId");
                if (camelId != null && camelId.equals(camelContextName)) {
                    String xml = (String) mBeanServer.invoke(routeMBean, "dumpRouteStatsAsXml", new Object[]{fullStats, includeProcessors}, new String[]{"boolean", "boolean"});
                    return xml;
                }
            }
        }
        return null;
    }

    @Override
    public String getStepStatsAsXml(String routeId, String camelContextName, boolean fullStats) throws Exception {
        CamelContext context = this.getLocalCamelContext(camelContextName);
        if (context == null) {
            return null;
        }

        ManagementAgent agent = context.getManagementStrategy().getManagementAgent();
        if (agent != null) {
            MBeanServer mBeanServer = agent.getMBeanServer();
            Set<ObjectName> set = mBeanServer.queryNames(new ObjectName(agent.getMBeanObjectDomainName() + ":type=routes,name=\"" + routeId + "\",*"), null);
            for (ObjectName routeMBean : set) {

                // the route must be part of the camel context
                String camelId = (String) mBeanServer.getAttribute(routeMBean, "CamelId");
                if (camelId != null && camelId.equals(camelContextName)) {
                    String xml = (String) mBeanServer.invoke(routeMBean, "dumpStepStatsAsXml", new Object[]{fullStats}, new String[]{"boolean"});
                    return xml;
                }
            }
        }
        return null;
    }

    @Override
    public String getRestModelAsXml(String camelContextName) throws Exception {
        CamelContext context = this.getLocalCamelContext(camelContextName);
        if (context == null) {
            return null;
        }

        List<RestDefinition> rests = context.getExtension(Model.class).getRestDefinitions();
        if (rests == null || rests.isEmpty()) {
            return null;
        }
        // use a rests definition to dump the rests
        RestsDefinition def = new RestsDefinition();
        def.setRests(rests);

        ExtendedCamelContext ecc = context.adapt(ExtendedCamelContext.class);
        return ecc.getModelToXMLDumper().dumpModelAsXml(context, def);
    }

    @Override
    public String getRestApiDocAsJson(String camelContextName) throws Exception {
        CamelContext context = this.getLocalCamelContext(camelContextName);
        if (context == null) {
            return null;
        }

        return context.getRestRegistry().apiDocAsJson();
    }

    @Override
    public List<Map<String, String>> getEndpoints(String camelContextName) throws Exception {
        List<Map<String, String>> answer = new ArrayList<>();

        if (camelContextName != null) {
            CamelContext context = this.getLocalCamelContext(camelContextName);
            if (context != null) {
                List<Endpoint> endpoints = new ArrayList<>(context.getEndpoints());
                // sort routes
                Collections.sort(endpoints, new Comparator<Endpoint>() {
                    @Override
                    public int compare(Endpoint o1, Endpoint o2) {
                        return o1.getEndpointKey().compareTo(o2.getEndpointKey());
                    }
                });
                for (Endpoint endpoint : endpoints) {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("camelContextName", context.getName());
                    row.put("uri", endpoint.getEndpointUri());
                    row.put("state", getEndpointState(endpoint));
                    answer.add(row);
                }
            }
        }
        return answer;
    }

    @Override
    public List<Map<String, String>> getEndpointRuntimeStatistics(String camelContextName) throws Exception {
        List<Map<String, String>> answer = new ArrayList<>();

        if (camelContextName != null) {
            CamelContext context = this.getLocalCamelContext(camelContextName);
            if (context != null && context.getRuntimeEndpointRegistry() != null) {
                EndpointRegistry staticRegistry = context.getEndpointRegistry();
                for (RuntimeEndpointRegistry.Statistic stat : context.getRuntimeEndpointRegistry().getEndpointStatistics()) {

                    String url = stat.getUri();
                    String routeId = stat.getRouteId();
                    String direction = stat.getDirection();
                    boolean isStatic = staticRegistry.isStatic(url);
                    boolean isDynamic = staticRegistry.isDynamic(url);
                    long hits = stat.getHits();

                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("camelContextName", context.getName());
                    row.put("uri", url);
                    row.put("routeId", routeId);
                    row.put("direction", direction);
                    row.put("static", Boolean.toString(isStatic));
                    row.put("dynamic", Boolean.toString(isDynamic));
                    row.put("hits", "" + hits);
                    answer.add(row);
                }
            }

            // sort the list
            Collections.sort(answer, new Comparator<Map<String, String>>() {
                @Override
                public int compare(Map<String, String> endpoint1, Map<String, String> endpoint2) {
                    // sort by route id
                    String route1 = endpoint1.get("routeId");
                    String route2 = endpoint2.get("routeId");
                    int num = route1.compareTo(route2);
                    if (num == 0) {
                        // we want in before out
                        String dir1 = endpoint1.get("direction");
                        String dir2 = endpoint2.get("direction");
                        num = dir1.compareTo(dir2);
                    }
                    return num;
                }

            });
        }
        return answer;
    }

    @Override
    public List<Map<String, String>> getRestServices(String camelContextName) throws Exception {
        List<Map<String, String>> answer = new ArrayList<>();

        if (camelContextName != null) {
            CamelContext context = this.getLocalCamelContext(camelContextName);
            if (context != null) {
                List<RestRegistry.RestService> services = new ArrayList<>(context.getRestRegistry().listAllRestServices());
                Collections.sort(services, new Comparator<RestRegistry.RestService>() {
                    @Override
                    public int compare(RestRegistry.RestService o1, RestRegistry.RestService o2) {
                        return o1.getUrl().compareTo(o2.getUrl());
                    }
                });
                for (RestRegistry.RestService service : services) {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("basePath", service.getBasePath());
                    row.put("baseUrl", service.getBaseUrl());
                    row.put("consumes", service.getConsumes());
                    row.put("description", service.getDescription());
                    row.put("inType", service.getInType());
                    row.put("method", service.getMethod());
                    row.put("outType", service.getOutType());
                    row.put("produces", service.getProduces());
                    row.put("routeId", service.getRouteId());
                    row.put("state", service.getState());
                    row.put("uriTemplate", service.getUriTemplate());
                    row.put("url", service.getUrl());
                    answer.add(row);
                }
            }
        }
        return answer;
    }

    @Override
    public List<Map<String, String>> getTransformers(String camelContextName) throws Exception {
        List<Map<String, String>> answer = new ArrayList<>();

        if (camelContextName != null) {
            CamelContext context = this.getLocalCamelContext(camelContextName);
            if (context != null) {
                List<Transformer> transformers = new ArrayList<>(context.getTransformerRegistry().values());
                for (Transformer transformer : transformers) {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("camelContextName", context.getName());
                    row.put("scheme", transformer.getModel());
                    row.put("from", transformer.getFrom().toString());
                    row.put("to", transformer.getTo().toString());
                    row.put("state", transformer.getStatus().toString());
                    row.put("description", transformer.toString());
                    answer.add(row);
                }
            }
        }
        return answer;
    }

    @Override
    public List<Map<String, String>> getValidators(String camelContextName) throws Exception {
        List<Map<String, String>> answer = new ArrayList<>();

        if (camelContextName != null) {
            CamelContext context = this.getLocalCamelContext(camelContextName);
            if (context != null) {
                List<Validator> validators = new ArrayList<>(context.getValidatorRegistry().values());
                for (Validator validator : validators) {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("camelContextName", context.getName());
                    row.put("type", validator.getType().toString());
                    row.put("state", validator.getStatus().toString());
                    row.put("description", validator.toString());
                    answer.add(row);
                }
            }
        }
        return answer;
    }

    private static String getEndpointState(Endpoint endpoint) {
        // must use String type to be sure remote JMX can read the attribute without requiring Camel classes.
        if (endpoint instanceof StatefulService) {
            ServiceStatus status = ((StatefulService) endpoint).getStatus();
            return status.name();
        }

        // assume started if not a ServiceSupport instance
        return ServiceStatus.Started.name();
    }

    private static String getRouteState(Route route) {
        // must use String type to be sure remote JMX can read the attribute without requiring Camel classes.

        ServiceStatus status = route.getCamelContext().getRouteController().getRouteStatus(route.getId());
        if (status != null) {
            return status.name();
        }

        // assume started if not a ServiceSupport instance
        return ServiceStatus.Started.name();
    }

}
