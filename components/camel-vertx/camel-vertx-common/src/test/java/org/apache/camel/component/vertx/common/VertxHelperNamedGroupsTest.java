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
package org.apache.camel.component.vertx.common;

import java.io.InputStream;
import java.net.InetAddress;
import java.security.KeyStore;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.jsse.JsseParametersHelper;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.NamedGroupsParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.SignatureSchemesParameters;
import org.apache.camel.support.jsse.TrustAllTrustManager;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * The named groups of sslContextParameters reach the TLS handshakes of Vert.x, on the server and on the client side, as
 * its key exchange groups. The signature schemes, which Vert.x has no setting for, are reported with a WARN.
 */
public class VertxHelperNamedGroupsTest {

    private static final String PASSWORD = "security";

    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final Logger logger = (Logger) LogManager.getLogger(VertxHelper.class);
    private final String thread = Thread.currentThread().getName();
    private final AbstractAppender appender
            = new AbstractAppender("VertxHelperNamedGroupsTest", null, null, true, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent event) {
                    // only the events of this test, as other tests may run at the same time
                    if (event.getLevel().isMoreSpecificThan(Level.WARN) && thread.equals(event.getThreadName())) {
                        warnings.add(event.getMessage().getFormattedMessage());
                    }
                }
            };

    private CamelContext context;
    private Vertx vertx;

    @BeforeEach
    public void setUp() {
        context = new DefaultCamelContext();
        vertx = Vertx.vertx();
        // an appender added while another test still starts log4j would be dropped with the old configuration
        await().atMost(30, TimeUnit.SECONDS).until(logger.getContext()::isStarted);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    public void tearDown() throws Exception {
        logger.removeAppender(appender);
        appender.stop();
        if (vertx != null) {
            vertx.close().toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
        }
        if (context != null) {
            context.stop();
        }
    }

    @Test
    public void testServerOffersOnlyTheConfiguredNamedGroups() throws Exception {
        SSLContextParameters scp = serverParameters();
        scp.setNamedGroups(namedGroups("secp256r1"));

        NetServerOptions options = new NetServerOptions().setHost("localhost").setPort(0);
        VertxHelper.setupSSLOptions(context, scp, options);

        NetServer server = result(vertx.createNetServer(options).connectHandler(NetSocket::close).listen());
        int port = server.actualPort();

        // a client that offers another group only finds no group in common
        assertThatThrownBy(() -> handshake(port, "x25519")).isInstanceOf(SSLException.class);
        // and one that offers the configured group connects
        handshake(port, "secp256r1");

        assertThat(options.getSslOptions().getKeyExchangeGroups()).containsExactly("secp256r1");
    }

    @Test
    public void testClientOffersOnlyTheConfiguredNamedGroups() throws Exception {
        SSLContextParameters scp = new SSLContextParameters();
        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setTrustManager(TrustAllTrustManager.INSTANCE);
        scp.setTrustManagers(tmp);
        scp.setNamedGroups(namedGroups("x25519"));

        // the client connects to the loopback address, which the certificate does not name
        NetClientOptions options = new NetClientOptions().setHostnameVerificationAlgorithm("");
        VertxHelper.setupSSLOptions(context, scp, options);
        NetClient client = vertx.createNetClient(options);

        // a server that accepts another group only finds no group in common
        try (SSLServerSocket serverSocket = serverSocket("secp256r1")) {
            assertThatThrownBy(() -> connect(client, serverSocket)).isInstanceOf(ExecutionException.class);
        }
        // and one that accepts the configured group is connected to
        try (SSLServerSocket serverSocket = serverSocket("x25519")) {
            connect(client, serverSocket);
        }
    }

    @Test
    public void testNoKeyExchangeGroupsWithoutNamedGroups() throws Exception {
        SSLContextParameters scp = serverParameters();

        NetServerOptions options = new NetServerOptions();
        VertxHelper.setupSSLOptions(context, scp, options);

        // Vert.x keeps the named groups of the JVM, unless Camel would reorder them to prefer the post-quantum ones
        SSLContext plain = SSLContext.getInstance("TLS");
        plain.init(null, null, null);
        List<String> postQuantum = JsseParametersHelper.postQuantumNamedGroups(
                plain.getDefaultSSLParameters().getNamedGroups());
        if (postQuantum == null) {
            assertThat(options.getSslOptions().getKeyExchangeGroups()).isNull();
        } else {
            assertThat(options.getSslOptions().getKeyExchangeGroups()).containsExactlyElementsOf(postQuantum);
        }
        assertThat(warnings).isEmpty();
    }

    @Test
    public void testSignatureSchemesAreReported() throws Exception {
        SSLContextParameters scp = serverParameters();
        SignatureSchemesParameters ssp = new SignatureSchemesParameters();
        ssp.setSignatureScheme(List.of("ecdsa_secp256r1_sha256"));
        scp.setSignatureSchemes(ssp);

        VertxHelper.setupSSLOptions(context, scp, new NetServerOptions());

        assertThat(warnings).singleElement().asString().contains("signature schemes");
    }

    private static SSLContextParameters serverParameters() {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource("jsse/service.jks");
        ksp.setPassword(PASSWORD);
        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyStore(ksp);
        kmp.setKeyPassword(PASSWORD);
        SSLContextParameters scp = new SSLContextParameters();
        scp.setKeyManagers(kmp);
        return scp;
    }

    private static NamedGroupsParameters namedGroups(String... groups) {
        NamedGroupsParameters ngp = new NamedGroupsParameters();
        ngp.setNamedGroup(List.of(groups));
        return ngp;
    }

    private static void handshake(int port, String namedGroup) throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[] { TrustAllTrustManager.INSTANCE }, null);
        try (SSLSocket socket = (SSLSocket) sslContext.getSocketFactory().createSocket("localhost", port)) {
            socket.setSoTimeout(10000);
            SSLParameters params = socket.getSSLParameters();
            params.setNamedGroups(new String[] { namedGroup });
            socket.setSSLParameters(params);
            socket.startHandshake();
        }
    }

    private static SSLServerSocket serverSocket(String namedGroup) throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        try (InputStream is = VertxHelperNamedGroupsTest.class.getClassLoader().getResourceAsStream("jsse/service.jks")) {
            ks.load(is, PASSWORD.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, PASSWORD.toCharArray());
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);
        SSLServerSocket serverSocket = (SSLServerSocket) sslContext.getServerSocketFactory()
                .createServerSocket(0, 1, InetAddress.getLoopbackAddress());
        SSLParameters params = serverSocket.getSSLParameters();
        params.setNamedGroups(new String[] { namedGroup });
        serverSocket.setSSLParameters(params);
        return serverSocket;
    }

    /**
     * Connects the client to the server socket, which accepts the connection and completes the TLS handshake on the
     * calling thread. Fails with an ExecutionException when the handshake of the client fails.
     */
    private static void connect(NetClient client, SSLServerSocket serverSocket) throws Exception {
        Future<NetSocket> connected
                = client.connect(serverSocket.getLocalPort(), serverSocket.getInetAddress().getHostAddress());
        serverSocket.setSoTimeout(10000);
        try (SSLSocket accepted = (SSLSocket) serverSocket.accept()) {
            accepted.setSoTimeout(10000);
            try {
                accepted.startHandshake();
            } catch (SSLException e) {
                // the client reports it too
            }
            result(connected).close();
        }
    }

    private static <T> T result(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
