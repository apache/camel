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

package org.apache.camel.test.infra.azure.eventgrid.clients;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import com.azure.json.JsonProviders;
import com.azure.json.JsonReader;
import com.azure.json.JsonWriter;

/**
 * A client of the Event Grid management (Azure Resource Manager) API of Floci AZ, which needs no authentication: it
 * creates custom topics, reads their access keys and subscribes webhooks to them.
 */
public class AzureEventGridManagementClient {
    private static final String API_VERSION = "2025-02-15";
    private static final String CLOUD_EVENT_SCHEMA = "CloudEventSchemaV1_0";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final String endpoint;

    /**
     * @param endpoint the address of Floci AZ, for example {@code http://localhost:4577}
     */
    public AzureEventGridManagementClient(String endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * Creates a custom topic that takes events in the CloudEvents 1.0 schema
     *
     * @param topicId the resource id of the topic:
     *                {@code /subscriptions/<id>/resourceGroups/<group>/providers/Microsoft.EventGrid/topics/<name>}
     */
    public void createCloudEventTopic(String topicId) {
        send("PUT", topicId, Map.of(
                "location", "eastus",
                "properties", Map.of("inputSchema", CLOUD_EVENT_SCHEMA)));
    }

    /**
     * The first access key of a topic
     */
    public String topicAccessKey(String topicId) {
        Map<String, Object> keys = send("POST", topicId + "/listKeys", null);
        Object key = keys.get("key1");
        if (!(key instanceof String)) {
            throw new IllegalStateException("No key1 in the keys of the topic " + topicId + ": " + keys.keySet());
        }
        return (String) key;
    }

    /**
     * Subscribes a webhook to a topic. Event Grid delivers the events to it in the CloudEvents 1.0 schema, after the
     * validation handshake: an {@code OPTIONS} request with the {@code WebHook-Request-Origin} header, to which the
     * webhook answers with the {@code WebHook-Allowed-Origin} header.
     *
     * @param topicId     the resource id of the topic
     * @param name        the name of the event subscription
     * @param endpointUrl the URL of the webhook, which the container of Floci AZ must reach
     */
    public void createWebhookSubscription(String topicId, String name, String endpointUrl) {
        send("PUT", topicId + "/providers/Microsoft.EventGrid/eventSubscriptions/" + name, Map.of(
                "properties", Map.of(
                        "destination", Map.of(
                                "endpointType", "WebHook",
                                "properties", Map.of("endpointUrl", endpointUrl)),
                        "eventDeliverySchema", CLOUD_EVENT_SCHEMA)));
    }

    /**
     * The certificate authority, in PEM, that signs the certificate of Floci AZ when it serves HTTPS
     * ({@code FLOCI_AZ_TLS_ENABLED=true})
     */
    public String tlsCertificate() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint + "/_floci/tls-cert"))
                .timeout(TIMEOUT)
                .GET()
                .build();
        return execute(request);
    }

    private Map<String, Object> send(String method, String resourceId, Map<String, Object> body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint + resourceId + "?api-version=" + API_VERSION))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .method(method, body != null
                        ? HttpRequest.BodyPublishers.ofString(toJson(body))
                        : HttpRequest.BodyPublishers.noBody())
                .build();
        return fromJson(execute(request));
    }

    private String execute(HttpRequest request) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(
                        request.method() + " " + request.uri() + " failed with " + response.statusCode() + ": "
                                                + response.body());
            }
            return response.body();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling " + request.uri(), e);
        }
    }

    private static String toJson(Map<String, Object> body) {
        StringWriter json = new StringWriter();
        try (JsonWriter writer = JsonProviders.createWriter(json)) {
            writer.writeUntyped(body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return json.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fromJson(String json) {
        try (JsonReader reader = JsonProviders.createReader(json)) {
            Object value = reader.readUntyped();
            if (!(value instanceof Map)) {
                throw new IllegalStateException("Not a JSON object: " + json);
            }
            return (Map<String, Object>) value;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
