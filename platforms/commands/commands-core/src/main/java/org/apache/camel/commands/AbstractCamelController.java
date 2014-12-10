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
package org.apache.camel.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.StatefulService;
import org.apache.camel.catalog.CamelComponentCatalog;
import org.apache.camel.catalog.DefaultCamelComponentCatalog;
import org.apache.camel.commands.internal.RegexUtil;
import org.apache.camel.model.ModelHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.ManagementAgent;
import org.apache.camel.spi.RestRegistry;
import org.apache.camel.util.JsonSchemaHelper;

/**
 * Abstract {@link org.apache.camel.commands.CamelController} that implementators should extend.
 */
public abstract class AbstractCamelController implements CamelController {

    private CamelComponentCatalog catalog = new DefaultCamelComponentCatalog();

    public CamelContext getCamelContext(String name) throws Exception {
        for (CamelContext camelContext : this.getCamelContexts()) {
            if (camelContext.getName().equals(name)) {
                return camelContext;
            }
        }
        return null;
    }

    public List<Map<String, String>> getRoutes(String camelContextName) throws Exception {
        return getRoutes(camelContextName, null);
    }

    public List<Map<String, String>> getRoutes(String camelContextName, String filter) throws Exception {
        List<Map<String, String>> answer = new ArrayList<Map<String, String>>();

        if (camelContextName != null) {
            CamelContext context = this.getCamelContext(camelContextName);
            if (context != null) {
                for (Route route : context.getRoutes()) {
                    if (filter == null || route.getId().matches(filter)) {
                        Map<String, String> row = new LinkedHashMap<String, String>();
                        row.put("camelContextName", context.getName());
                        row.put("routeId", route.getId());
                        row.put("state", getRouteState(route));
                        answer.add(row);
                    }
                }
            }
        } else {
            List<CamelContext> camelContexts = this.getCamelContexts();
            for (CamelContext camelContext : camelContexts) {
                for (Route route : camelContext.getRoutes()) {
                    if (filter == null || route.getId().matches(filter)) {
                        Map<String, String> row = new LinkedHashMap<String, String>();
                        row.put("camelContextName", camelContext.getName());
                        row.put("routeId", route.getId());
                        row.put("state", getRouteState(route));
                        answer.add(row);
                    }
                }
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

    public void resetRouteStats(String camelContextName) throws Exception {
        CamelContext context = this.getCamelContext(camelContextName);
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

    public void startRoute(String camelContextName, String routeId) throws Exception {
        CamelContext context = getCamelContext(camelContextName);
        if (context != null) {
            context.startRoute(routeId);
        }
    }

    public void stopRoute(String camelContextName, String routeId) throws Exception {
        CamelContext context = getCamelContext(camelContextName);
        if (context != null) {
            context.stopRoute(routeId);
        }
    }

    public void suspendRoute(String camelContextName, String routeId) throws Exception {
        CamelContext context = getCamelContext(camelContextName);
        if (context != null) {
            context.suspendRoute(routeId);
        }
    }

    public void resumeRoute(String camelContextName, String routeId) throws Exception {
        CamelContext context = getCamelContext(camelContextName);
        if (context != null) {
            context.resumeRoute(routeId);
        }
    }

    @SuppressWarnings("deprecation")
    public String getRouteModelAsXml(String routeId, String camelContextName) throws Exception {
        CamelContext context = this.getCamelContext(camelContextName);
        if (context == null) {
            return null;
        }
        RouteDefinition route = context.getRouteDefinition(routeId);
        if (route == null) {
            return null;
        }

        return ModelHelper.dumpModelAsXml(route);
    }

    @Override
    public String getRouteStatsAsXml(String routeId, String camelContextName, boolean fullStats, boolean includeProcessors) throws Exception {
        CamelContext context = this.getCamelContext(camelContextName);
        if (context == null) {
            return null;
        }

        ManagementAgent agent = context.getManagementStrategy().getManagementAgent();
        if (agent != null) {
            MBeanServer mBeanServer = agent.getMBeanServer();
            Set<ObjectName> set = mBeanServer.queryNames(new ObjectName(agent.getMBeanObjectDomainName() + ":type=routes,name=\"" + routeId + "\",*"), null);
            Iterator<ObjectName> iterator = set.iterator();
            if (iterator.hasNext()) {
                ObjectName routeMBean = iterator.next();

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

    @SuppressWarnings("deprecation")
    public String getRestModelAsXml(String camelContextName) throws Exception {
        CamelContext context = this.getCamelContext(camelContextName);
        if (context == null) {
            return null;
        }

        List<RestDefinition> rests = context.getRestDefinitions();
        if (rests == null || rests.isEmpty()) {
            return null;
        }
        // use a rests definition to dump the rests
        RestsDefinition def = new RestsDefinition();
        def.setRests(rests);
        return ModelHelper.dumpModelAsXml(def);
    }

    public List<Map<String, String>> getEndpoints(String camelContextName) throws Exception {
        List<Map<String, String>> answer = new ArrayList<Map<String, String>>();

        if (camelContextName != null) {
            CamelContext context = this.getCamelContext(camelContextName);
            if (context != null) {
                List<Endpoint> endpoints = new ArrayList<Endpoint>(context.getEndpoints());
                // sort routes
                Collections.sort(endpoints, new Comparator<Endpoint>() {
                    @Override
                    public int compare(Endpoint o1, Endpoint o2) {
                        return o1.getEndpointKey().compareTo(o2.getEndpointKey());
                    }
                });
                for (Endpoint endpoint : endpoints) {
                    Map<String, String> row = new LinkedHashMap<String, String>();
                    row.put("camelContextName", context.getName());
                    row.put("uri", endpoint.getEndpointUri());
                    row.put("state", getEndpointState(endpoint));
                    answer.add(row);
                }
            }
        }
        return answer;
    }

    public List<Map<String, String>> getRestServices(String camelContextName) throws Exception {
        List<Map<String, String>> answer = new ArrayList<Map<String, String>>();

        if (camelContextName != null) {
            CamelContext context = this.getCamelContext(camelContextName);
            if (context != null) {
                List<RestRegistry.RestService> services = new ArrayList<RestRegistry.RestService>(context.getRestRegistry().listAllRestServices());
                Collections.sort(services, new Comparator<RestRegistry.RestService>() {
                    @Override
                    public int compare(RestRegistry.RestService o1, RestRegistry.RestService o2) {
                        return o1.getUrl().compareTo(o2.getUrl());
                    }
                });
                for (RestRegistry.RestService service : services) {
                    Map<String, String> row = new LinkedHashMap<String, String>();
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

    public String explainEndpointAsJSon(String camelContextName, String uri, boolean allOptions) throws Exception {
        CamelContext context = this.getCamelContext(camelContextName);
        if (context == null) {
            return null;
        }
        return context.explainEndpointJson(uri, allOptions);
    }

    public List<Map<String, String>> listComponents(String camelContextName) throws Exception {
        CamelContext context = this.getCamelContext(camelContextName);
        if (context == null) {
            return null;
        }

        List<Map<String, String>> answer = new ArrayList<Map<String, String>>();

        // find all components
        Map<String, Properties> components = context.findComponents();

        // gather component detail for each component
        for (Map.Entry<String, Properties> entry : components.entrySet()) {
            String name = entry.getKey();
            String description = null;
            String label = null;
            // the status can be:
            // - loaded = in use
            // - classpath = on the classpath
            // - release = available from the Apache Camel release
            String status = context.hasComponent(name) != null ? "in use" : "on classpath";
            String type = null;
            String groupId = null;
            String artifactId = null;
            String version = null;

            // load component json data, and parse it to gather the component meta-data
            String json = context.getComponentParameterJsonSchema(name);
            List<Map<String, String>> rows = JsonSchemaHelper.parseJsonSchema("component", json, false);
            for (Map<String, String> row : rows) {
                if (row.containsKey("description")) {
                    description = row.get("description");
                } else if (row.containsKey("label")) {
                    label = row.get("label");
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

            Map<String, String> row = new HashMap<String, String>();
            row.put("name", name);
            row.put("status", status);
            if (description != null) {
                row.put("description", description);
            }
            if (label != null) {
                row.put("label", label);
            }
            if (type != null) {
                row.put("type", type);
            }
            if (groupId != null) {
                row.put("groupId", groupId);
            }
            if (artifactId != null) {
                row.put("artifactId", artifactId);
            }
            if (version != null) {
                row.put("version", version);
            }

            answer.add(row);
        }

        return answer;
    }

    @Override
    public List<Map<String, String>> listComponentsCatalog(String filter) throws Exception {
        List<Map<String, String>> answer = new ArrayList<Map<String, String>>();

        if (filter != null) {
            filter = RegexUtil.wildcardAsRegex(filter);
        }

        List<String> names = filter != null ? catalog.findComponentNames(filter) : catalog.findComponentNames();
        for (String name : names) {
            // load component json data, and parse it to gather the component meta-data
            String json = catalog.componentJSonSchema(name);
            List<Map<String, String>> rows = JsonSchemaHelper.parseJsonSchema("component", json, false);

            String description = null;
            String label = null;
            // the status can be:
            // - loaded = in use
            // - classpath = on the classpath
            // - release = available from the Apache Camel release
            String status = "release";
            String type = null;
            String groupId = null;
            String artifactId = null;
            String version = null;
            for (Map<String, String> row : rows) {
                if (row.containsKey("description")) {
                    description = row.get("description");
                } else if (row.containsKey("label")) {
                    label = row.get("label");
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

            Map<String, String> row = new HashMap<String, String>();
            row.put("name", name);
            row.put("status", status);
            if (description != null) {
                row.put("description", description);
            }
            if (label != null) {
                row.put("label", label);
            }
            if (type != null) {
                row.put("type", type);
            }
            if (groupId != null) {
                row.put("groupId", groupId);
            }
            if (artifactId != null) {
                row.put("artifactId", artifactId);
            }
            if (version != null) {
                row.put("version", version);
            }

            answer.add(row);
        }

        return answer;
    }

    @Override
    public Map<String, Set<String>> listLabelCatalog() throws Exception {
        Map<String, Set<String>> answer = new LinkedHashMap<String, Set<String>>();

        Set<String> labels = catalog.findLabels();
        for (String label : labels) {
            List<Map<String, String>> components = listComponentsCatalog(label);
            if (!components.isEmpty()) {
                Set<String> names = new LinkedHashSet<String>();
                for (Map<String, String> info : components) {
                    String name = info.get("name");
                    if (name != null) {
                        names.add(name);
                    }
                }
                answer.put(label, names);
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
        if (route instanceof StatefulService) {
            ServiceStatus status = ((StatefulService) route).getStatus();
            return status.name();
        }

        // assume started if not a ServiceSupport instance
        return ServiceStatus.Started.name();
    }

}
