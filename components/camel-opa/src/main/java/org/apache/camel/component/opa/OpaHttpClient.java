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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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

import com.styra.opa.openapi.utils.HTTPClient;
import org.apache.camel.util.ObjectHelper;

/**
 * The HTTP transport the OPA SDK uses to reach the server.
 * <p/>
 * Supplied rather than left to the SDK, whose default {@code SpeakeasyHTTPClient} is a one-liner around
 * {@code HttpClient.newHttpClient()} with two consequences a policy decision point cannot afford:
 * <ul>
 * <li><b>Nothing bounds the call.</b> That factory sets no connect timeout and the SDK sets no request timeout, so both
 * are the JDK default of "wait indefinitely". A server that accepts the connection and then goes quiet parks the
 * calling thread for ever - and a component that fails closed never reaches the point of denying, it simply stops.
 * {@code failOpen} does not help, because it sits downstream of a call that never returns.</li>
 * <li><b>It builds a client per request.</b> Each {@link HttpClient} holds its selector thread and executor until it is
 * closed or collected - once per message, on the path an {@code OpaSecurityPolicy} guards.</li>
 * </ul>
 * One client is built here per evaluator and reused, and every request is re-issued carrying a timeout. The response
 * body is buffered rather than streamed with {@code ofInputStream}: the timeout of a request only covers the wait for
 * the response headers, so the whole response, body included, is awaited under {@code requestTimeout} here, and a
 * server that stalls or drips its body is cut off like one that never answers.
 */
public class OpaHttpClient implements HTTPClient, AutoCloseable {

    private static final String AUTHORIZATION = "Authorization";

    private final HttpClient client;
    private final Duration requestTimeout;
    private final String bearerToken;

    OpaHttpClient(long connectionTimeout, long requestTimeout, SSLContext sslContext, String bearerToken) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectionTimeout));
        if (sslContext != null) {
            builder.sslContext(sslContext);
        }
        this.client = builder.build();
        this.requestTimeout = Duration.ofMillis(requestTimeout);
        // the SDK has no constructor taking a transport and headers together, so the token is applied here
        // instead of through OPAClient(String, Map) - the request that goes out is the same either way
        // isNotEmpty, not a null check: an unset placeholder resolves to "", and sending
        // "Authorization: Bearer " is worse than sending nothing at all
        this.bearerToken = ObjectHelper.isNotEmpty(bearerToken) ? bearerToken : null;
    }

    @Override
    public void close() throws Exception {
        if (client instanceof AutoCloseable c) {
            c.close();
        }
    }

    @Override
    public HttpResponse<InputStream> send(HttpRequest request) throws IOException, InterruptedException {
        // the SDK hands us a finished request, and HttpRequest is immutable - rebuilding it is the only way to
        // attach a timeout. The filter keeps every header the SDK set, dropping only an Authorization we are
        // about to replace, because header() appends rather than overwrites
        HttpRequest.Builder builder = HttpRequest
                .newBuilder(request, (name, value) -> bearerToken == null || !AUTHORIZATION.equalsIgnoreCase(name))
                .timeout(requestTimeout);
        if (bearerToken != null) {
            builder.header(AUTHORIZATION, "Bearer " + bearerToken);
        }
        // A streaming body handler completes as soon as headers arrive, leaving the SDK's subsequent body read
        // outside HttpRequest.timeout(). Complete the body before returning its stream and bound that entire wait.
        CompletableFuture<HttpResponse<InputStream>> response = client.sendAsync(builder.build(),
                info -> HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofByteArray(),
                        bytes -> new ByteArrayInputStream(bytes)));
        try {
            return response.get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            response.cancel(true);
            throw new HttpTimeoutException("Timed out waiting for the complete OPA response after " + requestTimeout);
        } catch (InterruptedException e) {
            response.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error error) {
                // an OutOfMemoryError from buffering the body is not an HTTP failure; keep it what it is
                throw error;
            }
            if (cause instanceof IOException io) {
                throw io;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IOException("OPA HTTP request failed", cause);
        }
    }
}
