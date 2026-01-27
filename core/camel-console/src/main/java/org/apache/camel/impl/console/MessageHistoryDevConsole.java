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

import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.camel.Route;
import org.apache.camel.spi.BacklogTracer;
import org.apache.camel.spi.BacklogTracerEventMessage;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

@DevConsole(name = "message-history", displayName = "Message History", description = "History of latest completed exchange")
@Configurer(extended = true)
public class MessageHistoryDevConsole extends AbstractDevConsole {

    public static final String CODE_LIMIT = "codeLimit";

    public MessageHistoryDevConsole() {
        super("camel", "message-history", "Message History", "History of latest completed exchange");
    }

    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        BacklogTracer tracer = getCamelContext().getCamelContextExtension().getContextPlugin(BacklogTracer.class);
        if (tracer != null) {
            Collection<BacklogTracerEventMessage> queue = tracer.getLatestMessageHistory();
            for (BacklogTracerEventMessage t : queue) {
                String json = t.toJSon(0);
                sb.append(json).append("\n");
            }
        }

        return sb.toString();
    }

    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        int limit = Integer.parseInt((String) options.getOrDefault(CODE_LIMIT, "5"));

        BacklogTracer tracer = getCamelContext().getCamelContextExtension().getContextPlugin(BacklogTracer.class);
        if (tracer == null) {
            return root;
        }

        JsonArray arr = new JsonArray();
        Collection<BacklogTracerEventMessage> queue = tracer.getLatestMessageHistory();
        for (BacklogTracerEventMessage t : queue) {
            arr.add(processTraceMessage(t, limit));
        }
        root.put("name", getCamelContext().getName());
        root.put("traces", arr);

        return root;
    }

    private JsonObject processTraceMessage(BacklogTracerEventMessage t, int codeLimit) {
        JsonObject to = (JsonObject) t.asJSon();

        if (codeLimit > 0) {
            addSourceCodeToTrace(to, codeLimit);
        }

        return to;
    }

    private void addSourceCodeToTrace(JsonObject to, int limit) {
        String rid = to.getString("routeId");
        String loc = to.getString("location");
        if (rid == null) {
            return;
        }

        List<JsonObject> code = enrichSourceCode(rid, loc, limit);
        if (code != null && !code.isEmpty()) {
            to.put("code", code);
        }
    }

    private List<JsonObject> enrichSourceCode(String routeId, String location, int lines) {
        Route route = getCamelContext().getRoute(routeId);
        if (route == null) {
            return null;
        }
        Resource resource = route.getSourceResource();
        if (resource == null) {
            return null;
        }

        List<JsonObject> code = new ArrayList<>();

        location = StringHelper.afterLast(location, ":");
        int line = 0;
        try {
            if (location != null) {
                line = Integer.parseInt(location);
            }
            LineNumberReader reader = new LineNumberReader(resource.getReader());
            for (int i = 1; i <= line + lines; i++) {
                String t = reader.readLine();
                if (t != null) {
                    int low = line - lines + 2; // grab more of the following code than previous code (+2)
                    int high = line + lines + 1 + 2;
                    if (i >= low && i <= high) {
                        JsonObject c = new JsonObject();
                        c.put("line", i);
                        if (line == i) {
                            c.put("match", true);
                        }
                        c.put("code", Jsoner.escape(t));
                        code.add(c);
                    }
                }
            }
            IOHelper.close(reader);
        } catch (Exception e) {
            // ignore
        }

        return code;
    }

}
