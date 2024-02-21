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
package org.apache.camel.component.salesforce.internal.streaming;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import jakarta.servlet.http.HttpServletResponse;

import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.AbstractNetworkConnector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class StubServer {

    private static final Logger LOG = LoggerFactory.getLogger(StubServer.class);

    private final List<StubResponse> defaultStubs = new ArrayList<>();

    private final Server server;

    private final List<StubResponse> stubs = new ArrayList<>();

    class StubHandler extends Handler.Abstract {

        private StubResponse stubFor(final Request request, final String body) throws IOException {
            final List<StubResponse> allResponses = new ArrayList<>(defaultStubs);
            allResponses.addAll(stubs);

            for (final StubResponse stub : allResponses) {
                if (stub.matches(request, body)) {
                    return stub;
                }
            }

            return null;
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) throws Exception {
            StringBuilder body = new StringBuilder();
            while (true) {
                Content.Chunk chunk = request.read();
                if (chunk.isLast()) {
                    break;
                }

                byte[] bytes = new byte[chunk.getByteBuffer().remaining()];
                chunk.getByteBuffer().get(bytes);
                String chunkString = new String(bytes, StandardCharsets.UTF_8);
                body.append(chunkString);
            }

            final StubResponse stub = stubFor(request, body.toString());

            if (stub == null) {
                LOG.error("Stub not found for {} {}", request.getMethod(), request.getHttpURI().getPath());
                Response.writeError(request, response, callback, HttpServletResponse.SC_NOT_IMPLEMENTED);
                callback.succeeded();
                return false;
            }

            response.setStatus(stub.responseStatus);
            response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");

            final String id = messageIdFrom(body.toString());

            stub.writeTo(id, response, callback);

            callback.succeeded();
            return true;
        }
    }

    final class StubResponse {

        private Predicate<String> requestCondition;

        private final String requestMethod;

        private final String requestPath;

        private BlockingQueue<String> responseMessages;

        private final int responseStatus;

        private String responseString;

        public StubResponse(final String requestMethod, final String requestPath, final int responseStatus,
                            final Predicate<String> requestCondition,
                            final BlockingQueue<String> responseMessages) {
            this(requestMethod, requestPath, responseStatus, requestCondition);

            this.responseMessages = responseMessages;
        }

        private StubResponse(final String requestMethod, final String requestPath, final int responseStatus) {
            this.responseStatus = responseStatus;
            this.requestMethod = Objects.requireNonNull(requestMethod, "requestMethod");
            this.requestPath = Objects.requireNonNull(requestPath, "requestPath");
        }

        private StubResponse(final String requestMethod, final String requestPath, final int responseStatus,
                             final BlockingQueue<String> responseMessages) {
            this(requestMethod, requestPath, responseStatus);

            this.responseMessages = responseMessages;
        }

        private StubResponse(final String requestMethod, final String requestPath, final int responseStatus,
                             final Predicate<String> requestCondition) {
            this(requestMethod, requestPath, responseStatus);

            this.requestCondition = requestCondition;
        }

        private StubResponse(final String requestMethod, final String requestPath, final int responseStatus,
                             final Predicate<String> requestCondition,
                             final String responseString) {
            this(requestMethod, requestPath, responseStatus, requestCondition);

            this.responseString = responseString;
        }

        private StubResponse(final String requestMethod, final String requestPath, final int responseStatus,
                             final String responseString) {
            this(requestMethod, requestPath, responseStatus);
            this.responseString = responseString;
        }

        @Override
        public String toString() {
            return requestMethod + " " + requestPath;
        }

        private boolean matches(final Request request, final String body) throws IOException {
            final boolean matches = Objects.equals(requestMethod, request.getMethod())
                    && Objects.equals(requestPath, request.getHttpURI().getPath());

            if (!matches) {
                return false;
            }

            if (requestCondition == null) {
                return true;
            }

            return requestCondition.test(body);
        }

        private void writeTo(final String messageId, final Response out, final Callback callback) throws IOException {
            if (responseString != null) {
                out.write(true,
                        ByteBuffer.wrap(responseString.replace("$id", messageId).getBytes(StandardCharsets.UTF_8)),
                        callback);
                return;
            }

            if (responseMessages != null) {
                while (true) {
                    try {
                        final String message = responseMessages.poll(25, TimeUnit.MILLISECONDS);
                        if (message != null) {
                            out.write(true,
                                    ByteBuffer.wrap(message.replace("$id", messageId).getBytes(StandardCharsets.UTF_8)),
                                    callback);
                            return;
                        }

                        if (!server.isRunning()) {
                            return;
                        }
                    } catch (final InterruptedException ignored) {
                        return;
                    }
                }
            }
        }
    }

    public StubServer() {
        server = new Server(0);
        server.setHandler(new StubHandler());

        try {
            server.start();
        } catch (final Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @SuppressWarnings("resource")
    public void abruptlyRestart() {
        final int port = port();

        stop();

        connector().setPort(port);

        try {
            server.start();
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("resource")
    public int port() {
        return connector().getLocalPort();
    }

    public void replyTo(final String method, final String path, final BlockingQueue<String> messages) {
        stubs.add(new StubResponse(method, path, 200, messages));
    }

    public void replyTo(final String method, final String path, final int status) {
        stubs.add(new StubResponse(method, path, status));
    }

    public void replyTo(
            final String method, final String path, final Predicate<String> requestCondition,
            final BlockingQueue<String> messages) {
        stubs.add(new StubResponse(method, path, 200, requestCondition, messages));
    }

    public void replyTo(
            final String method, final String path, final Predicate<String> requestCondition, final String response) {
        stubs.add(new StubResponse(method, path, 200, requestCondition, response));
    }

    public void replyTo(final String method, final String path, final String response) {
        stubs.add(new StubResponse(method, path, 200, response));
    }

    public void reset() {
        stubs.clear();
    }

    public void stop() {
        try {
            for (final EndPoint endPoint : connector().getConnectedEndPoints()) {
                endPoint.close();
            }

            server.stop();
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public void stubsAsDefaults() {
        defaultStubs.addAll(stubs);
        stubs.clear();
    }

    private AbstractNetworkConnector connector() {
        final AbstractNetworkConnector connector = (AbstractNetworkConnector) server.getConnectors()[0];
        return connector;
    }

    private static String messageIdFrom(final String body) {
        int idx = body.indexOf("\"id\":\"");
        String id = "";

        if (idx > 0) {
            idx += 6;
            char ch;
            while (Character.isDigit(ch = body.charAt(idx++))) {
                id += ch;
            }
        }
        return id;
    }

}
