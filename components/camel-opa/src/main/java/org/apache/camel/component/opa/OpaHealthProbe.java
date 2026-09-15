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

import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

/**
 * The OPA readiness probe, shared by the producer health check and the one on {@code OpaSecurityPolicy}.
 * <p/>
 * Both ask the same question of the same endpoint, so the probe lives in one place: a fix here - a changed timeout, a
 * new failure mode to report - applies to both rather than to whichever was remembered.
 */
public final class OpaHealthProbe {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    /**
     * Shared across every OPA health check in the JVM. {@link HttpClient} only became {@link AutoCloseable} in Java 21,
     * so on the Java 17 baseline one client per check would leak its selector thread with no way to shut it down. The
     * client is immutable and thread-safe, so sharing is free; the per-request URL and token live on the
     * {@link HttpRequest}.
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    private OpaHealthProbe() {
    }

    /**
     * Probes the OPA server's health endpoint and records the outcome on the builder.
     * <p/>
     * An unreachable server and a server answering with an error are reported differently, so an outage is never
     * mistaken for a policy that denied.
     *
     * @param builder     the result to populate
     * @param serverUrl   base URL of the OPA server, without the /v1/data suffix
     * @param bearerToken token for OPA API authentication, or null when OPA does not require one
     * @param policyPath  the policy this check is reporting for, recorded as a detail
     */
    public static void probe(
            HealthCheckResultBuilder builder, String serverUrl, String bearerToken, String policyPath) {
        builder.detail("opa.serverUrl", URISupport.sanitizeUri(serverUrl));
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
