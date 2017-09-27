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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.management.InstanceNotFoundException;
import javax.management.ObjectName;

import org.apache.camel.commands.AbstractCamelController;

import org.jolokia.client.J4pClient;
import org.jolokia.client.exception.J4pException;
import org.jolokia.client.exception.J4pRemoteException;
import org.jolokia.client.request.J4pExecRequest;
import org.jolokia.client.request.J4pExecResponse;
import org.jolokia.client.request.J4pReadRequest;
import org.jolokia.client.request.J4pReadResponse;
import org.jolokia.client.request.J4pSearchRequest;
import org.jolokia.client.request.J4pSearchResponse;
import org.jolokia.client.request.J4pVersionRequest;
import org.jolokia.client.request.J4pVersionResponse;
import org.json.simple.JSONObject;

/**
 * A {@link org.apache.camel.commands.CamelController} that uses Jolokia Client to connect to remote JVMs which
 * has an Jolokia agent running.
 */
public class DefaultJolokiaCamelController extends AbstractCamelController implements JolokiaCamelController {

    private Map<String, ObjectName> cache = new HashMap<String, ObjectName>(100);

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
                    id = removeLeadingAndEndingQuotes(id);
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
    public void using(J4pClient client) {
        this.jolokia = client;
        this.url = null;
    }

    @Override
    public void connect(String url, String username, String password) throws Exception {
        this.jolokia = JolokiaClientFactory.createJolokiaClient(url, username, password);
        this.url = url;
    }

    @Override
    public boolean ping() {
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        J4pVersionResponse vr;
        try {
            vr = jolokia.execute(new J4pVersionRequest());
            return vr != null && vr.getValue() != null;
        } catch (J4pException e) {
            return false;
        }
    }

