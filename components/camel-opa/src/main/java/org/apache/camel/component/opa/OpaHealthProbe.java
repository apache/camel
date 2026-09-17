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
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLContext;

import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.util.FileUtil;
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

    /**
     * One client per distinct TLS configuration, because an {@link SSLContext} can only be set when the client is
     * built. A probe that ignored it would fail its handshake against the very server the decision call reaches happily
     * - reporting DOWN, and with it an application that never becomes ready.
     * <p/>
     * Keyed on identity and never evicted, which is bounded in practice: the key is an {@code SSLContext} built from an
     * endpoint's {@code sslContextParameters}, and a deployment has one or two of those, not one per exchange.
     */
    private static final Map<SSLContext, HttpClient> TLS_CLIENTS = new ConcurrentHashMap<>();

    private OpaHealthProbe() {
    }

    private static HttpClient clientFor(SSLContext sslContext) {
        if (sslContext == null) {
            return HTTP_CLIENT;
        }
        return TLS_CLIENTS.computeIfAbsent(sslContext,
                ctx -> HttpClient.newBuilder().connectTimeout(TIMEOUT).sslContext(ctx).build());
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
     * @param sslContext  the TLS configuration the decision call uses, or null for the JVM default
     */
    public static void probe(
            HealthCheckResultBuilder builder, String serverUrl, String bearerToken, String policyPath,
            SSLContext sslContext) {
        builder.detail("opa.serverUrl", URISupport.sanitizeUri(serverUrl));
        builder.detail("opa.policyPath", policyPath);

        HttpRequest.Builder request = HttpRequest.newBuilder()
                // a serverUrl with a trailing slash would build //health, which OPA's router answers with a
                // redirect the client is not configured to follow - reporting a healthy server DOWN on HTTP 301
                .uri(URI.create(FileUtil.stripTrailingSeparator(serverUrl) + "/health"))
                .timeout(TIMEOUT)
                .GET();
        if (ObjectHelper.isNotEmpty(bearerToken)) {
            request.header("Authorization", "Bearer " + bearerToken);
        }

        try {
            HttpResponse<Void> response = clientFor(sslContext).send(request.build(), HttpResponse.BodyHandlers.discarding());
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
