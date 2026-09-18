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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.ExceptionHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.JsonRecordSupport;
import org.apache.camel.util.json.Jsoner;

@DevConsole(name = "receive", displayName = "Camel Receive", description = "Consume messages from endpoints",
            readOnly = false)
@Configurer(extended = true)
public class ReceiveDevConsole extends AbstractDevConsole {

    public record EndpointEntry(
            @Metadata(description = "The endpoint URI") String uri,
            @Metadata(description = "Whether the endpoint is remote") boolean remote) {
    }

    public record Response(
            @Metadata(description = "The dumped received messages, as opaque JSON objects (only present when dumping)") List<Map<String, Object>> messages,
            @Metadata(description = "Whether receiving is enabled (only present when not dumping)") Boolean enabled,
            @Metadata(description = "Total number of messages received so far (only present when not dumping)") Long total,
            @Metadata(description = "Epoch time in milliseconds of the first message in the last dump (only present when not dumping)") Long firstTimestamp,
            @Metadata(description = "Epoch time in milliseconds of the last message in the last dump (only present when not dumping)") Long lastTimestamp,
            @Metadata(description = "The endpoint URI that receiving was started for (only present on success)") String url,
            @Metadata(description = "The error message (only present on failure to start receiving)") String error,
            @Metadata(description = "The error stack trace, one entry per line (only present on failure to start receiving)") List<String> stackTrace,
            @Metadata(description = "The active consumer endpoints (only present when not dumping and there are any)") List<EndpointEntry> endpoints) {
    }

    @Metadata(defaultValue = "100",
              description = "Maximum capacity of last number of messages to capture (capacity must be between 50 and 1000)")
    private int capacity = 100;
    @Metadata(defaultValue = "32768", label = "advanced",
              description = "To limit the message body to a maximum size in the received message. Use 0 or negative value to use unlimited size.")
    private int bodyMaxChars = 32 * 1024;
    @Metadata(defaultValue = "true", label = "advanced",
              description = "Whether all received messages should be removed when dumping. By default, the messages are removed, which means that dumping will not contain previous dumped messages.")
    private boolean removeOnDump = true;

    @Metadata(label = "query", description = "Whether to enable or disable receive mode",
              javaType = "java.lang.String", enums = "true,false")
    public static final String ENABLED = "enabled";

    @Metadata(label = "query", description = "Whether to dump received messages",
              javaType = "java.lang.String", enums = "true,false")
    public static final String DUMP = "dump";

    @Metadata(label = "query",
              description = "Endpoint for where to receive messages (can also refer to a route id, endpoint pattern)",
              javaType = "java.lang.String")
    public static final String ENDPOINT = "endpoint";

    private final List<Consumer> consumers = new ArrayList<>();
    private final AtomicBoolean enabled = new AtomicBoolean();
    private final AtomicLong uuid = new AtomicLong();
    private Queue<JsonObject> queue;
    private long firstTimestamp;
    private long lastTimestamp;

    public ReceiveDevConsole() {
        super("camel", "receive", "Camel Receive", "Consume messages from endpoints");
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getBodyMaxChars() {
        return bodyMaxChars;
    }

    public void setBodyMaxChars(int bodyMaxChars) {
        this.bodyMaxChars = bodyMaxChars;
    }

    public boolean isRemoveOnDump() {
        return removeOnDump;
    }

    public void setRemoveOnDump(boolean removeOnDump) {
        this.removeOnDump = removeOnDump;
    }

    @Override
    protected void doInit() throws Exception {
        if (capacity > 1000 || capacity < 50) {
            throw new IllegalArgumentException("Capacity must be between 50 and 1000");
        }
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    @Override
    protected void doStop() throws Exception {
        stopConsumers();
    }

    protected void stopConsumers() {
        for (Consumer c : consumers) {
            ServiceHelper.stopAndShutdownServices(c);
        }
        consumers.clear();
    }

    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        String dump = optionString(options, DUMP);
        if ("true".equals(dump)) {
            JsonArray arr = new JsonArray();
            arr.addAll(queue);
            if (removeOnDump) {
                queue.clear();
            }
            JsonObject jo = (JsonObject) arr.get(0);
            firstTimestamp = jo.getLongOrDefault("timestamp", 0);
            jo = (JsonObject) arr.get(arr.size() - 1);
            lastTimestamp = jo.getLongOrDefault("timestamp", 0);
            String json = arr.toJson();
            sb.append(json).append("\n");
            return sb.toString();
        }

        String enabled = optionString(options, ENABLED);
        if ("false".equals(enabled)) {
            // turn off all consumers
            stopConsumers();
            this.enabled.set(false);
            sb.append("Enabled: ").append("false").append("\n");
            return sb.toString();
        }

        String pattern = optionString(options, ENDPOINT);
        if (pattern != null) {
            try {
                Endpoint target = findMatchingEndpoint(getCamelContext(), pattern);
                if (target != null) {
                    sb.append("Starting to receive messages from: ").append(target.getEndpointUri());
                    Consumer consumer = createConsumer(target);
                    if (!consumers.contains(consumer)) {
                        consumers.add(consumer);
                        ServiceHelper.startService(consumer);
                    }
                }
                this.enabled.set(true);
            } catch (Exception e) {
                sb.append("Error starting to receive messages due to: ").append(e.getMessage());
            }
        }

        sb.append("Enabled: ").append(this.enabled.get()).append("\n");
        sb.append("Total: ").append(this.uuid.get()).append("\n");
        for (Consumer c : consumers) {
            sb.append("    ").append(c.getEndpoint().toString()).append("\n");
        }
        return sb.toString();
    }

    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        String dump = optionString(options, DUMP);
        if ("true".equals(dump)) {
            List<Map<String, Object>> messages = new ArrayList<>(queue);
            if (removeOnDump) {
                queue.clear();
            }
            JsonObject first = (JsonObject) messages.get(0);
            firstTimestamp = first.getLongOrDefault("timestamp", 0);
            JsonObject last = (JsonObject) messages.get(messages.size() - 1);
            lastTimestamp = last.getLongOrDefault("timestamp", 0);

            Response response = new Response(messages, null, null, null, null, null, null, null, null);
            return JsonRecordSupport.toJsonObject(response);
        }

        String enabledOpt = optionString(options, ENABLED);
        if ("false".equals(enabledOpt)) {
            // turn off all consumers
            stopConsumers();
            this.enabled.set(false);
            Response response = new Response(null, false, null, null, null, null, null, null, null);
            return JsonRecordSupport.toJsonObject(response);
        }

        String url = null;
        String error = null;
        List<String> stackTrace = null;

        String pattern = optionString(options, ENDPOINT);
        if (pattern != null) {
            try {
                Endpoint target = findMatchingEndpoint(getCamelContext(), pattern);
                if (target != null) {
                    url = target.getEndpointUri();
                    Consumer consumer = createConsumer(target);
                    if (!consumers.contains(consumer)) {
                        consumers.add(consumer);
                        ServiceHelper.startService(consumer);
                    }
                }
                this.enabled.set(true);
            } catch (Exception e) {
                error = Jsoner.escape(e.getMessage());
                final String trace = ExceptionHelper.stackTraceToString(e);
                stackTrace = Arrays.asList(trace.split("\n"));
            }
        }

        List<EndpointEntry> endpoints = null;
        if (!consumers.isEmpty()) {
            endpoints = new ArrayList<>();
            for (Consumer c : consumers) {
                endpoints.add(new EndpointEntry(c.getEndpoint().toString(), c.getEndpoint().isRemote()));
            }
        }

        Response response = new Response(
                null, this.enabled.get(), uuid.get(), firstTimestamp, lastTimestamp, url, error, stackTrace, endpoints);
        return JsonRecordSupport.toJsonObject(response);
    }