    @Override
    public Map<String, Object> getCamelContextInformation(String camelContextName) throws Exception {
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        Map<String, Object> answer = new LinkedHashMap<String, Object>();

        ObjectName found = lookupCamelContext(camelContextName);
        if (found != null) {

            String pattern = String.format("%s:context=%s,type=services,name=DefaultTypeConverter", found.getDomain(), found.getKeyProperty("context"));
            ObjectName tc = ObjectName.getInstance(pattern);

            String pattern2 = String.format("%s:context=%s,type=services,name=DefaultAsyncProcessorAwaitManager", found.getDomain(), found.getKeyProperty("context"));
            ObjectName am = ObjectName.getInstance(pattern2);

            List<J4pReadRequest> list = new ArrayList<J4pReadRequest>();
            list.add(new J4pReadRequest(found));
            list.add(new J4pReadRequest(tc));
            list.add(new J4pReadRequest(am));

            List<J4pReadResponse> rr = jolokia.execute(list);
            if (rr != null && rr.size() > 0) {
                // camel context attributes
                J4pReadResponse first = rr.get(0);
                for (String key : first.getAttributes()) {
                    answer.put(asKey(key), first.getValue(key));
                }

                // type converter attributes
                if (rr.size() >= 2) {
                    J4pReadResponse second = rr.get(1);
                    for (String key : second.getAttributes()) {
                        answer.put("typeConverter." + asKey(key), second.getValue(key));
                    }
                }

                // async processor await manager attributes
                if (rr.size() >= 3) {
                    J4pReadResponse second = rr.get(2);
                    for (String key : second.getAttributes()) {
                        answer.put("asyncProcessorAwaitManager." + asKey(key), second.getValue(key));
                    }
                }
            }

            // would be great if there was an api in jolokia to read optional (eg ignore if an mbean does not exists)
            answer.put("streamCachingEnabled", false);
            try {
                pattern = String.format("%s:context=%s,type=services,name=DefaultStreamCachingStrategy", found.getDomain(), found.getKeyProperty("context"));
                ObjectName sc = ObjectName.getInstance(pattern);

                // there is only a mbean if stream caching is enabled
                J4pReadResponse rsc = jolokia.execute(new J4pReadRequest(sc));
                if (rsc != null) {
                    for (String key : rsc.getAttributes()) {
                        answer.put("streamCaching." + asKey(key), rsc.getValue(key));
                    }
                }
                answer.put("streamCachingEnabled", true);
            } catch (J4pRemoteException e) {
                // ignore
                boolean ignore = InstanceNotFoundException.class.getName().equals(e.getErrorType());
                if (!ignore) {
                    throw e;
                }
            }

            // store some data using special names as that is what the core-commands expects
            answer.put("name", answer.get("camelId"));
            answer.put("status", answer.get("state"));
            answer.put("version", answer.get("camelVersion"));
            answer.put("suspended", "Suspended".equals(answer.get("state")));
            TimeUnit unit = TimeUnit.valueOf((String) answer.get("timeUnit"));
            long timeout = (Long) answer.get("timeout");
            answer.put("shutdownTimeout", "" + unit.toSeconds(timeout));
            answer.put("applicationContextClassLoader", answer.get("applicationContextClassName"));
            answer.put("HeadersMapFactory", answer.get("headersMapFactoryClassName"));
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
            list.add(new J4pReadRequest(on, "CamelId", "State", "Uptime", "ExchangesTotal", "ExchangesInflight", "ExchangesFailed"));
        }

        List<J4pReadResponse> lrr = jolokia.execute(list);
        for (J4pReadResponse rr : lrr) {
            Map<String, String> row = new LinkedHashMap<String, String>();
            row.put("name", rr.getValue("CamelId").toString());
            row.put("state", rr.getValue("State").toString());
            row.put("uptime", rr.getValue("Uptime").toString());
            row.put("exchangesTotal", rr.getValue("ExchangesTotal").toString());
            row.put("exchangesInflight", rr.getValue("ExchangesInflight").toString());
            row.put("exchangesFailed", rr.getValue("ExchangesFailed").toString());
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
    public List<Map<String, Object>> browseInflightExchanges(String camelContextName, String route, int limit, boolean sortByLongestDuration) throws Exception {
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        List<Map<String, Object>> answer = new ArrayList<Map<String, Object>>();

        ObjectName found = lookupCamelContext(camelContextName);
        if (found != null) {
            String pattern = String.format("%s:context=%s,type=services,name=DefaultInflightRepository", found.getDomain(), found.getKeyProperty("context"));
            ObjectName on = ObjectName.getInstance(pattern);
            J4pExecResponse er = jolokia.execute(new J4pExecRequest(on, "browse(String,int,boolean)", route, limit, sortByLongestDuration));
            if (er != null) {
                JSONObject data = er.getValue();
                if (data != null) {
                    for (Object obj : data.values()) {
                        JSONObject inflight = (JSONObject) obj;

                        Map<String, Object> row = new LinkedHashMap<String, Object>();
                        row.put("exchangeId", asString(inflight.get("exchangeId")));
                        row.put("fromRouteId", asString(inflight.get("fromRouteId")));
                        row.put("routeId", asString(inflight.get("routeId")));
                        row.put("nodeId", asString(inflight.get("nodeId")));
                        row.put("elapsed", asString(inflight.get("elapsed")));
                        row.put("duration", asString(inflight.get("duration")));
                        answer.add(row);
                    }
                }
            }
        }

        return answer;
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

        ObjectName found = camelContextName != null ? lookupCamelContext(camelContextName) : null;
        if (found != null) {

            String pattern = String.format("%s:context=%s,type=routes,*", found.getDomain(), found.getKeyProperty("context"));
            J4pSearchResponse sr = jolokia.execute(new J4pSearchRequest(pattern));

            List<J4pReadRequest> list = new ArrayList<J4pReadRequest>();
            for (ObjectName on : sr.getObjectNames()) {
                list.add(new J4pReadRequest(on, "CamelId", "RouteId", "State", "Uptime", "ExchangesTotal", "ExchangesInflight", "ExchangesFailed"));
            }

            List<J4pReadResponse> lrr = jolokia.execute(list);
            for (J4pReadResponse rr : lrr) {
                String routeId = rr.getValue("RouteId").toString();
                if (filter == null || routeId.matches(filter)) {
                    Map<String, String> row = new LinkedHashMap<String, String>();
                    row.put("camelContextName", rr.getValue("CamelId").toString());
                    row.put("routeId", routeId);
                    row.put("state", rr.getValue("State").toString());
                    row.put("uptime", rr.getValue("Uptime").toString());
                    row.put("exchangesTotal", rr.getValue("ExchangesTotal").toString());
                    row.put("exchangesInflight", rr.getValue("ExchangesInflight").toString());
                    row.put("exchangesFailed", rr.getValue("ExchangesFailed").toString());
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
    public String getRouteModelAsXml(String routeId, String camelContextName) throws Exception {
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
    public String getRouteStatsAsXml(String routeId, String camelContextName, boolean fullStats, boolean includeProcessors) throws Exception {
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
    public String getRestApiDocAsJson(String camelContextName) throws Exception {
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        ObjectName found = lookupCamelContext(camelContextName);
        if (found != null) {
            String pattern = String.format("%s:context=%s,type=services,name=DefaultRestRegistry", found.getDomain(), found.getKeyProperty("context"));
            ObjectName on = ObjectName.getInstance(pattern);

            J4pExecResponse response = jolokia.execute(new J4pExecRequest(on, "apiDocAsJson()"));
            if (response != null) {
                String json = response.getValue();
                return json;
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
            String pattern = String.format("%s:context=%s,type=endpoints,*", found.getDomain(), found.getKeyProperty("context"));
            J4pSearchResponse sr = jolokia.execute(new J4pSearchRequest(pattern));

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
    public List<Map<String, String>> getEndpointRuntimeStatistics(String camelContextName) throws Exception {
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        List<Map<String, String>> answer = new ArrayList<Map<String, String>>();

        ObjectName found = lookupCamelContext(camelContextName);
        if (found != null) {
            String pattern = String.format("%s:context=%s,type=services,name=DefaultRuntimeEndpointRegistry", found.getDomain(), found.getKeyProperty("context"));
            ObjectName on = ObjectName.getInstance(pattern);

            J4pExecResponse response = jolokia.execute(new J4pExecRequest(on, "endpointStatistics()"));
            if (response != null) {
                JSONObject data = response.getValue();
                for (Object obj : data.values()) {
                    JSONObject data2 = (JSONObject) obj;
                    JSONObject service = (JSONObject) data2.values().iterator().next();

                    Map<String, String> row = new LinkedHashMap<String, String>();
                    row.put("index", asString(service.get("index")));
                    row.put("url", asString(service.get("url")));
                    row.put("routeId", asString(service.get("routeId")));
                    row.put("direction", asString(service.get("direction")));
                    row.put("static", asString(service.get("static")));
                    row.put("dynamic", asString(service.get("dynamic")));
                    row.put("hits", asString(service.get("hits")));
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
                JSONObject data = response.getValue();
                if (data != null) {
                    for (Object obj : data.values()) {
                        JSONObject data2 = (JSONObject) obj;
                        JSONObject service = (JSONObject) data2.values().iterator().next();

                        Map<String, String> row = new LinkedHashMap<String, String>();
                        row.put("basePath", asString(service.get("basePath")));
                        row.put("baseUrl", asString(service.get("baseUrl")));
                        row.put("consumes", asString(service.get("consumes")));
                        row.put("description", asString(service.get("description")));
                        row.put("inType", asString(service.get("inType")));
                        row.put("method", asString(service.get("method")));
                        row.put("outType", asString(service.get("outType")));
                        row.put("produces", asString(service.get("produces")));
                        row.put("routeId", asString(service.get("routeId")));
                        row.put("state", asString(service.get("state")));
                        row.put("uriTemplate", asString(service.get("uriTemplate")));
                        row.put("url", asString(service.get("url")));
                        answer.add(row);
                    }
                }
            }

            // sort the list
            Collections.sort(answer, new Comparator<Map<String, String>>() {
                @Override
                public int compare(Map<String, String> service1, Map<String, String> service2) {
                    String url1 = service1.get("url");
                    String url2 = service2.get("url");
                    return url1.compareTo(url2);
                }
            });
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
    public String explainEipAsJSon(String camelContextName, String nameOrId, boolean allOptions) throws Exception {
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        ObjectName found = lookupCamelContext(camelContextName);
        if (found != null) {
            J4pExecResponse response = jolokia.execute(new J4pExecRequest(found, "explainEipJson(java.lang.String,boolean)", nameOrId, allOptions));
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

        List<Map<String, String>> answer = new ArrayList<Map<String, String>>();

        ObjectName found = lookupCamelContext(camelContextName);
        if (found != null) {
            J4pExecResponse response = jolokia.execute(new J4pExecRequest(found, "listComponents()"));
            if (response != null) {
                JSONObject data = response.getValue();
                for (Object obj : data.values()) {
                    JSONObject component = (JSONObject) obj;

                    Map<String, String> row = new LinkedHashMap<String, String>();
                    row.put("artifactId", asString(component.get("artifactId")));
                    row.put("title", asString(component.get("title")));
                    row.put("description", asString(component.get("description")));
                    row.put("groupId", asString(component.get("groupId")));
                    row.put("label", asString(component.get("label")));
                    row.put("name", asString(component.get("name")));
                    row.put("status", asString(component.get("status")));
                    row.put("type", asString(component.get("type")));
                    row.put("version", asString(component.get("version")));
                    answer.add(row);
                }
            }

            // sort the list
            Collections.sort(answer, new Comparator<Map<String, String>>() {
                @Override
                public int compare(Map<String, String> component1, Map<String, String> component2) {
                    String name1 = component1.get("name");
                    String name2 = component2.get("name");
                    return name1.compareTo(name2);
                }
            });
        }

        return answer;
    }

    @Override
    public List<Map<String, String>> getTransformers(String camelContextName) throws Exception {
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        List<Map<String, String>> answer = new ArrayList<Map<String, String>>();

        ObjectName found = lookupCamelContext(camelContextName);
        if (found != null) {
            String pattern = String.format("%s:context=%s,type=services,name=DefaultTransformerRegistry", found.getDomain(), found.getKeyProperty("context"));
            ObjectName on = ObjectName.getInstance(pattern);

            J4pExecResponse response = jolokia.execute(new J4pExecRequest(on, "listTransformers()"));
            if (response != null) {
                JSONObject data = response.getValue();
                if (data != null) {
                    for (Object obj : data.values()) {
                        JSONObject data2 = (JSONObject) obj;
                        JSONObject service = (JSONObject) data2.values().iterator().next();

                        Map<String, String> row = new LinkedHashMap<String, String>();
                        row.put("scheme", asString(service.get("scheme")));
                        row.put("from", asString(service.get("from")));
                        row.put("to", asString(service.get("to")));
                        row.put("static", asString(service.get("static")));
                        row.put("dynamic", asString(service.get("dynamic")));
                        row.put("description", asString(service.get("description")));
                        answer.add(row);
                    }
                }
            }

            // sort the list
            Collections.sort(answer, new Comparator<Map<String, String>>() {
                @Override
                public int compare(Map<String, String> service1, Map<String, String> service2) {
                    String scheme1 = service1.get("scheme");
                    String scheme2 = service2.get("scheme");
                    if (scheme1 != null && scheme2 != null) {
                        return scheme1.compareTo(scheme2);
                    } else if (scheme1 != null) {
                        return -1;
                    } else if (scheme2 != null) {
                        return 1;
                    } else {
                        String from1 = service1.get("from");
                        String from2 = service2.get("from");
                        if (from1.equals(from2)) {
                            String to1 = service1.get("to");
                            String to2 = service2.get("to");
                            return to1.compareTo(to2);
                        }
                        return from1.compareTo(from2);
                    }
                }
            });
        }
        return answer;
    }

    @Override
    public List<Map<String, String>> getValidators(String camelContextName) throws Exception {
        if (jolokia == null) {
            throw new IllegalStateException("Need to connect to remote jolokia first");
        }

        List<Map<String, String>> answer = new ArrayList<Map<String, String>>();

        ObjectName found = lookupCamelContext(camelContextName);
        if (found != null) {
            String pattern = String.format("%s:context=%s,type=services,name=DefaultValidatorRegistry", found.getDomain(), found.getKeyProperty("context"));
            ObjectName on = ObjectName.getInstance(pattern);

            J4pExecResponse response = jolokia.execute(new J4pExecRequest(on, "listValidators()"));
            if (response != null) {
                JSONObject data = response.getValue();
                if (data != null) {
                    for (Object obj : data.values()) {
                        JSONObject data2 = (JSONObject) obj;
                        JSONObject service = (JSONObject) data2.values().iterator().next();

                        Map<String, String> row = new LinkedHashMap<String, String>();
                        row.put("type", asString(service.get("type")));
                        row.put("static", asString(service.get("static")));
                        row.put("dynamic", asString(service.get("dynamic")));
                        row.put("description", asString(service.get("description")));
                        answer.add(row);
                    }
                }
            }

            // sort the list
            Collections.sort(answer, new Comparator<Map<String, String>>() {
                @Override
                public int compare(Map<String, String> service1, Map<String, String> service2) {
                    String type1 = service1.get("type");
                    String type2 = service2.get("type");
                    return type1.compareTo(type2);
                }
            });
        }
        return answer;
    }

    private static String asKey(String attributeKey) {
        char ch = Character.toLowerCase(attributeKey.charAt(0));
        return ch + attributeKey.substring(1);
    }

    private static String asString(Object basePath) {
        if (basePath == null) {
            return null;
        } else {
            return basePath.toString();
        }
    }

    private static String removeLeadingAndEndingQuotes(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }

        String copy = s.trim();
        if (copy.startsWith("'") && copy.endsWith("'")) {
            return copy.substring(1, copy.length() - 1);
        }
        if (copy.startsWith("\"") && copy.endsWith("\"")) {
            return copy.substring(1, copy.length() - 1);
        }

        // no quotes, so return as-is
        return s;
    }

}
