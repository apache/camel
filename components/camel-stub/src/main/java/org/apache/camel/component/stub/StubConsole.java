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
package org.apache.camel.component.stub;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "stub", description = "Browse messages on stub endpoints")
public class StubConsole extends AbstractDevConsole {

    @Metadata(label = "query", description = "Filters the routes matching by queue name",
              javaType = "java.lang.String")
    public static final String FILTER = "filter";

    @Metadata(label = "query", description = "Limits the number of messages dumped",
              javaType = "java.lang.Integer")
    public static final String LIMIT = "limit";

    @Metadata(label = "query", description = "To use either xml or json output format",
              javaType = "java.lang.String", enums = "xml,json")
    public static final String FORMAT = "format";

    @Metadata(label = "query", description = "Whether to browse messages", defaultValue = "false",
              javaType = "java.lang.Boolean")
    public static final String BROWSE = "browse";

    public StubConsole() {
        super("camel", "stub", "Stub", "Browse messages on stub endpoints");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        String filter = optionString(options, FILTER);
        final int max = optionInt(options, LIMIT, Integer.MAX_VALUE);
        final boolean dump = optionBoolean(options, BROWSE, false);

        StringBuilder sb = new StringBuilder();

        List<StubEndpoint> list = getCamelContext().getEndpoints()
                .stream()
                .filter(e -> e instanceof StubEndpoint)
                .map(StubEndpoint.class::cast)
                .filter(e -> accept(e.getName(), filter))
                .toList();

        Set<String> names = new HashSet<>();
        for (StubEndpoint se : list) {
            String name = se.getName();
            if (names.contains(name)) {
                // queue may be shared between endpoints so only print once
                continue;
            } else {
                names.add(name);
            }

            sb.append(String.format("Queue: %s (max: %d, size: %d)%n", name, se.getSize(), se.getCurrentQueueSize()));

            if (dump) {
                Queue<Exchange> q = se.getQueue();
                List<Exchange> copy = new ArrayList<>(q);
                if (max > 0 && q.size() > max) {
                    int pos = q.size() - 1 - max;
                    int end = q.size() - 1;
                    copy = copy.subList(pos, end);
                }
                for (Exchange exchange : copy) {
                    // dump to xml or json
                    try {
                        String format = optionString(options, FORMAT);
                        String msg = null;
                        if (format == null || "xml".equals(format)) {
                            msg = MessageHelper.dumpAsXml(exchange.getMessage(), true, 4);
                        } else if ("json".equals(format)) {
                            msg = MessageHelper.dumpAsJSon(exchange.getMessage(), true, 4);
                        }
                        if (msg != null) {
                            sb.append("\n").append(msg).append("\n");
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
        }

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        String filter = optionString(options, FILTER);
        final int max = optionInt(options, LIMIT, Integer.MAX_VALUE);
        final boolean dump = optionBoolean(options, BROWSE, false);

        JsonObject root = new JsonObject();
        JsonArray queues = new JsonArray();

        List<StubEndpoint> list = getCamelContext().getEndpoints()
                .stream().filter(e -> e instanceof StubEndpoint)
                .map(StubEndpoint.class::cast)
                .filter(e -> accept(e.getName(), filter))
                .toList();

        Set<String> names = new HashSet<>();
        for (StubEndpoint se : list) {
            String name = se.getName();
            if (names.contains(name)) {
                // queue may be shared between endpoints so only print once
                continue;
            } else {
                names.add(name);
            }

            JsonObject jo = new JsonObject();
            jo.put("name", name);
            jo.put("endpointUri", se.getEndpointUri());
            jo.put("max", se.getSize());
            jo.put("size", se.getCurrentQueueSize());

            // browse messages
            if (dump) {
                List<JsonObject> arr = new ArrayList<>();

                Queue<Exchange> q = se.getQueue();
                List<Exchange> copy = new ArrayList<>(q);
                if (max > 0 && q.size() > max) {
                    int pos = q.size() - 1 - max;
                    int end = q.size() - 1;
                    copy = copy.subList(pos, end);
                }
                for (Exchange exchange : copy) {
                    try {
                        JsonObject msg
                                = MessageHelper.dumpAsJSonObject(exchange.getMessage(), false, true, true, true, false, true,
                                        128 * 1024);
                        arr.add(msg);
                    } catch (Exception e) {
                        // ignore
                    }
                }
                if (!arr.isEmpty()) {
                    jo.put("messages", arr);
                }
            }
            queues.add(jo);
        }

        root.put("queues", queues);
        return root;
    }

    private static boolean accept(String name, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }

        return PatternHelper.matchPattern(name, filter);
    }

}
