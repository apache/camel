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
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
public final class OpaHealthProbe implements AutoCloseable {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final SSLContext sslContext;
    private HttpClient client;
    private boolean closed;

    public OpaHealthProbe(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    private synchronized HttpClient client() {
        if (closed) {
            throw new IllegalStateException("The health probe is closed");
        }
        if (client == null) {
            HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(TIMEOUT);
            if (sslContext != null) {
                builder.sslContext(sslContext);
            }
            client = builder.build();
        }
        return client;
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (client != null) {
            client.shutdownNow();
            client = null;
        }
    }

    /**
     * The client this probe holds, without creating one. For the tests of the probe's lifecycle.
     */
    synchronized HttpClient currentClient() {
        return client;
    }

    /**
     * Sends the request and waits for the whole response, body included, for at most {@link #TIMEOUT}. The timeout of
     * the request itself only covers the wait for the response headers, so a server that stalls while sending the body
     * would otherwise hold the thread that runs the health check for as long as it likes.
     */
    private <T> HttpResponse<T> sendBounded(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws Exception {
        CompletableFuture<HttpResponse<T>> call = client().sendAsync(request, handler);
        try {
            return call.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            call.cancel(true);
            throw new HttpTimeoutException("Timed out waiting for the OPA health endpoint after " + TIMEOUT);
        } catch (InterruptedException e) {
            call.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
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
        try (OpaHealthProbe probe = new OpaHealthProbe(sslContext)) {
            probe.probe(builder, serverUrl, bearerToken, policyPath);
        }
    }

    public void probe(HealthCheckResultBuilder builder, String serverUrl, String bearerToken, String policyPath) {
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
            HttpResponse<Void> response = sendBounded(request.build(), HttpResponse.BodyHandlers.discarding());
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
