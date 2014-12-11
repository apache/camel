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
package org.apache.camel.commands.jolokia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.management.ObjectName;

import org.apache.camel.commands.AbstractCamelController;
import org.apache.camel.util.LRUCache;
import org.apache.camel.util.StringHelper;
import org.jolokia.client.J4pClient;
import org.jolokia.client.request.J4pExecRequest;
import org.jolokia.client.request.J4pExecResponse;
import org.jolokia.client.request.J4pReadRequest;
import org.jolokia.client.request.J4pReadResponse;
import org.jolokia.client.request.J4pSearchRequest;
import org.jolokia.client.request.J4pSearchResponse;
import org.json.simple.JSONObject;

/**
 * A {@link org.apache.camel.commands.CamelController} that uses Jolokia Client to connect to remote JVMs which
 * has an Jolokia agent running.
 */
public class JolokiaCamelController extends AbstractCamelController implements RemoteCamelController {

    private Map<String, ObjectName> cache = new LRUCache<String, ObjectName>(1000);

    private J4pClient jolokia;
    private String url;

    private ObjectName lookupCamelContext(String camelContextName) throws Exception {
        ObjectName on = cache.get(camelContextName);
        if (on == null) {
            ObjectName found = null;
            J4pSearchResponse sr = jolokia.execute(new J4pSearchRequest("*:type=context,*"));
            if (sr != null) {
                for (ObjectName name : sr.getObjectNames()) {
                    String id = name.getKeyProperty("name");
                    id = StringHelper.removeLeadingAndEndingQuotes(id);
                    if (camelContextName.equals(id)) {
                        found = name;
                        break;
                    }
                }
            }
            if (found != null) {
                on = found;
                cache.put(camelContextName, on);
            }
        }
        return on;
    }

    @Override
    public void connect(String url, String username, String password) throws Exception {
        this.jolokia = JolokiaClientFactory.createJolokiaClient(url, username, password);
        this.url = url;
    }

    @Override
    public Map<String, Object> getCamelContextInformation(String camelContextName) throws Exception {
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        Map<String, Object> answer = new LinkedHashMap<String, Object>();

        ObjectName found = lookupCamelContext(camelContextName);
        if (found != null) {
            J4pReadResponse rr = jolokia.execute(new J4pReadRequest(found));
            if (rr != null) {
                for (String key : rr.getAttributes()) {
                    answer.put(asKey(key), rr.getValue(key));
                }
            }
        }

        return answer;
    }

    @Override
    public List<Map<String, String>> getCamelContexts() throws Exception {
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        List<Map<String, String>> answer = new ArrayList<Map<String, String>>();

        J4pSearchResponse sr = jolokia.execute(new J4pSearchRequest("*:type=context,*"));

        List<J4pReadRequest> list = new ArrayList<J4pReadRequest>();
        for (ObjectName on : sr.getObjectNames()) {
            list.add(new J4pReadRequest(on, "CamelId", "State", "Uptime"));
        }

        List<J4pReadResponse> lrr = jolokia.execute(list);
        for (J4pReadResponse rr : lrr) {
            Map<String, String> row = new LinkedHashMap<String, String>();
            row.put("name", rr.getValue("CamelId").toString());
            row.put("state", rr.getValue("State").toString());
            row.put("uptime", rr.getValue("Uptime").toString());
            answer.add(row);
        }

        return answer;
    }

    @Override
    public String getCamelContextStatsAsXml(String camelContextName, boolean fullStats, boolean includeProcessors) throws Exception {
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        ObjectName found = lookupCamelContext(camelContextName);
        if (found != null) {
            J4pExecResponse er = jolokia.execute(new J4pExecRequest(found, "dumpRoutesStatsAsXml(boolean,boolean)", fullStats, includeProcessors));
            if (er != null) {
                String xml = er.getValue();
                return xml;
            }
        }

        return null;
    }

    @Override
    public void startContext(String camelContextName) throws Exception {
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        ObjectName found = lookupCamelContext(camelContextName);
        if (found != null) {
            jolokia.execute(new J4pExecRequest(found, "start"));
        }
    }

    @Override
    public void stopContext(String camelContextName) throws Exception {
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        ObjectName found = lookupCamelContext(camelContextName);
        if (found != null) {
            jolokia.execute(new J4pExecRequest(found, "stop"));
        }
    }

