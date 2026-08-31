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
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.camel.spi.BacklogTracer;
import org.apache.camel.spi.BacklogTracerEventMessage;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "trace", displayName = "Camel Tracing", description = "Trace routed messages", readOnly = false)
@Configurer(extended = true)
public class TraceDevConsole extends AbstractDevConsole {

    public record Response(
            @Metadata(description = "Whether tracing is enabled (only present when a backlog tracer is available)") Boolean enabled,
            @Metadata(description = "The traced messages, as opaque JSON objects (only present when dumping)") List<Map<String, Object>> traces,
            @Metadata(description = "Whether the tracer is in standby mode (only present when not dumping)") Boolean standby,
            @Metadata(description = "Total number of traced messages (only present when not dumping)") Long counter,
            @Metadata(description = "The current backlog size (only present when not dumping)") Integer backlogSize,
            @Metadata(description = "The current queue size (only present when not dumping)") Long queueSize,
            @Metadata(description = "Whether messages are removed from the backlog when dumped (only present when not dumping)") Boolean removeOnDump,
            @Metadata(description = "The trace filter (only present when configured)") String traceFilter,
            @Metadata(description = "The trace pattern (only present when configured)") String tracePattern,
            @Metadata(description = "Whether rests are traced (only present when not dumping)") Boolean traceRests,
            @Metadata(description = "Whether route templates/Kamelets are traced (only present when not dumping)") Boolean traceTemplates,
            @Metadata(description = "Maximum size of the message body to include (only present when not dumping)") Integer bodyMaxChars,
            @Metadata(description = "Whether file-based message bodies are included (only present when not dumping)") Boolean bodyIncludeFiles,
            @Metadata(description = "Whether streaming message bodies are included (only present when not dumping)") Boolean bodyIncludeStreams,
            @Metadata(description = "Whether exchange properties are included (only present when not dumping)") Boolean includeExchangeProperties,
            @Metadata(description = "Whether exchange variables are included (only present when not dumping)") Boolean includeExchangeVariables,
            @Metadata(description = "Whether exceptions are included (only present when not dumping)") Boolean includeException) {
    }

    @Metadata(defaultValue = "100",
              description = "Maximum capacity of last number of messages to capture (capacity must be between 50 and 1000)")
    private int capacity = 100;

    @Metadata(label = "query", description = "Whether to enable or disable tracing",
              javaType = "java.lang.String", enums = "true,false")
    public static final String ENABLED = "enabled";

    @Metadata(label = "query", description = "Whether to dump trace messages",
              javaType = "java.lang.String", enums = "true,false")
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
        String enabled = optionString(options, ENABLED);
        String dump = optionString(options, DUMP);

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

    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        String enabled = optionString(options, ENABLED);
        String dump = optionString(options, DUMP);

        Response response = new Response(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        BacklogTracer tracer = getCamelContext().getCamelContextExtension().getContextPlugin(BacklogTracer.class);
        if (tracer != null) {
            if (dump != null) {
                for (BacklogTracerEventMessage t : tracer.dumpAllTracedMessages()) {
                    addMessage(t);
                }
                List<Map<String, Object>> traces = new ArrayList<>();
                for (BacklogTracerEventMessage t : queue) {
                    traces.add((JsonObject) t.asJSon());
                }
                response = new Response(
                        tracer.isEnabled(), traces, null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null);
            } else {
                if ("true".equals(enabled)) {
                    tracer.setEnabled(true);
                } else if ("false".equals(enabled)) {
                    tracer.setEnabled(false);
                }
                response = new Response(
                        tracer.isEnabled(), null, tracer.isStandby(), tracer.getTraceCounter(),
                        tracer.getBacklogSize(), tracer.getQueueSize(), tracer.isRemoveOnDump(),
                        tracer.getTraceFilter(), tracer.getTracePattern(), tracer.isTraceRests(),
                        tracer.isTraceTemplates(), tracer.getBodyMaxChars(), tracer.isBodyIncludeFiles(),
                        tracer.isBodyIncludeStreams(), tracer.isIncludeExchangeProperties(),
                        tracer.isIncludeExchangeVariables(), tracer.isIncludeException());
            }
        }

        return JsonRecordSupport.toJsonObject(response);
    }

}
