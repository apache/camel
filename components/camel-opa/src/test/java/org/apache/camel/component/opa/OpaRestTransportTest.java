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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.apache.camel.Exchange;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The transport underneath {@code evaluationMode=rest}.
 * <p/>
 * A server that refuses a connection fails fast and is already covered; what is not, and what an operator actually
 * meets, is a server that <em>accepts</em> and then says nothing. The SDK's own transport applies no timeout of any
 * kind, so that case parked the routing thread for ever - and a component that fails closed never reached the point of
 * denying, it simply stopped.
 */
public class OpaRestTransportTest extends CamelTestSupport {

    private static final String KEYSTORE = "opa-server.p12";
    private static final String PASSWORD = "changeit";

    private final List<Closeable> open = new ArrayList<>();

    private interface Closeable extends AutoCloseable {
        @Override
        void close() throws IOException;
    }

    @AfterEach
    void closeSockets() throws Exception {
        for (Closeable c : open) {
            c.close();
        }
        open.clear();
    }

    /**
     * A listener that completes the TCP handshake and then never answers. Deliberately not an HttpServer: the point is
     * a peer that is reachable but mute, which is what a wedged OPA looks like from the client side.
     */
    private int mutePort() throws IOException {
        ServerSocket listener = new ServerSocket(0);
        open.add(listener::close);
        Thread accepter = new Thread(() -> {
            try {
                listener.accept();
                // hold it: no read, no write, no close. Ends when the test closes the listener.
            } catch (IOException e) {
                // the listener was closed as the test finished
            }
        }, "opa-mute-server");
        accepter.setDaemon(true);
        accepter.start();
        return listener.getLocalPort();
    }

    /**
     * An HTTPS listener presenting the throwaway self-signed certificate from {@code opa-server.p12}, answering the
     * policy query the way a real OPA would.
     */
    private int tlsPort() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = getClass().getResourceAsStream("/" + KEYSTORE)) {
            keyStore.load(in, PASSWORD.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, PASSWORD.toCharArray());
        SSLContext serverContext = SSLContext.getInstance("TLS");
        serverContext.init(kmf.getKeyManagers(), null, null);

        HttpsServer server = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverContext));
        server.createContext("/v1/data/authz/allow", exchange -> {
            byte[] body = "{\"result\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        open.add(() -> server.stop(0));
        return server.getAddress().getPort();
    }

    /** Trust material holding exactly the fixture certificate, and nothing else. */
    private SSLContextParameters trustingTheFixture() {
        KeyStoreParameters ks = new KeyStoreParameters();
        ks.setCamelContext(context);
        ks.setResource(KEYSTORE);
        ks.setPassword(PASSWORD);
        ks.setType("PKCS12");

        TrustManagersParameters tm = new TrustManagersParameters();
        tm.setCamelContext(context);
        tm.setKeyStore(ks);

        SSLContextParameters ssl = new SSLContextParameters();
        ssl.setCamelContext(context);
        ssl.setTrustManagers(tm);
        return ssl;
    }

    @Test
    @Timeout(60)
    void reachesAnHttpsServerWhenSslContextParametersTrustIt() throws Exception {
        context.getRegistry().bind("opaTls", trustingTheFixture());

        Exchange out = template.request(
                "opa:authz/allow?serverUrl=https://localhost:" + tlsPort() + "&sslContextParameters=#opaTls", e -> {
                });

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(true);
    }

    @Test
    @Timeout(60)
    void failsClosedAgainstTheSameServerWithoutTheTrustMaterial() throws Exception {
        // same listener, same policy, same request - only sslContextParameters is missing. If this passed, the
        // option would not be doing anything and the test above would prove nothing
        Exchange out = template.request(
                "opa:authz/allow?serverUrl=https://localhost:" + tlsPort(), e -> {
                });

        assertThat(out.getException()).isInstanceOf(OpaPolicyEvaluationException.class);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isNull();
    }

    /** A plain listener that answers every policy query and records the Authorization it was sent. */
    private int recordingPort(AtomicReference<String> seen) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/data/authz/allow", exchange -> {
            seen.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"result\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        open.add(() -> server.stop(0));
        return server.getAddress().getPort();
    }

    @Test
    @Timeout(60)
    void sendsTheBearerTokenWhenOneIsConfigured() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();

        template.request("opa:authz/allow?serverUrl=http://localhost:" + recordingPort(seen) + "&bearerToken=s3cr3t",
                e -> {
                });

        assertThat(seen.get()).isEqualTo("Bearer s3cr3t");
    }

    @Test
    @Timeout(60)
    void sendsNoAuthorizationAtAllWhenTheTokenIsEmpty() throws Exception {
        // an unset placeholder resolves to "", and "Authorization: Bearer " is not an absent header - it is a
        // malformed credential, which a server enforcing tokens answers with a 401 rather than ignoring
        AtomicReference<String> seen = new AtomicReference<>("not called");

        template.request("opa:authz/allow?serverUrl=http://localhost:" + recordingPort(seen) + "&bearerToken=",
                e -> {
                });

        assertThat(seen.get()).isNull();
    }

    @Test
    @Timeout(60)
    void failsClosedWhenTheServerAcceptsAndThenSaysNothing() throws Exception {
        Exchange out = template.request(
                "opa:authz/allow?serverUrl=http://localhost:" + mutePort() + "&requestTimeout=500", e -> {
                });

        assertThat(out.getException()).isInstanceOf(OpaPolicyEvaluationException.class);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isNull();
    }

    @Test
    @Timeout(60)
    void proceedsOnTheSameStallWhenFailOpenIsSet() throws Exception {
        // a timeout is a failure to reach a verdict, not a deny, so it is handled like every other such failure
        Exchange out = template.request(
                "opa:authz/allow?serverUrl=http://localhost:" + mutePort() + "&requestTimeout=500&failOpen=true",
                e -> {
                });

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(true);
    }
}