    @Override
    public void suspendContext(String camelContextName) throws Exception {
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        ObjectName found = lookupCamelContext(camelContextName);
        if (found != null) {
            jolokia.execute(new J4pExecRequest(found, "suspend"));
        }
    }

    @Override
    public void resumeContext(String camelContextName) throws Exception {
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        ObjectName found = lookupCamelContext(camelContextName);
        if (found != null) {
            jolokia.execute(new J4pExecRequest(found, "resume"));
        }
    }

    @Override
    public List<Map<String, String>> getRoutes(String camelContextName) throws Exception {
        return getRoutes(camelContextName, null);
    }

    @Override
    public List<Map<String, String>> getRoutes(String camelContextName, String filter) throws Exception {
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        List<Map<String, String>> answer = new ArrayList<Map<String, String>>();

        if (camelContextName != null) {

            J4pSearchResponse sr = jolokia.execute(new J4pSearchRequest("*:type=routes,*"));

            List<J4pReadRequest> list = new ArrayList<J4pReadRequest>();
            for (ObjectName on : sr.getObjectNames()) {
                list.add(new J4pReadRequest(on, "CamelId", "RouteId", "State"));
            }

            List<J4pReadResponse> lrr = jolokia.execute(list);
            for (J4pReadResponse rr : lrr) {
                String routeId = rr.getValue("RouteId").toString();
                if (filter == null || routeId.matches(filter)) {
                    Map<String, String> row = new LinkedHashMap<String, String>();
                    row.put("camelContextName", rr.getValue("CamelId").toString());
                    row.put("routeId", routeId);
                    row.put("state", rr.getValue("State").toString());
                    answer.add(row);
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
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        ObjectName found = lookupCamelContext(camelContextName);

        if (found != null) {
            String pattern = String.format("%s:context=%s,type=routes,name=*", found.getDomain(), found.getKeyProperty("context"));
            J4pSearchResponse sr = jolokia.execute(new J4pSearchRequest(pattern));

            List<J4pExecRequest> list = new ArrayList<J4pExecRequest>();
            for (ObjectName on : sr.getObjectNames()) {
                list.add(new J4pExecRequest(on, "reset(boolean)", true));
            }

            jolokia.execute(list);
        }
    }

    @Override
    public void startRoute(String camelContextName, String routeId) throws Exception {
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        ObjectName found = lookupCamelContext(camelContextName);
        if (found != null) {
            String pattern = String.format("%s:context=%s,type=routes,name=\"%s\"", found.getDomain(), found.getKeyProperty("context"), routeId);
            ObjectName on = ObjectName.getInstance(pattern);
            jolokia.execute(new J4pExecRequest(on, "start()"));
        }
    }

    @Override
    public void stopRoute(String camelContextName, String routeId) throws Exception {
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        ObjectName found = lookupCamelContext(camelContextName);
        if (found != null) {
            String pattern = String.format("%s:context=%s,type=routes,name=\"%s\"", found.getDomain(), found.getKeyProperty("context"), routeId);
            ObjectName on = ObjectName.getInstance(pattern);
            jolokia.execute(new J4pExecRequest(on, "stop()"));
        }
    }

    @Override
    public void suspendRoute(String camelContextName, String routeId) throws Exception {
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        ObjectName found = lookupCamelContext(camelContextName);
        if (found != null) {
            String pattern = String.format("%s:context=%s,type=routes,name=\"%s\"", found.getDomain(), found.getKeyProperty("context"), routeId);
            ObjectName on = ObjectName.getInstance(pattern);
            jolokia.execute(new J4pExecRequest(on, "suspend()"));
        }
    }

    @Override
    public void resumeRoute(String camelContextName, String routeId) throws Exception {
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        ObjectName found = lookupCamelContext(camelContextName);
        if (found != null) {
            String pattern = String.format("%s:context=%s,type=routes,name=\"%s\"", found.getDomain(), found.getKeyProperty("context"), routeId);
            ObjectName on = ObjectName.getInstance(pattern);
            jolokia.execute(new J4pExecRequest(on, "resume()"));
        }
    }

    @Override
    public String getRouteModelAsXml(String camelContextName, String routeId) throws Exception {
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        ObjectName found = lookupCamelContext(camelContextName);
        if (found != null) {
            String pattern = String.format("%s:context=%s,type=routes,name=\"%s\"", found.getDomain(), found.getKeyProperty("context"), routeId);
            ObjectName on = ObjectName.getInstance(pattern);
            J4pExecResponse response = jolokia.execute(new J4pExecRequest(on, "dumpRouteAsXml()"));
            if (response != null) {
                String xml = response.getValue();
                return xml;
            }
        }

        return null;
    }

    @Override
    public String getRouteStatsAsXml(String camelContextName, String routeId, boolean fullStats, boolean includeProcessors) throws Exception {
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        ObjectName found = lookupCamelContext(camelContextName);
        if (found != null) {
            String pattern = String.format("%s:context=%s,type=routes,name=\"%s\"", found.getDomain(), found.getKeyProperty("context"), routeId);
            ObjectName on = ObjectName.getInstance(pattern);
            J4pExecResponse response = jolokia.execute(new J4pExecRequest(on, "dumpRouteStatsAsXml(boolean,boolean)", fullStats, includeProcessors));
            if (response != null) {
                String xml = response.getValue();
                return xml;
            }
        }

        return null;
    }

    @Override
    public String getRestModelAsXml(String camelContextName) throws Exception {
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        ObjectName found = lookupCamelContext(camelContextName);
        if (found != null) {
            J4pExecResponse response = jolokia.execute(new J4pExecRequest(found, "dumpRestsAsXml()"));
            if (response != null) {
                String xml = response.getValue();
                return xml;
            }
        }

        return null;
    }

    @Override
    public List<Map<String, String>> getEndpoints(String camelContextName) throws Exception {
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        List<Map<String, String>> answer = new ArrayList<Map<String, String>>();

        ObjectName found = lookupCamelContext(camelContextName);
        if (found != null) {
            J4pSearchResponse sr = jolokia.execute(new J4pSearchRequest("*:type=endpoints,*"));

            List<J4pReadRequest> list = new ArrayList<J4pReadRequest>();
            for (ObjectName on : sr.getObjectNames()) {
                list.add(new J4pReadRequest(on, "CamelId", "EndpointUri", "State"));
            }

            List<J4pReadResponse> lrr = jolokia.execute(list);
            for (J4pReadResponse rr : lrr) {
                Map<String, String> row = new LinkedHashMap<String, String>();
                row.put("camelContextName", rr.getValue("CamelId").toString());
                row.put("uri", rr.getValue("EndpointUri").toString());
                row.put("state", rr.getValue("State").toString());
                answer.add(row);
            }
        }

        return answer;
    }

    @Override
    public List<Map<String, String>> getRestServices(String camelContextName) throws Exception {
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        List<Map<String, String>> answer = new ArrayList<Map<String, String>>();

        ObjectName found = lookupCamelContext(camelContextName);
        if (found != null) {
            String pattern = String.format("%s:context=%s,type=services,name=DefaultRestRegistry", found.getDomain(), found.getKeyProperty("context"));
            ObjectName on = ObjectName.getInstance(pattern);

            J4pExecResponse response = jolokia.execute(new J4pExecRequest(on, "listRestServices()"));
            if (response != null) {
                JSONObject data = response.asJSONObject();
                // TODO: parse the open data type
            }

            /*
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
            }   */
        }

        return answer;
    }

    @Override
    public String explainEndpointAsJSon(String camelContextName, String uri, boolean allOptions) throws Exception {
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        ObjectName found = lookupCamelContext(camelContextName);
        if (found != null) {
            J4pExecResponse response = jolokia.execute(new J4pExecRequest(found, "explainEndpointJson(java.lang.String,boolean)", uri, allOptions));
            if (response != null) {
                String json = response.getValue();
                return json;
            }
        }

        return null;
    }

    @Override
    public List<Map<String, String>> listComponents(String camelContextName) throws Exception {
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        // TODO: implement me

        return null;
    }

    private static String asKey(String attributeKey) {
        char ch = Character.toLowerCase(attributeKey.charAt(0));
        return ch + attributeKey.substring(1);
    }

}
