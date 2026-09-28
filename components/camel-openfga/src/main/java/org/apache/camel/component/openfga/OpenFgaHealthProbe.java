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
package org.apache.camel.component.openfga;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;

import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

/**
 * The OpenFGA readiness probe, shared by the producer health check and the one on {@code OpenFgaSecurityPolicy}.
 * <p/>
 * Both ask the same question of the same server, so the probe lives in one place: a fix here - a changed timeout, a new
 * failure mode to report - applies to both rather than to whichever was remembered.
 */
public final class OpenFgaHealthProbe {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    /**
     * What a healthy OpenFGA reports. Its {@code /healthz} endpoint is the gRPC health service behind an HTTP gateway,
     * and it answers {@code {"status":"SERVING"}}; a 200 alone is not enough to conclude the server is serving.
     * <p/>
     * Matched as a whole value rather than by searching for the word: the other states the health service can report
     * include {@code NOT_SERVING} and {@code SERVICE_UNKNOWN}, and a plain substring search for {@code SERVING} finds
     * {@code NOT_SERVING} too - reporting an unhealthy server as ready.
     */
    private static final Pattern SERVING = Pattern.compile("\"status\"\\s*:\\s*\"SERVING\"");

    /**
     * Shared across every OpenFGA health check in the JVM. {@link HttpClient} only became {@link AutoCloseable} in Java
     * 21, so on the Java 17 baseline one client per check would leak its selector thread with no way to shut it down.
     * The client is immutable and thread-safe, so sharing is free; the per-request URL and token live on the
     * {@link HttpRequest}.
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    /**
     * One client per distinct TLS configuration, because an {@link SSLContext} can only be set when the client is
     * built. A probe that ignored it would fail its handshake against the very server the authorization call reaches
     * happily - reporting DOWN, and with it an application that never becomes ready.
     * <p/>
     * Keyed on identity and never evicted, which is bounded in practice: the key is an {@code SSLContext} built from an
     * endpoint's {@code sslContextParameters}, and a deployment has one or two of those, not one per exchange.
     */
    private static final Map<SSLContext, HttpClient> TLS_CLIENTS = new ConcurrentHashMap<>();

    private OpenFgaHealthProbe() {
    }

    private static HttpClient clientFor(SSLContext sslContext) {
        if (sslContext == null) {
            return HTTP_CLIENT;
        }
        return TLS_CLIENTS.computeIfAbsent(sslContext,
                ctx -> HttpClient.newBuilder().connectTimeout(TIMEOUT).sslContext(ctx).build());
    }

    /**
     * Probes the OpenFGA server's health endpoint and records the outcome on the builder.
     * <p/>
     * An unreachable server, a server answering with an error, and a server answering that it is not serving are
     * reported differently, so an outage is never mistaken for a denial.
     *
     * @param builder    the result to populate
     * @param apiUrl     base URL of the OpenFGA HTTP API
     * @param apiToken   token for OpenFGA API authentication, or null when the server does not require one
     * @param storeId    the store this check is reporting for, recorded as a detail
     * @param sslContext the TLS configuration the authorization call uses, or null for the JVM default
     */
    public static void probe(
            HealthCheckResultBuilder builder, String apiUrl, String apiToken, String storeId, SSLContext sslContext) {
        builder.detail("openfga.apiUrl", URISupport.sanitizeUri(apiUrl));
        if (ObjectHelper.isNotEmpty(storeId)) {
            builder.detail("openfga.storeId", storeId);
        }

        HttpRequest.Builder request = HttpRequest.newBuilder()
                // an apiUrl with a trailing slash would build //healthz, which the gateway's router answers with a
                // redirect the client is not configured to follow - reporting a healthy server DOWN on HTTP 301
                .uri(URI.create(FileUtil.stripTrailingSeparator(apiUrl) + "/healthz"))
                .timeout(TIMEOUT)
                .GET();
        if (ObjectHelper.isNotEmpty(apiToken)) {
            request.header("Authorization", "Bearer " + apiToken);
        }

        try {
            HttpResponse<String> response
                    = clientFor(sslContext).send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                builder.down();
                builder.message("OpenFGA answered its health endpoint with HTTP " + response.statusCode());
                builder.detail("openfga.statusCode", response.statusCode());
            } else if (response.body() == null || !SERVING.matcher(response.body()).find()) {
                // the gateway can answer 200 while the service behind it reports something other than SERVING, so the
                // body is what decides; taking the status alone would report such a server ready
                builder.down();
                builder.message("OpenFGA reported that it is not serving");
            } else {
                builder.up();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            builder.down();
            builder.message("Interrupted while checking the OpenFGA server");
            builder.error(e);
        } catch (Exception e) {
            builder.down();
            builder.message("Cannot reach the OpenFGA server: " + e.getMessage());
            builder.error(e);
        }
    }
}
