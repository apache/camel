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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.zip.GZIPInputStream;

import javax.xml.stream.XMLStreamException;

import org.apache.camel.spi.ExecutorServiceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends the requests of the MTConnect HTTP binding to one agent and turns its responses into documents or classified
 * failures: an MTConnect error document is recognised by its root element whatever the HTTP status, an HTTP error
 * status without one is reported with its status code, and a response the agent stops sending for the silence bound is
 * closed and reported as unreachable: the read timeout for a document, two heartbeats for a part of the sample stream.
 * Requests may run on several threads at once. The reader owns the HTTP client and the threads it reads with:
 * {@link #open} creates them and {@link #close()} releases them.
 */
final class AgentReader implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(AgentReader.class);

    private final ExecutorServiceManager manager;
    private final ScheduledExecutorService liveness;
    private final ExecutorService httpExecutor;
    private final AgentHttpClient client;
    private final DocumentParser parser = new DocumentParser();
    private final ReadDeadline documentDeadline;
    private final Set<AgentHttpClient.Response> open = ConcurrentHashMap.newKeySet();

    private AgentReader(ExecutorServiceManager manager, ScheduledExecutorService liveness, ExecutorService httpExecutor,
                        AgentHttpClient client, long readTimeout) {
        this.manager = manager;
        this.liveness = liveness;
        this.httpExecutor = httpExecutor;
        this.client = client;
        this.documentDeadline = new ReadDeadline(liveness, readTimeout);
    }

    /**
     * Creates a reader of the endpoint's agent with its own threads from Camel's executor service manager, named after
     * the given source.
     */
    static AgentReader open(MTConnectEndpoint endpoint, Object source)
            throws GeneralSecurityException, IOException {
        ExecutorServiceManager manager = endpoint.getCamelContext().getExecutorServiceManager();
        ScheduledExecutorService liveness = manager.newSingleThreadScheduledExecutor(source, "MTConnectLiveness");
        ExecutorService httpExecutor = manager.newCachedThreadPool(source, "MTConnectHttpClient");
        try {
            return new AgentReader(
                    manager, liveness, httpExecutor, endpoint.createClient(httpExecutor),
                    endpoint.getReadTimeout());
        } catch (GeneralSecurityException | IOException | RuntimeException e) {
            manager.shutdownNow(liveness);
            manager.shutdownNow(httpExecutor);
            throw e;
        }
    }

    /** The probe request: the agent's device model. */
    Answer probe() throws AgentFailure, InterruptedException {
        return read("probe", client.probe());
    }

    /** The current request, at a sequence the agent still buffers when at is not null. */
    Answer current(Long at) throws AgentFailure, InterruptedException {
        return read("current", client.current(at));
    }

    /** A sample request the agent answers with one document; null parameters are left to the agent. */
    Answer sample(Long from, Long to, Long count) throws AgentFailure, InterruptedException {
        return read("sample", client.sample(from, to, count));
    }

    /**
     * Opens the streaming sample request from the given sequence. A stream without a part for two heartbeats counts as
     * unreachable.
     */
    SampleStream stream(long from, long count, long interval, long heartbeat)
            throws AgentFailure, InterruptedException {
        // a stream is not compressed: the client reads it part by part
        AgentHttpClient.Response response = send(client.sample(from, count, interval, heartbeat), false);
        String boundary = response.status() == 200 ? MultipartReader.boundary(response.contentType()) : null;
        long silence = heartbeat > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : 2 * heartbeat;
        return new SampleStream(
                response, boundary == null ? null : new MultipartReader(response.body(), boundary),
                new ReadDeadline(liveness, silence));
    }

    /** The assets request for the endpoint's device; null parameters are left to the agent. */
    Answer assets(String type, Long count, boolean removed) throws AgentFailure, InterruptedException {
        return read("assets", client.assets(type, count, removed));
    }

    /** The asset request for one or more asset ids. */
    Answer asset(List<String> ids) throws AgentFailure, InterruptedException {
        return read("asset", client.asset(ids));
    }

    /**
     * Cancels the requests waiting for their answer and closes every response being read, from another thread, so that
     * the reading threads return at once.
     */
    void abort() {
        client.cancel();
        open.forEach(AgentReader::closeQuietly);
    }

    /** Closes the HTTP client and stops the reader's threads; call {@link #abort()} first to end the reads at once. */
    @Override
    public void close() throws Exception {
        try {
            client.close();
        } finally {
            manager.shutdownNow(liveness);
            manager.shutdownGraceful(httpExecutor);
        }
    }

    /**
     * Sends a request whose answer is one document, asking for gzip compression, and returns the decoded body with the
     * parsed document.
     */
    private Answer read(String request, URI uri) throws AgentFailure, InterruptedException {
        AgentHttpClient.Response response = send(uri, true);
        try {
            byte[] body = body(response);
            return new Answer(request, body, parse(response.status(), body));
        } finally {
            release(response);
        }
    }

    private AgentHttpClient.Response send(URI uri, boolean compressed) throws AgentFailure, InterruptedException {
        LOG.debug("GET {}", uri);
        try {
            AgentHttpClient.Response response = client.send(uri, compressed);
            open.add(response);
            if (client.cancelled()) {
                // abort() may have run between the answer and its registration, when it could not close this response
                release(response);
                throw new IOException("The request was cancelled");
            }
            return response;
        } catch (IOException e) {
            throw new AgentFailure(AgentFailure.Kind.UNREACHABLE, describe(e), e);
        }
    }

    /**
     * Reads a whole response body, decoded when the agent compressed it; the silence bound starts again whenever data
     * arrives.
     */
    private byte[] body(AgentHttpClient.Response response) throws AgentFailure {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        try (ReadDeadline.Watch watch = documentDeadline.watch(response)) {
            // the gzip header is read here, within the silence bound
            try (InputStream in = response.gzip() ? new GZIPInputStream(response.body()) : response.body()) {
                int read;
                while ((read = in.read(chunk)) != -1) {
                    watch.touch();
                    body.write(chunk, 0, read);
                }
            } catch (IOException e) {
                throw unreachable(watch, e);
            }
        }
        return body.toByteArray();
    }

    private AgentDocument parse(int status, byte[] body) throws AgentFailure {
        AgentDocument document;
        try {
            document = parser.parse(body);
        } catch (XMLStreamException e) {
            String message = status != 200 ? "HTTP " + status : "Unreadable agent document: " + e.getMessage();
            throw new AgentFailure(AgentFailure.Kind.ERROR, message, e);
        }
        if (status != 200 && !(document instanceof AgentDocument.Error)) {
            throw new AgentFailure(AgentFailure.Kind.ERROR, "HTTP " + status, null);
        }
        return document;
    }

    private AgentFailure unreachable(ReadDeadline.Watch watch, IOException e) {
        String message = watch.expired()
                ? "No data from the agent within " + watch.bound() + " ms"
                : describe(e);
        return new AgentFailure(AgentFailure.Kind.UNREACHABLE, message, e);
    }

    private void release(AgentHttpClient.Response response) {
        open.remove(response);
        closeQuietly(response);
    }

    private static String describe(IOException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    private static void closeQuietly(AgentHttpClient.Response response) {
        try {
            response.close();
        } catch (IOException e) {
            LOG.debug("Closing an agent response failed", e);
        }
    }

    /**
     * A whole answer: the request it answers (probe, current, sample, assets or asset), the body as the agent sent it
     * and the document parsed from it.
     */
    record Answer(String request, byte[] body, AgentDocument document) {

        /**
         * The document as the type the request is answered with.
         *
         * @throws AgentFailure when the agent answered with another document
         */
        <T extends AgentDocument> T expect(Class<T> type) throws AgentFailure {
            if (!type.isInstance(document)) {
                throw new AgentFailure(
                        AgentFailure.Kind.ERROR, "The agent answered the " + request + " request with a "
                                                 + document.getClass().getSimpleName() + " document",
                        null);
            }
            return type.cast(document);
        }
    }

    /** The open streaming sample response, read part by part. */
    final class SampleStream implements AutoCloseable {

        private final AgentHttpClient.Response response;
        private final MultipartReader parts;
        private final ReadDeadline partDeadline;
        private boolean single;

        private SampleStream(AgentHttpClient.Response response, MultipartReader parts, ReadDeadline partDeadline) {
            this.response = response;
            this.parts = parts;
            this.partDeadline = partDeadline;
        }

        /**
         * The next part's answer, or null when the agent ended the stream. An answer that is not a multipart stream can
         * only be an error document.
         */
        Answer next() throws AgentFailure {
            if (parts == null) {
                if (single) {
                    return null;
                }
                single = true;
                byte[] body = body(response);
                AgentDocument document = parse(response.status(), body);
                if (!(document instanceof AgentDocument.Error)) {
                    throw new AgentFailure(
                            AgentFailure.Kind.ERROR,
                            "The agent answered the sample request without streaming; read it with streaming=false",
                            null);
                }
                return new Answer("sample", body, document);
            }
            byte[] part;
            try (ReadDeadline.Watch watch = partDeadline.watch(response)) {
                try {
                    part = parts.nextPart();
                } catch (IOException e) {
                    throw unreachable(watch, e);
                }
            }
            return part == null ? null : new Answer("sample", part, parse(200, part));
        }

        @Override
        public void close() {
            release(response);
        }
    }
}
