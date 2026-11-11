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
package org.apache.camel.component.mtconnect;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLContext;

import org.apache.camel.util.IOHelper;

/**
 * Sends the probe, current, sample and asset requests of the MTConnect HTTP binding to one agent and hands back the
 * response body as a stream, so that a streaming sample response is read part by part while it arrives. Requests ask
 * for XML. Requests may be sent from several threads at once.
 */
final class AgentHttpClient implements AutoCloseable {

    private final String address;
    private final String device;
    private final String base;
    private final String path;
    private final Duration readTimeout;
    private final HttpClient client;
    private final Set<CompletableFuture<HttpResponse<InputStream>>> inFlight = ConcurrentHashMap.newKeySet();
    private volatile boolean cancelled;

    /**
     * @param agentUrl       the agent address, with an optional base path
     * @param device         the device name or uuid, or null for every device
     * @param path           the XPath filter for current and sample requests, or null
     * @param connectTimeout the bound for establishing the connection
     * @param readTimeout    the bound for the response headers after the request
     * @param sslContext     the TLS context for https agents, or null for the runtime default
     * @param executor       the executor for the client's asynchronous tasks
     */
    AgentHttpClient(String agentUrl, String device, String path, Duration connectTimeout, Duration readTimeout,
                    SSLContext sslContext, Executor executor) {
        this.address = agentUrl.endsWith("/") ? agentUrl.substring(0, agentUrl.length() - 1) : agentUrl;
        this.device = device == null || device.isEmpty() ? null : device;
        this.base = this.device == null ? address : address + "/" + encode(this.device);
        this.path = path == null || path.isEmpty() ? null : path;
        this.readTimeout = readTimeout;
        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(connectTimeout)
                .executor(executor);
        if (sslContext != null) {
            builder.sslContext(sslContext);
        }
        this.client = builder.build();
    }

    URI probe() {
        return URI.create(base + "/probe");
    }

    /** The current request, at a sequence the agent still buffers when at is not null. */
    URI current(Long at) {
        Query query = new Query();
        query.add("at", at);
        query.add("path", path);
        return URI.create(base + "/current" + query);
    }

    /** The streaming sample request. */
    URI sample(long from, long count, long interval, long heartbeat) {
        Query query = new Query();
        query.add("from", from);
        query.add("count", count);
        query.add("interval", interval);
        query.add("heartbeat", heartbeat);
        query.add("path", path);
        return URI.create(base + "/sample" + query);
    }

    /** A sample request the agent answers with one document; null parameters are left to the agent. */
    URI sample(Long from, Long to, Long count) {
        Query query = new Query();
        query.add("from", from);
        query.add("to", to);
        query.add("count", count);
        query.add("path", path);
        return URI.create(base + "/sample" + query);
    }

    /**
     * The assets request, with the device as a query parameter: Mazak 1.4 answers /<device>/assets with no assets but
     * /assets?device=<device> with all of them. Null parameters are left to the agent.
     */
    URI assets(String type, Long count, boolean removed) {
        Query query = new Query();
        query.add("device", device);
        query.add("type", type);
        query.add("count", count);
        query.add("removed", removed ? "true" : null);
        return URI.create(address + "/assets" + query);
    }

    /** The asset request for one or more asset ids, joined with the standard's separator. */
    URI asset(List<String> ids) {
        StringJoiner joined = new StringJoiner(";");
        ids.forEach(id -> joined.add(encode(id)));
        return URI.create(address + "/asset/" + joined);
    }

    /**
     * Sends a GET request and returns once the response headers have arrived.
     *
     * @param  compressed  whether to ask for a gzip-compressed answer; not for a stream, which the client reads part by
     *                     part
     * @throws IOException when the connection fails, no response headers arrive within the read timeout, or the request
     *                     was cancelled
     */
    Response send(URI uri, boolean compressed) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(readTimeout)
                .header("Accept", "application/xml")
                .GET();
        if (compressed) {
            request.header("Accept-Encoding", "gzip");
        }
        CompletableFuture<HttpResponse<InputStream>> future
                = client.sendAsync(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        inFlight.add(future);
        if (cancelled) {
            future.cancel(true);
        }
        HttpResponse<InputStream> response;
        try {
            response = future.get();
        } catch (InterruptedException e) {
            // nobody will read the answer: end the request, or close the body of an answer that arrived meanwhile
            future.cancel(true);
            future.thenAccept(arrived -> IOHelper.close(arrived.body()));
            throw e;
        } catch (CancellationException e) {
            throw new IOException("The request was cancelled", e);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof IOException cause ? cause : new IOException(e.getCause());
        } finally {
            inFlight.remove(future);
        }
        // buffered, because the multipart reader reads part headers and boundaries byte by byte
        return new Response(
                response.statusCode(), response.headers().firstValue("Content-Type").orElse(null),
                response.headers().firstValue("Content-Encoding").orElse(null), new BufferedInputStream(response.body()));
    }

    /**
     * Cancels the requests waiting for their response headers, and every later one, so that a consumer that stops does
     * not wait up to the read timeout for an agent that sends nothing. Nothing is lost by not waiting: an MTConnect
     * request holds no session or other state on the agent that an orderly close would release.
     */
    void cancel() {
        cancelled = true;
        inFlight.forEach(future -> future.cancel(true));
    }

    /** Whether {@link #cancel()} was called. */
    boolean cancelled() {
        return cancelled;
    }

    /** Shuts the client down and waits for its requests to end, which also ends its selector thread. */
    @Override
    public void close() {
        client.close();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** A query string of the parameters that have a value. */
    private static final class Query {

        private final StringJoiner joiner = new StringJoiner("&", "?", "").setEmptyValue("");

        void add(String name, Object value) {
            if (value != null) {
                joiner.add(name + "=" + encode(value.toString()));
            }
        }

        @Override
        public String toString() {
            return joiner.toString();
        }
    }

    /**
     * An agent response whose body is read while it arrives, as sent: a gzip-compressed body is decoded by its reader.
     * Closing it releases the connection.
     */
    record Response(int status, String contentType, String contentEncoding, InputStream body) implements Closeable {

        boolean gzip() {
            return contentEncoding != null && contentEncoding.trim().equalsIgnoreCase("gzip");
        }

        @Override
        public void close() throws IOException {
            body.close();
        }
    }
}
