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
package org.apache.camel.component.hashicorp.vault;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManagerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * A fake HashiCorp Vault over HTTP or HTTPS, which records the requests it receives and answers them with the replies
 * set per path (404 for any other path).
 */
final class FakeVault implements AutoCloseable {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    /**
     * A request to Vault: the path is without /v1/, the token and namespace are the X-Vault-Token and X-Vault-Namespace
     * headers (null when not sent), and the client certificate is the subject of the certificate the client presented
     * over HTTPS (null when none).
     */
    record Request(String method, String path, String token, String namespace, String body, String clientCertificate) {

        Map<?, ?> json() {
            try {
                return JSON.readValue(body, Map.class);
            } catch (JacksonException e) {
                throw new IllegalStateException("Not JSON: " + body, e);
            }
        }
    }

    record Reply(int status, String body) {
    }

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final Map<String, Function<Request, Reply>> replies = new ConcurrentHashMap<>();

    FakeVault() throws IOException {
        this(null);
    }

    private FakeVault(SSLContext tls) throws IOException {
        InetSocketAddress address = new InetSocketAddress("localhost", 0);
        if (tls == null) {
            server = HttpServer.create(address, 0);
        } else {
            HttpsServer https = HttpsServer.create(address, 0);
            https.setHttpsConfigurator(new HttpsConfigurator(tls) {
                @Override
                public void configure(HttpsParameters parameters) {
                    SSLParameters ssl = getSSLContext().getDefaultSSLParameters();
                    // ask for a client certificate, as Vault does for the cert login, without requiring one
                    ssl.setWantClientAuth(true);
                    parameters.setSSLParameters(ssl);
                }
            });
            server = https;
        }
        server.createContext("/v1/", this::handle);
        server.start();
    }

    /**
     * A fake Vault over HTTPS, with the certificate of localhost (tls/server.p12) signed by the test CA (tls/ca.pem),
     * which trusts the client certificates of the test CA.
     */
    static FakeVault https() throws IOException, GeneralSecurityException {
        char[] password = "changeit".toCharArray();
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(KeyStore.getInstance(new File(tlsFile("server.p12")), password), password);
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(KeyStore.getInstance(new File(tlsFile("truststore.p12")), password));
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(keys.getKeyManagers(), trust.getTrustManagers(), null);
        return new FakeVault(tls);
    }

    /**
     * The path of a file of the test TLS material (tls/ next to this class)
     */
    static String tlsFile(String name) {
        try {
            return Path.of(FakeVault.class.getResource("tls/" + name).toURI()).toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    int port() {
        return server.getAddress().getPort();
    }

    /**
     * Answers the requests to the path (without /v1/) with the reply.
     */
    void on(String path, Function<Request, Reply> reply) {
        replies.put(path, reply);
    }

    /**
     * The requests received for the path (without /v1/), in the order received.
     */
    List<Request> requests(String path) {
        return requests.stream().filter(request -> request.path().equals(path)).toList();
    }

    /**
     * The reply to a login: a renewable service token with the given time to live.
     */
    static Reply login(String token, int ttlSeconds) {
        return new Reply(
                200, "{\"auth\":{\"client_token\":\"" + token + "\",\"accessor\":\"accessor-" + token + "\",\"lease_duration\":"
                     + ttlSeconds + ",\"renewable\":true,\"token_type\":\"service\",\"policies\":[\"default\"]}}");
    }

    /**
     * The reply to the lookup of a renewable service token with the given time to live.
     */
    static Reply lookup(int ttlSeconds) {
        return new Reply(
                200, "{\"data\":{\"accessor\":\"accessor\",\"ttl\":" + ttlSeconds
                     + ",\"renewable\":true,\"type\":\"service\",\"policies\":[\"default\"]}}");
    }

    /**
     * The reply to the read of a secret in a kv version 2 engine, with a password field.
     */
    static Reply secret(String password) {
        return new Reply(
                200, "{\"data\":{\"data\":{\"password\":\"" + password + "\"},\"metadata\":{\"version\":1}}}");
    }

    static Reply forbidden() {
        return new Reply(403, "{\"errors\":[\"permission denied\"]}");
    }

    static Reply noContent() {
        return new Reply(204, null);
    }

    private void handle(HttpExchange http) throws IOException {
        Request request = new Request(
                http.getRequestMethod(), http.getRequestURI().getPath().substring("/v1/".length()),
                http.getRequestHeaders().getFirst("X-Vault-Token"), http.getRequestHeaders().getFirst("X-Vault-Namespace"),
                new String(http.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), clientCertificate(http));
        requests.add(request);
        Function<Request, Reply> answer = replies.get(request.path());
        Reply reply = answer != null ? answer.apply(request) : new Reply(404, "{\"errors\":[]}");
        if (reply.body() == null) {
            http.sendResponseHeaders(reply.status(), -1);
            http.close();
            return;
        }
        byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
        http.getResponseHeaders().add("Content-Type", "application/json");
        http.sendResponseHeaders(reply.status(), bytes.length);
        try (OutputStream os = http.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String clientCertificate(HttpExchange http) {
        if (http instanceof HttpsExchange https) {
            try {
                Certificate[] chain = https.getSSLSession().getPeerCertificates();
                return ((X509Certificate) chain[0]).getSubjectX500Principal().getName();
            } catch (SSLPeerUnverifiedException e) {
                // the client presented no certificate
            }
        }
        return null;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
