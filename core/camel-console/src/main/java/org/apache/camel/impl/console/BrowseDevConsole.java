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

import java.util.ArrayList;
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
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "browse", description = "Browse pending messages on Camel components")
public class BrowseDevConsole extends AbstractDevConsole {

    public record BrowseEntry(
            @Metadata(description = "The endpoint URI") String endpointUri,
            @Metadata(description = "Number of messages currently in the queue") int queueSize,
            @Metadata(description = "The maximum number of messages returned (only present when messages were dumped)") Integer limit,
            @Metadata(description = "The starting position of the returned messages (only present when messages were dumped)") Integer position,
            @Metadata(description = "Epoch time in milliseconds of the first returned message (only present when available)") Long firstTimestamp,
            @Metadata(description = "Epoch time in milliseconds of the last returned message (only present when available)") Long lastTimestamp,
            @Metadata(description = "The dumped messages, as opaque JSON objects (only present when messages were dumped and found)") List<Map<String, Object>> messages) {
    }

    public record Response(@Metadata(description = "The browsed endpoints") List<BrowseEntry> browse) {
    }

    public BrowseDevConsole() {
        super("camel", "browse", "Browse", "Browse pending messages on Camel components");
    }

    @Metadata(label = "query", description = "Filters the endpoints matching by route id, endpoint url",
              javaType = "java.lang.String")
    public static final String FILTER = "filter";

    @Metadata(label = "query", description = "Limits the number of entries per endpoint", javaType = "java.lang.Integer",
              defaultValue = "100")
    public static final String LIMIT = "limit";

    @Metadata(label = "query", description = "To receive N last messages from the tail", javaType = "java.lang.Integer")
    public static final String TAIL = "tail";

    @Metadata(label = "query", description = "Whether to include message dumps", javaType = "java.lang.Boolean",
              defaultValue = "true")
    public static final String DUMP = "dump";

    @Metadata(label = "query", description = "Whether to include message body in dumps", javaType = "java.lang.Boolean",
              defaultValue = "true")
    public static final String INCLUDE_BODY = "includeBody";

    @Metadata(label = "query", description = "Whether to calculate fresh queue size (can cause performance overhead)",
              javaType = "java.lang.Boolean", defaultValue = "false")
    public static final String FRESH_SIZE = "freshSize";

    @Metadata(label = "query", description = "Maximum size of the message body to include in the dump",
              javaType = "java.lang.Integer", defaultValue = "32768")
    public static final String BODY_MAX_CHARS = "bodyMaxChars";

    @Metadata(defaultValue = "32768",
              description = "Maximum size of the message body to include in the dump")
    private int bodyMaxChars = 32 * 1024;

    @Metadata(defaultValue = "100",
              description = "Maximum number of messages per endpoint to include in the dump")
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

        String filter = optionString(options, FILTER);
        final int pos = optionInt(options, TAIL, 0);
        final int max = optionInt(options, LIMIT, limit);
        boolean freshSize = optionBoolean(options, FRESH_SIZE, false);
        boolean dump = optionBoolean(options, DUMP, true);
        boolean includeBody = optionBoolean(options, INCLUDE_BODY, true);
        int maxChars = optionInt(options, BODY_MAX_CHARS, bodyMaxChars);

        Collection<Endpoint> endpoints = new TreeSet<>(Comparator.comparing(Endpoint::getEndpointUri));
        endpoints.addAll(getCamelContext().getEndpoints());
        for (Endpoint endpoint : endpoints) {
            if (endpoint instanceof BrowsableEndpoint be
                    && (filter == null || PatternHelper.matchPattern(endpoint.getEndpointUri(), filter))) {

                if (dump) {
                    List<Exchange> list = freshSize ? be.getExchanges(Integer.MAX_VALUE, null) : be.getExchanges(max, null);
                    int queueSize = list != null ? list.size() : 0;
                    int begin = 0;
                    if (list != null && pos > 0) {
                        begin = Math.max(0, list.size() - pos);
                        list = list.subList(begin, list.size());
                    }
                    if (list != null) {
                        sb.append("\n");
                        sb.append(String.format("Browse: %s (size: %d limit: %d position: %d)%n", endpoint.getEndpointUri(),
                                queueSize, max, begin));
                        for (Exchange e : list) {
                            String json
                                    = MessageHelper.dumpAsJSon(e.getMessage(), false, false, includeBody, 2, true, true, true,
                                            maxChars, true);
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
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        List<BrowseEntry> arr = new ArrayList<>();

        String filter = optionString(options, FILTER);
        final int pos = optionInt(options, TAIL, 0);
        final int max = optionInt(options, LIMIT, limit);
        boolean freshSize = optionBoolean(options, FRESH_SIZE, false);
        boolean dump = optionBoolean(options, DUMP, true);
        boolean includeBody = optionBoolean(options, INCLUDE_BODY, true);
        int maxChars = optionInt(options, BODY_MAX_CHARS, bodyMaxChars);

        Collection<Endpoint> endpoints = new TreeSet<>(Comparator.comparing(Endpoint::getEndpointUri));
        endpoints.addAll(getCamelContext().getEndpoints());
        for (Endpoint endpoint : endpoints) {
            if (endpoint instanceof BrowsableEndpoint be
                    && (filter == null || PatternHelper.matchPattern(endpoint.getEndpointUri(), filter))) {
                if (dump) {
                    List<Exchange> list = freshSize ? be.getExchanges(Integer.MAX_VALUE, null) : be.getExchanges(max, null);
                    int queueSize = list != null ? list.size() : 0;
                    int begin = 0;
                    if (list != null && pos > 0) {
                        begin = Math.max(0, list.size() - pos);
                        list = list.subList(begin, list.size());
                    }
                    if (list != null) {
                        Long firstTimestamp = null;
                        Long lastTimestamp = null;
                        if (!list.isEmpty()) {
                            long ts = list.get(0).getMessage().getHeader(Exchange.MESSAGE_TIMESTAMP, 0L, long.class);
                            if (ts > 0) {
                                firstTimestamp = ts;
                            }
                            if (list.size() > 1) {
                                ts = list.get(list.size() - 1).getMessage().getHeader(Exchange.MESSAGE_TIMESTAMP, 0L,
                                        long.class);
                                if (ts > 0) {
                                    lastTimestamp = ts;
                                }
                            }
                        }
                        List<Map<String, Object>> messages = new ArrayList<>();
                        for (Exchange e : list) {
                            messages.add(MessageHelper.dumpAsJSonObject(e.getMessage(), false, false, includeBody, true, true,
                                    true, maxChars));
                        }
                        arr.add(new BrowseEntry(
                                endpoint.getEndpointUri(), queueSize, max, begin, firstTimestamp, lastTimestamp,
                                messages.isEmpty() ? null : messages));
                    }
                } else {
                    BrowsableEndpoint.BrowseStatus status = be.getBrowseStatus(Integer.MAX_VALUE);
                    Long firstTimestamp = status.firstTimestamp() > 0 ? status.firstTimestamp() : null;
                    Long lastTimestamp = status.lastTimestamp() > 0 ? status.lastTimestamp() : null;
                    arr.add(new BrowseEntry(
                            endpoint.getEndpointUri(), status.size(), null, null, firstTimestamp, lastTimestamp, null));
                }
            }
        }

        Response response = new Response(arr.isEmpty() ? null : arr);
        return JsonRecordSupport.toJsonObject(response);
    }

}
