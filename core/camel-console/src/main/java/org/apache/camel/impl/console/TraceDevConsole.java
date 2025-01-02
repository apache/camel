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

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.camel.spi.BacklogTracer;
import org.apache.camel.spi.BacklogTracerEventMessage;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "trace", displayName = "Camel Tracing", description = "Trace routed messages")
@Configurer(extended = true)
public class TraceDevConsole extends AbstractDevConsole {

    @Metadata(defaultValue = "100",
              description = "Maximum capacity of last number of messages to capture (capacity must be between 50 and 1000)")
    private int capacity = 100;

    /**
     * Whether to enable or disable tracing
     */
    public static final String ENABLED = "enabled";

    /**
     * Whether to dump trace messages
     */
    public static final String DUMP = "dump";

    private Queue<BacklogTracerEventMessage> queue;

    public TraceDevConsole() {
        super("camel", "trace", "Camel Tracing", "Trace routed messages");
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    @Override
    protected void doInit() throws Exception {
        if (capacity > 1000 || capacity < 50) {
            throw new IllegalArgumentException("Capacity must be between 50 and 1000");
        }
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();
        String enabled = (String) options.get(ENABLED);
        String dump = (String) options.get(DUMP);

        BacklogTracer tracer = getCamelContext().getCamelContextExtension().getContextPlugin(BacklogTracer.class);
        if (tracer != null) {
            if (dump != null) {
                for (BacklogTracerEventMessage t : tracer.dumpAllTracedMessages()) {
                    addMessage(t);
                }
                for (BacklogTracerEventMessage t : queue) {
                    String json = t.toJSon(0);
                    sb.append(json).append("\n");
                }
            } else {
                if ("true".equals(enabled)) {
                    tracer.setEnabled(true);
                } else if ("false".equals(enabled)) {
                    tracer.setEnabled(false);
                }
                sb.append("Enabled: ").append(tracer.isEnabled()).append("\n");
                sb.append("Standby: ").append(tracer.isStandby()).append("\n");
                sb.append("Trace Counter: ").append(tracer.getTraceCounter()).append("\n");
                sb.append("Backlog Size: ").append(tracer.getBacklogSize()).append("\n");
                sb.append("Queue Size: ").append(tracer.getQueueSize()).append("\n");
                sb.append("Remove On Dump: ").append(tracer.isRemoveOnDump()).append("\n");
                if (tracer.getTraceFilter() != null) {
                    sb.append("Trace Filter: ").append(tracer.getTraceFilter()).append("\n");
                }
                if (tracer.getTracePattern() != null) {
                    sb.append("Trace Pattern: ").append(tracer.getTracePattern()).append("\n");
                }
                sb.append("Trace Rests: ").append(tracer.isTraceRests()).append("\n");
                sb.append("Trace Templates: ").append(tracer.isTraceTemplates()).append("\n");
                sb.append("Body Max Chars: ").append(tracer.getBodyMaxChars()).append("\n");
                sb.append("Body Include Files: ").append(tracer.isBodyIncludeFiles()).append("\n");
                sb.append("Body Include Streams: ").append(tracer.isBodyIncludeStreams()).append("\n");
                sb.append("Include Exchange Properties: ").append(tracer.isIncludeExchangeProperties()).append("\n");
                sb.append("Include Exchange Variables: ").append(tracer.isIncludeExchangeVariables()).append("\n");
                sb.append("Include Exception: ").append(tracer.isIncludeException()).append("\n");
            }
        }

        return sb.toString();
    }

    private void addMessage(BacklogTracerEventMessage message) {
        // ensure there is space on the queue by polling until at least single slot is free
        int drain = queue.size() - capacity + 1;
        if (drain > 0) {
            for (int i = 0; i < drain; i++) {
                queue.poll();
            }
        }
        queue.add(message);
    }

    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();
        String enabled = (String) options.get(ENABLED);
        String dump = (String) options.get(DUMP);

        BacklogTracer tracer = getCamelContext().getCamelContextExtension().getContextPlugin(BacklogTracer.class);
        if (tracer != null) {
            if (dump != null) {
                for (BacklogTracerEventMessage t : tracer.dumpAllTracedMessages()) {
                    addMessage(t);
                }
                JsonArray arr = new JsonArray();
                root.put("enabled", tracer.isEnabled());
                root.put("traces", arr);
                for (BacklogTracerEventMessage t : queue) {
                    JsonObject jo = (JsonObject) t.asJSon();
                    arr.add(jo);
                }
            } else {
                if ("true".equals(enabled)) {
                    tracer.setEnabled(true);
                } else if ("false".equals(enabled)) {
                    tracer.setEnabled(false);
                }
                root.put("enabled", tracer.isEnabled());
                root.put("standby", tracer.isStandby());
                root.put("counter", tracer.getTraceCounter());
                root.put("backlogSize", tracer.getBacklogSize());
                root.put("queueSize", tracer.getQueueSize());
                root.put("removeOnDump", tracer.isRemoveOnDump());
                if (tracer.getTraceFilter() != null) {
                    root.put("traceFilter", tracer.getTraceFilter());
                }
                if (tracer.getTracePattern() != null) {
                    root.put("tracePattern", tracer.getTracePattern());
                }
                root.put("traceRests", tracer.isTraceRests());
                root.put("traceTemplates", tracer.isTraceTemplates());
                root.put("bodyMaxChars", tracer.getBodyMaxChars());
                root.put("bodyIncludeFiles", tracer.isBodyIncludeFiles());
                root.put("bodyIncludeStreams", tracer.isBodyIncludeStreams());
                root.put("includeExchangeProperties", tracer.isIncludeExchangeProperties());
                root.put("includeExchangeVariables", tracer.isIncludeExchangeVariables());
                root.put("includeException", tracer.isIncludeException());
            }
        }

        return root;
    }

}
