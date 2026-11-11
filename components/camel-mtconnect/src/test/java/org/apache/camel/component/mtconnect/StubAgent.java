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
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

/**
 * An in-process MTConnect agent on the JDK's HTTP server: probe, current and sample requests, streaming sample parts on
 * the requested interval and heartbeat, a sequence counter and a circular buffer, in the shape of a
 * {@link StubDialect}. Knobs reproduce what real agents and links do: pause, refuse, restart, overrun, error answers,
 * closed streams and a sample answer without streaming.
 */
final class StubAgent implements AutoCloseable {

    /** The password of the test key and trust stores under tls/. */
    static final char[] PASSWORD = "changeit".toCharArray();

    /** A request kind the stub can be told to answer with a failure. */
    enum Request {
        PROBE,
        CURRENT,
        SAMPLE,
        ASSETS,
        ASSET
    }

    /** A failure answer: an MTConnect error document with a status, or a proxy's HTML page when the code is null. */
    record Answer(int status, String errorCode) {
        static Answer html(int status) {
            return new Answer(status, null);
        }
    }

    private final StubDialect dialect;
    private final StubDeviceModel model;
    private final StubDocuments documents;
    private final StubAssets assets;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final ExecutorService handlers = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "stub-agent");
        thread.setDaemon(true);
        return thread;
    });
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<String> compressedRequests = new CopyOnWriteArrayList<>();
    private final SSLContext tls;
    // the certificate the TLS context serves; certificate() swaps it for the handshakes from then on
    private volatile X509ExtendedKeyManager keys;
    // the one cipher suite offered to new connections; another one after a certificate change, see certificate()
    private volatile String cipherSuite = "TLS_AES_128_GCM_SHA256";
    private final List<Long> history = new CopyOnWriteArrayList<>();
    private final Deque<StubDocuments.Observation> buffer = new ArrayDeque<>();
    private final Map<String, StubDocuments.Observation> latest = new LinkedHashMap<>();
    private final Map<Request, Answer> answers = new HashMap<>();
    private HttpServer server;
    private final int port;
    private long instanceId = 1793113341;
    private int bufferSize = 256;
    private long nextSequence = 1;
    private int generation;
    private boolean paused;
    private boolean streaming = true;
    private boolean closed;
    private boolean endStreamsAtOnce;
    private boolean closeConnectionAfterStreams;
    private boolean compress;

    private StubAgent(StubDialect dialect, String keyStore) throws IOException {
        this.dialect = dialect;
        this.tls = keyStore == null ? null : tls(keyStore);
        this.model = new StubDeviceModel(dialect.probeResource);
        this.documents = new StubDocuments(dialect);
        this.assets = new StubAssets(dialect.assetsResource);
        locked(() -> {
            reset();
            return null;
        });
        server = listen(0);
        port = server.getAddress().getPort();
    }

    static StubAgent start(StubDialect dialect) throws IOException {
        return new StubAgent(dialect, null);
    }

    /**
     * An agent on https with the test server certificate of tls/server.p12 (localhost and 127.0.0.1), signed by the
     * test certificate authority of tls/truststore.p12.
     */
    static StubAgent startHttps(StubDialect dialect) throws IOException {
        return new StubAgent(dialect, "tls/server.p12");
    }

    /**
     * Serves another certificate on the connections accepted from now on, without a restart: tls/untrusted.p12 is
     * self-signed, so a client that trusts the test certificate authority rejects it. A resumed TLS session would skip
     * the certificate check, so new connections are offered another cipher suite, which no cached session can rejoin:
     * the client makes a full handshake and meets the new certificate.
     */
    void certificate(String keyStore) throws IOException {
        keys = keyManager(keyStore);
        cipherSuite = "TLS_AES_256_GCM_SHA384";
    }

    /** A TLS context whose served certificate is the one {@link #keys} holds at each handshake. */
    private SSLContext tls(String keyStore) throws IOException {
        keys = keyManager(keyStore);
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(new KeyManager[] { new CurrentKeyManager() }, null, null);
            return context;
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    private static X509ExtendedKeyManager keyManager(String resource) throws IOException {
        try (InputStream in = StubAgent.class.getClassLoader().getResourceAsStream(resource)) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(in, PASSWORD);
            KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            factory.init(keyStore, PASSWORD);
            for (KeyManager manager : factory.getKeyManagers()) {
                if (manager instanceof X509ExtendedKeyManager extended) {
                    return extended;
                }
            }
            throw new IOException("No X.509 key manager for " + resource);
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    /** Delegates every choice to the key manager of the moment, so that the served certificate can change. */
    private final class CurrentKeyManager extends X509ExtendedKeyManager {

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return keys.getClientAliases(keyType, issuers);
        }

        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            return keys.chooseClientAlias(keyType, issuers, socket);
        }

        @Override
        public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
            return keys.chooseEngineClientAlias(keyType, issuers, engine);
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return keys.getServerAliases(keyType, issuers);
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return keys.chooseServerAlias(keyType, issuers, socket);
        }

        @Override
        public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
            return keys.chooseEngineServerAlias(keyType, issuers, engine);
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return keys.getCertificateChain(alias);
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return keys.getPrivateKey(alias);
        }
    }

    String url() {
        return (tls == null ? "http" : "https") + "://localhost:" + port;
    }

    StubDeviceModel model() {
        return model;
    }

    /** The request URIs that asked for a gzip-compressed answer, in arrival order. */
    List<String> compressedRequests() {
        return compressedRequests;
    }

    /** The request URIs in arrival order, as sent. */
    List<String> requests() {
        return requests;
    }

    /** Every sequence the agent ever assigned, in order, across restarts. */
    List<Long> history() {
        return history;
    }

    long instanceId() {
        return locked(() -> instanceId);
    }

    long nextSequence() {
        return locked(() -> nextSequence);
    }

    /** Adds a sample or event observation; a null value is UNAVAILABLE. Returns its sequence. */
    long set(String id, String value) {
        return set(id, value, Map.of());
    }

    /** Adds a sample or event observation with further attributes of the observation element, such as statistic. */
    long set(String id, String value, Map<String, String> attributes) {
        return locked(() -> append(id, value, null, null, null, null, null, attributes));
    }

    /** Adds a data set observation. */
    long setEntries(String id, Map<String, String> entries) {
        return add(id, null, entries, null, null, null, null);
    }

    /** Adds a condition observation at the given level, such as Fault. */
    long condition(String id, String level, String nativeCode, String nativeSeverity, String qualifier, String text) {
        return add(id, text, null, level, nativeCode, nativeSeverity, qualifier);
    }

    void bufferSize(int size) {
        locked(() -> bufferSize = size);
    }

    /** Adds an asset and reports AssetChanged for it, as an agent does when an asset is uploaded. */
    void addAsset(String elementXml) {
        locked(() -> assetEvent(assets.add(elementXml), "ASSET_CHANGED"));
    }

    /** Gives an asset a new timestamp and reports AssetChanged for it, as an agent does when the asset is written. */
    void changeAsset(String id) {
        locked(() -> assetEvent(assets.touch(id, Instant.now()), "ASSET_CHANGED"));
    }

    /** Gives an asset a new timestamp without reporting it, as a change lost in an overrun or an outage. */
    void changeAssetSilently(String id) {
        locked(() -> assets.touch(id, Instant.now()));
    }

    /** Marks an asset removed without reporting it, as a removal lost in an overrun or an outage. */
    void removeAssetSilently(String id) {
        locked(() -> assets.remove(id, Instant.now()));
    }

    /** Reports AssetChanged for an asset that did not change. */
    void announceAsset(String id) {
        locked(() -> assetEvent(assets.get(id), "ASSET_CHANGED"));
    }

    /** Marks an asset removed with a new timestamp and reports AssetRemoved, as an agent does on a delete. */
    void removeAsset(String id) {
        locked(() -> assetEvent(assets.remove(id, Instant.now()), "ASSET_REMOVED"));
    }

    /** Drops an asset and reports AssetRemoved, so that the agent no longer holds the removed asset. */
    void dropAsset(String id) {
        locked(() -> assetEvent(assets.forget(id), "ASSET_REMOVED"));
    }

    /** Adds an AssetChanged or AssetRemoved observation on the first data item of that type, with the asset type. */
    private long assetEvent(StubAssets.Asset asset, String type) {
        StubDeviceModel.Item item = model.items().stream().filter(candidate -> type.equals(candidate.type())).findFirst()
                .orElseThrow();
        return append(item.id(), asset.id(), null, null, null, null, null, Map.of("assetType", asset.type()));
    }

    /** Accepts connections but sends nothing, on open streams and new requests, until {@link #resume()}. */
    void pause() {
        locked(() -> paused = true);
    }

    void resume() {
        locked(() -> paused = false);
    }

    /** Closes the listener and every open connection, so that connections are refused until {@link #accept()}. */
    void refuse() {
        locked(() -> generation++);
        server.stop(0);
    }

    void accept() throws IOException {
        server = listen(port);
    }

    /** Restarts the agent: a new instance id, the sequence from 1, every item UNAVAILABLE, open streams closed. */
    void restart() {
        locked(() -> {
            instanceId++;
            buffer.clear();
            latest.clear();
            nextSequence = 1;
            reset();
            return generation++;
        });
    }

    /** Closes open streams and adds more observations than the buffer holds, so a client's cursor falls out of it. */
    void overrun(String id) {
        locked(() -> {
            generation++;
            for (int i = 0; i < bufferSize + 5; i++) {
                append(id, Integer.toString(i), null, null, null, null, null, Map.of());
            }
            return null;
        });
    }

    /** Ends the open streams the way an agent does: a zero-length chunk, no closing boundary. */
    void closeStreams() {
        locked(() -> generation++);
    }

    /**
     * Answers every sample stream with Connection: close, so that the client opens a new connection, with a new TLS
     * handshake, for its next request instead of reusing the pooled one.
     */
    void closeConnectionAfterStreams() {
        locked(() -> closeConnectionAfterStreams = true);
    }

    /** Ends every sample stream right after its response headers, the way a proxy that cuts responses does. */
    void endStreamsAtOnce() {
        locked(() -> endStreamsAtOnce = true);
    }

    /** Compresses the answers to requests that ask for gzip, as an agent behind a compressing proxy does. */
    void compress() {
        locked(() -> compress = true);
    }

    /** Answers sample requests with one streams document instead of a stream, until {@link #clearAnswers()}. */
    void answerSampleWithoutStreaming() {
        locked(() -> streaming = false);
    }

    /** Answers every request of a kind with a failure until {@link #clearAnswers()}. */
    void answer(Request request, Answer answer) {
        locked(() -> answers.put(request, answer));
    }

    void clearAnswers() {
        locked(() -> {
            answers.clear();
            streaming = true;
            endStreamsAtOnce = false;
            closeConnectionAfterStreams = false;
            return null;
        });
    }

    @Override
    public void close() {
        locked(() -> closed = true);
        server.stop(0);
        handlers.shutdownNow();
    }

    private void reset() {
        for (StubDeviceModel.Item item : model.items()) {
            append(item.id(), null, null, item.condition() ? "Unavailable" : null, null, null, null, Map.of());
        }
    }

    private long add(
            String id, String value, Map<String, String> entries, String level, String nativeCode, String severity,
            String qualifier) {
        return locked(() -> append(id, value, entries, level, nativeCode, severity, qualifier, Map.of()));
    }

    private long append(
            String id, String value, Map<String, String> entries, String level, String nativeCode, String severity,
            String qualifier, Map<String, String> attributes) {
        long sequence = nextSequence++;
        StubDocuments.Observation observation = new StubDocuments.Observation(
                sequence, model.item(id), value, entries, level, nativeCode, severity, qualifier, attributes, Instant.now());
        buffer.addLast(observation);
        while (buffer.size() > bufferSize) {
            buffer.removeFirst();
        }
        latest.put(id, observation);
        history.add(sequence);
        changed.signalAll();
        return sequence;
    }

    private HttpServer listen(int listenPort) throws IOException {
        InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), listenPort);
        HttpServer created;
        if (tls == null) {
            created = HttpServer.create(address, 0);
        } else {
            HttpsServer https = HttpsServer.create(address, 0);
            https.setHttpsConfigurator(new HttpsConfigurator(tls) {
                @Override
                public void configure(HttpsParameters params) {
                    params.setProtocols(new String[] { "TLSv1.3" });
                    params.setCipherSuites(new String[] { cipherSuite });
                }
            });
            created = https;
        }
        created.setExecutor(handlers);
        created.createContext("/", this::handle);
        created.start();
        return created;
    }

    private void handle(HttpExchange exchange) throws IOException {
        requests.add(exchange.getRequestURI().toString());
        if (gzipAccepted(exchange)) {
            compressedRequests.add(exchange.getRequestURI().toString());
        }
        try (exchange) {
            if (!awaitResume()) {
                return;
            }
            String path = exchange.getRequestURI().getRawPath();
            String[] segments = path.substring(1).split("/");
            boolean byId = path.startsWith("/asset/");
            Request request = byId ? Request.ASSET : Request.valueOf(segments[segments.length - 1].toUpperCase(Locale.ROOT));
            Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
            // the asset requests take the device as a query parameter, the others as the first path segment
            String device = request == Request.ASSETS ? query.get("device")
                    : !byId && segments.length > 1 ? URLDecoder.decode(segments[0], StandardCharsets.UTF_8) : null;
            Answer answer = locked(() -> answers.get(request));
            if (answer != null) {
                fail(exchange, answer, "NO_DEVICE".equals(answer.errorCode())
                        ? "Could not find the device '" + device + "'" : "Answer set by the test");
            } else if (device != null && !model.hasDevice(device)) {
                fail(exchange, new Answer(404, "NO_DEVICE"), "Could not find the device '" + device + "'");
            } else if (request == Request.PROBE) {
                send(exchange, 200, locked(() -> model.probe(device, instanceId, bufferSize)));
            } else if (request == Request.CURRENT) {
                current(exchange, device, query);
            } else if (request == Request.ASSETS) {
                assets(exchange, device, query);
            } else if (request == Request.ASSET) {
                asset(exchange, URLDecoder.decode(path.substring("/asset/".length()), StandardCharsets.UTF_8));
            } else {
                sample(exchange, device, query);
            }
        } catch (IllegalArgumentException e) {
            send(exchange, 404, "<html><body>Not found</body></html>");
        }
    }

    private void current(HttpExchange exchange, String device, Map<String, String> query) throws IOException {
        if (!query.containsKey("at")) {
            send(exchange, 200, locked(() -> documents.streams(window(nextSequence), latest.values().stream()
                    .filter(o -> StubDeviceModel.matches(o.item(), device)).toList(), false)));
            return;
        }
        long at = Long.parseLong(query.get("at"));
        String outOfRange = locked(() -> at < firstSequence() || at >= nextSequence
                ? "'at' must be greater than or equal to " + firstSequence() : null);
        if (outOfRange != null) {
            fail(exchange, new Answer(dialect.outOfRangeStatus, "OUT_OF_RANGE"), outOfRange);
            return;
        }
        // the last observation of each item at the sequence, among those the buffer still holds
        send(exchange, 200, locked(() -> {
            Map<String, StubDocuments.Observation> values = new LinkedHashMap<>();
            buffer.stream().filter(o -> o.sequence() <= at && StubDeviceModel.matches(o.item(), device))
                    .forEach(o -> values.put(o.item().id(), o));
            // cppagent 2.8 and Mazak 1.4 answer with their own next sequence, not the one after at
            return documents.streams(window(nextSequence), List.copyOf(values.values()), false);
        }));
    }

    private void assets(HttpExchange exchange, String device, Map<String, String> query) throws IOException {
        String uuid = device == null ? null : model.uuid(device);
        int count = Integer.parseInt(query.getOrDefault("count", "100"));
        boolean removed = "true".equals(query.get("removed"));
        send(exchange, 200, locked(() -> assets.document(instanceId,
                assets.select(uuid, query.get("type"), count, removed))));
    }

    /** Answers asset/<id>[;<id>...]: cppagent 2.8 leaves unknown ids out, Mazak 1.4 fails on any. */
    private void asset(HttpExchange exchange, String ids) throws IOException {
        List<StubAssets.Asset> found = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        locked(() -> {
            for (String id : ids.split(";")) {
                StubAssets.Asset asset = assets.get(id);
                if (asset == null) {
                    missing.add(id);
                } else {
                    found.add(asset);
                }
            }
            return null;
        });
        if (!missing.isEmpty() && (!dialect.modern() || found.isEmpty())) {
            fail(exchange, new Answer(dialect.modern() ? 404 : 200, "ASSET_NOT_FOUND"),
                    "Could not find asset: " + missing.get(0));
            return;
        }
        send(exchange, 200, locked(() -> assets.document(instanceId, found)));
    }

    private void sample(HttpExchange exchange, String device, Map<String, String> query) throws IOException {
        long from = query.containsKey("from") ? Long.parseLong(query.get("from")) : locked(this::firstSequence);
        Long to = query.containsKey("to") ? Long.valueOf(query.get("to")) : null;
        int count = Integer.parseInt(query.getOrDefault("count", "100"));
        String outOfRange = locked(() -> from < firstSequence() || from > nextSequence || count > bufferSize
                ? "'from' must be greater than or equal to " + firstSequence() : null);
        if (outOfRange != null) {
            fail(exchange, new Answer(dialect.outOfRangeStatus, "OUT_OF_RANGE"), outOfRange);
            return;
        }
        // without an interval an agent answers with one document
        if (!query.containsKey("interval") || !locked(() -> streaming)) {
            send(exchange, 200, locked(() -> part(device, from, count, to).xml()));
            return;
        }
        stream(exchange, device, from, count, Long.parseLong(query.get("interval")),
                Long.parseLong(query.getOrDefault("heartbeat", "10000")));
    }

    private void stream(HttpExchange exchange, String device, long from, int count, long interval, long heartbeat)
            throws IOException {
        String boundary = UUID.randomUUID().toString();
        exchange.getResponseHeaders().set("Content-Type", dialect.multipartType + ";boundary=" + boundary);
        if (locked(() -> closeConnectionAfterStreams)) {
            exchange.getResponseHeaders().set("Connection", "close");
        }
        exchange.sendResponseHeaders(200, 0);
        if (locked(() -> endStreamsAtOnce)) {
            return;
        }
        OutputStream out = exchange.getResponseBody();
        int streamGeneration = locked(() -> generation);
        long cursor = from;
        long lastPart = System.nanoTime();
        while (true) {
            Part part;
            lock.lock();
            try {
                while (true) {
                    if (closed || generation != streamGeneration) {
                        return;
                    }
                    long since = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastPart);
                    if (!paused) {
                        boolean pending = cursor < nextSequence;
                        if (pending && since >= interval || since >= heartbeat) {
                            part = pending ? part(device, cursor, count, null) : part(device, nextSequence, count, null);
                            break;
                        }
                    }
                    changed.await(10, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } finally {
                lock.unlock();
            }
            byte[] content = part.xml().getBytes(StandardCharsets.UTF_8);
            String headers = "--" + boundary + "\r\n" + dialect.partContentType + ": " + dialect.contentType + "\r\n"
                             + dialect.partContentLength + ": " + content.length + "\r\n\r\n";
            out.write(headers.getBytes(StandardCharsets.US_ASCII));
            out.write(content);
            out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            cursor = part.next();
            lastPart = System.nanoTime();
        }
    }

    /** A sample document from the cursor with up to count observations up to to, and the cursor after it. */
    private record Part(String xml, long next) {
    }

    private Part part(String device, long from, int count, Long to) {
        List<StubDocuments.Observation> selected = new ArrayList<>();
        long next = to == null ? nextSequence : Math.min(nextSequence, to + 1);
        for (StubDocuments.Observation observation : buffer) {
            if (observation.sequence() < from) {
                continue;
            }
            if (to != null && observation.sequence() > to) {
                break;
            }
            if (selected.size() == count) {
                next = observation.sequence();
                break;
            }
            if (StubDeviceModel.matches(observation.item(), device)) {
                selected.add(observation);
            }
        }
        return new Part(documents.streams(window(next), selected, true), next);
    }

    private StubDocuments.Window window(long next) {
        return new StubDocuments.Window(instanceId, bufferSize, next, firstSequence(), nextSequence - 1);
    }

    private long firstSequence() {
        return buffer.isEmpty() ? nextSequence : buffer.peekFirst().sequence();
    }

    private boolean awaitResume() {
        lock.lock();
        try {
            while (paused && !closed) {
                changed.await(10, TimeUnit.MILLISECONDS);
            }
            return !closed;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            lock.unlock();
        }
    }

    private void fail(HttpExchange exchange, Answer answer, String message) throws IOException {
        if (answer.errorCode() == null) {
            send(exchange, answer.status(), "<html><head><title>" + answer.status() + " Bad Gateway</title></head>"
                                            + "<body><h1>Bad Gateway</h1></body></html>");
            return;
        }
        send(exchange, answer.status(),
                locked(() -> documents.error(instanceId, bufferSize, answer.errorCode(), message)));
    }

    private void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] content = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", body.startsWith("<html") ? "text/html" : dialect.contentType);
        if (gzipAccepted(exchange) && locked(() -> compress)) {
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
                gzip.write(content);
            }
            content = compressed.toByteArray();
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
        }
        exchange.sendResponseHeaders(status, content.length);
        exchange.getResponseBody().write(content);
    }

    private static boolean gzipAccepted(HttpExchange exchange) {
        String accepted = exchange.getRequestHeaders().getFirst("Accept-Encoding");
        return accepted != null && accepted.contains("gzip");
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> query = new HashMap<>();
        if (raw != null) {
            for (String pair : raw.split("&")) {
                int equals = pair.indexOf('=');
                query.put(URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
            }
        }
        return query;
    }

    private <T> T locked(Supplier<T> action) {
        lock.lock();
        try {
            T result = action.get();
            changed.signalAll();
            return result;
        } finally {
            lock.unlock();
        }
    }
}
