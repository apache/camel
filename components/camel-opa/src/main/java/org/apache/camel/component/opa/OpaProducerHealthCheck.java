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
package org.apache.camel.component.opa;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.apache.camel.util.ObjectHelper;

/**
 * Readiness check for the OPA server a producer sends its decisions to.
 * <p/>
 * The component fails closed, so an OPA server that cannot be reached fails every exchange through the route. This
 * check probes the server's {@code /health} endpoint so that an unavailable policy decision point is visible before
 * traffic starts failing, rather than only in the error logs afterwards.
 */
public class OpaProducerHealthCheck extends AbstractHealthCheck {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    // java.net.http.HttpClient only became AutoCloseable in Java 21 (JEP 480); on Camel's Java 17 baseline there
    // is no way to shut down its internal executor/selector threads, so a per-instance client would leak a thread
    // pool on every producer start. Share a single client across all checks - the per-request URL and bearer token
    // are set on the HttpRequest, so nothing endpoint-specific needs to live on the client.
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    private final String serverUrl;
    private final String bearerToken;
    private final String policyPath;

    public OpaProducerHealthCheck(String serverUrl, String bearerToken, String policyPath, String id) {
        super("camel", "producer:opa-" + id);
        this.serverUrl = serverUrl;
        this.bearerToken = bearerToken;
        this.policyPath = policyPath;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        builder.detail("opa.serverUrl", serverUrl);
        builder.detail("opa.policyPath", policyPath);

        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/health"))
                .timeout(TIMEOUT)
                .GET();
        if (ObjectHelper.isNotEmpty(bearerToken)) {
            request.header("Authorization", "Bearer " + bearerToken);
        }

        try {
            HttpResponse<Void> response = HTTP_CLIENT.send(request.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 200) {
                builder.up();
            } else {
                builder.down();
                builder.message("OPA server answered its health endpoint with HTTP " + response.statusCode());
                builder.detail("opa.statusCode", response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            builder.down();
            builder.message("Interrupted while checking the OPA server");
            builder.error(e);
        } catch (Exception e) {
            builder.down();
            builder.message("Cannot reach the OPA server: " + e.getMessage());
            builder.error(e);
        }
    }
}
