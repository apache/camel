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

package org.apache.camel.impl.console;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.spi.BrowsableEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "browse", description = "Browse pending messages on Camel components")
public class BrowseDevConsole extends AbstractDevConsole {

    public BrowseDevConsole() {
        super("camel", "browse", "Browse", "Browse pending messages on Camel components");
    }

    /**
     * Filters the endpoints matching by route id, endpoint url
     */
    public static final String FILTER = "filter";

    /**
     * Limits the number of entries per endpoint
     */
    public static final String LIMIT = "limit";

    /**
     * To receive N last messages from the tail
     */
    public static final String TAIL = "tail";

    /**
     * Whether to include message dumps
     */
    public static final String DUMP = "dump";

    /**
     * Whether to include message body in dumps
     */
    public static final String INCLUDE_BODY = "includeBody";

    /**
     * Whether to calculate fresh queue size (can cause performance overhead)
     */
    public static final String FRESH_SIZE = "freshSize";

    /**
     * Maximum size of the message body to include in the dump
     */
    public static final String BODY_MAX_CHARS = "bodyMaxChars";

    @Metadata(defaultValue = "32768", description = "Maximum size of the message body to include in the dump")
    private int bodyMaxChars = 32 * 1024;

    @Metadata(defaultValue = "100", description = "Maximum number of messages per endpoint to include in the dump")
    private int limit = 100;

    public int getBodyMaxChars() {
        return bodyMaxChars;
    }

    public void setBodyMaxChars(int bodyMaxChars) {
        this.bodyMaxChars = bodyMaxChars;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        String filter = (String) options.get(FILTER);
        String lim = (String) options.get(LIMIT);
        String tail = (String) options.get(TAIL);
        final int pos = tail == null ? 0 : Integer.parseInt(tail);
        final int max = lim == null ? limit : Integer.parseInt(lim);
        boolean freshSize = "true".equals(options.getOrDefault(FRESH_SIZE, "false"));
        boolean dump = "true".equals(options.getOrDefault(DUMP, "true"));
        boolean includeBody = "true".equals(options.getOrDefault(INCLUDE_BODY, "true"));
        int maxChars = Integer.parseInt((String) options.getOrDefault(BODY_MAX_CHARS, "" + bodyMaxChars));

        Collection<Endpoint> endpoints = new TreeSet<>(Comparator.comparing(Endpoint::getEndpointUri));
        endpoints.addAll(getCamelContext().getEndpoints());
        for (Endpoint endpoint : endpoints) {
            if (endpoint instanceof BrowsableEndpoint be
                    && (filter == null || PatternHelper.matchPattern(endpoint.getEndpointUri(), filter))) {

                if (dump) {
                    List<Exchange> list =
                            freshSize ? be.getExchanges(Integer.MAX_VALUE, null) : be.getExchanges(max, null);
                    int queueSize = list != null ? list.size() : 0;
                    int begin = 0;
                    if (list != null && pos > 0) {
                        begin = Math.max(0, list.size() - pos);
                        list = list.subList(begin, list.size());
                    }
                    if (list != null) {
                        sb.append("\n");
                        sb.append(String.format(
                                "Browse: %s (size: %d limit: %d position: %d)%n",
                                endpoint.getEndpointUri(), queueSize, max, begin));
                        for (Exchange e : list) {
                            String json = MessageHelper.dumpAsJSon(
                                    e.getMessage(), false, false, includeBody, 2, true, true, true, maxChars, true);
                            sb.append(json);
                            sb.append("\n");
                        }
                    }
                } else {
                    BrowsableEndpoint.BrowseStatus status = be.getBrowseStatus(Integer.MAX_VALUE);
                    sb.append(String.format("Browse: %s (size: %d%n", endpoint.getEndpointUri(), status.size()));
                }
            }
        }
        sb.append("\n");

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();

        String filter = (String) options.get(FILTER);
        String lim = (String) options.get(LIMIT);
        String tail = (String) options.get(TAIL);
        final int pos = tail == null ? 0 : Integer.parseInt(tail);
        final int max = lim == null ? limit : Integer.parseInt(lim);
        boolean freshSize = "true".equals(options.getOrDefault(FRESH_SIZE, "false"));
        boolean dump = "true".equals(options.getOrDefault(DUMP, "true"));
        boolean includeBody = "true".equals(options.getOrDefault(INCLUDE_BODY, "true"));
        int maxChars = Integer.parseInt((String) options.getOrDefault(BODY_MAX_CHARS, "" + bodyMaxChars));

        Collection<Endpoint> endpoints = new TreeSet<>(Comparator.comparing(Endpoint::getEndpointUri));
        endpoints.addAll(getCamelContext().getEndpoints());
        for (Endpoint endpoint : endpoints) {
            if (endpoint instanceof BrowsableEndpoint be
                    && (filter == null || PatternHelper.matchPattern(endpoint.getEndpointUri(), filter))) {
                if (dump) {
                    List<Exchange> list =
                            freshSize ? be.getExchanges(Integer.MAX_VALUE, null) : be.getExchanges(max, null);
                    int queueSize = list != null ? list.size() : 0;
                    int begin = 0;
                    if (list != null && pos > 0) {
                        begin = Math.max(0, list.size() - pos);
                        list = list.subList(begin, list.size());
                    }
                    if (list != null) {
                        JsonObject jo = new JsonObject();
                        jo.put("endpointUri", endpoint.getEndpointUri());
                        jo.put("queueSize", queueSize);
                        jo.put("limit", max);
                        jo.put("position", begin);
                        if (!list.isEmpty()) {
                            long ts = list.get(0).getMessage().getHeader(Exchange.MESSAGE_TIMESTAMP, 0L, long.class);
                            if (ts > 0) {
                                jo.put("firstTimestamp", ts);
                            }
                            if (list.size() > 1) {
                                ts = list.get(list.size() - 1)
                                        .getMessage()
                                        .getHeader(Exchange.MESSAGE_TIMESTAMP, 0L, long.class);
                                if (ts > 0) {
                                    jo.put("lastTimestamp", ts);
                                }
                            }
                        }
                        arr.add(jo);
                        JsonArray arr2 = new JsonArray();
                        for (Exchange e : list) {
                            arr2.add(MessageHelper.dumpAsJSonObject(
                                    e.getMessage(), false, false, includeBody, true, true, true, maxChars));
                        }
                        if (!arr2.isEmpty()) {
                            jo.put("messages", arr2);
                        }
                    }
                } else {
                    BrowsableEndpoint.BrowseStatus status = be.getBrowseStatus(Integer.MAX_VALUE);
                    JsonObject jo = new JsonObject();
                    jo.put("endpointUri", endpoint.getEndpointUri());
                    jo.put("queueSize", status.size());
                    if (status.firstTimestamp() > 0) {
                        jo.put("firstTimestamp", status.firstTimestamp());
                    }
                    if (status.lastTimestamp() > 0) {
                        jo.put("lastTimestamp", status.lastTimestamp());
                    }
                    arr.add(jo);
                }
            }
        }
        if (!arr.isEmpty()) {
            root.put("browse", arr);
        }

        return root;
    }
}