    private Consumer createConsumer(Endpoint target) throws Exception {
        for (Consumer c : consumers) {
            if (c.getEndpoint() == target) {
                return c;
            }
        }
        return target.createConsumer(this::addMessage);
    }

    private void addMessage(Exchange exchange) {
        JsonObject json
                = MessageHelper.dumpAsJSonObject(exchange.getMessage(), true, true, true, true, true, true, bodyMaxChars);
        json.put("uid", uuid.incrementAndGet());
        json.put("endpointUri", exchange.getFromEndpoint().toString());
        json.put("remoteEndpoint", exchange.getFromEndpoint().isRemote());
        lastTimestamp = exchange.getMessage().getMessageTimestamp();
        json.put("timestamp", lastTimestamp);

        // ensure there is space on the queue by polling until at least single slot is free
        int drain = queue.size() - capacity + 1;
        if (drain > 0) {
            for (int i = 0; i < drain; i++) {
                queue.poll();
            }
        }
        queue.add(json);
    }

    protected static Endpoint findMatchingEndpoint(CamelContext camelContext, String endpoint) {
        Endpoint target = null;
        boolean scheme = endpoint.contains(":");
        boolean pattern = endpoint.endsWith("*");
        if (!scheme || pattern) {
            if (!scheme && !endpoint.endsWith("*")) {
                endpoint = endpoint + "*";
            }
            // find all producers for this camel context via JMX mbeans (this allows to find also producers created via dynamic EIPs)
            MBeanServer mbeanServer = camelContext.getManagementStrategy().getManagementAgent().getMBeanServer();
            if (mbeanServer != null) {
                try {
                    String jmxDomain
                            = camelContext.getManagementStrategy().getManagementAgent().getMBeanObjectDomainName();
                    String prefix
                            = camelContext.getManagementStrategy().getManagementAgent().getIncludeHostName() ? "*/" : "";
                    ObjectName query = ObjectName.getInstance(
                            jmxDomain + ":context=" + prefix + camelContext.getManagementName() + ",type=producers,*");
                    Set<ObjectName> set = mbeanServer.queryNames(query, null);
                    if (set != null && !set.isEmpty()) {
                        for (ObjectName on : set) {
                            String uri = (String) mbeanServer.getAttribute(on, "EndpointUri");
                            if (PatternHelper.matchPattern(uri, endpoint)) {
                                // is the endpoint able to create a consumer
                                target = camelContext.getEndpoint(uri);
                                // is the target able to create a consumer
                                UriEndpoint ann
                                        = ObjectHelper.getAnnotationDeep(target, UriEndpoint.class);
                                if (ann != null) {
                                    if (ann.producerOnly()) {
                                        // skip if the endpoint cannot consume (we need to be able to consume to receive)
                                        target = null;
                                    }
                                    if ("*".equals(endpoint) && !ann.remote()) {
                                        // skip internal when matching everything
                                        target = null;
                                    }
                                }
                                if (target != null) {
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        } else {
            target = camelContext.getEndpoint(endpoint);
            // is the target able to create a consumer
            UriEndpoint ann
                    = ObjectHelper.getAnnotationDeep(target, UriEndpoint.class);
            if (ann != null) {
                if (ann.producerOnly()) {
                    // skip if the endpoint cannot consume (we need to be able to consume to receive)
                    throw new IllegalArgumentException("Cannot consume from endpoint: " + endpoint);
                }
            }
        }
        return target;
    }

}
