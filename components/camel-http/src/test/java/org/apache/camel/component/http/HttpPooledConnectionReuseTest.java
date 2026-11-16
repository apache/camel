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
package org.apache.camel.component.http;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.util.IOHelper;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A pooled connection that a NAT dropped silently, or that the server closed, must not be reused when the component is
 * configured with connectionIdleTimeout or validateAfterInactivity.
 */
public class HttpPooledConnectionReuseTest extends BaseHttpTest {

    private static final long IDLE_TIMEOUT = 500;

    private HttpServer localServer;
    private TestProxy proxy;

    @Override
    public void setupResources() throws Exception {
        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost")
                .register("/", (request, response, context) -> {
                    response.setCode(HttpStatus.SC_OK);
                    // a server that announces a long keep-alive: it takes precedence over httpClient.connectionKeepAlive
                    response.addHeader("Keep-Alive", "timeout=300");
                    response.setEntity(new StringEntity(getExpectedContent(), ContentType.TEXT_PLAIN));
                })
                .create();
        localServer.start();
        proxy = new TestProxy(localServer.getLocalPort());
    }

    @Override
    public void cleanupResources() throws Exception {
        IOHelper.close(proxy);
        if (localServer != null) {
            localServer.close(CloseMode.IMMEDIATE);
        }
    }

    @Test
    public void connectionDroppedSilentlyIsReusedWithoutIdleTimeout() {
        Exchange second = sendAfterSilentDrop("http://localhost:" + proxy.getPort()
                                              + "/?responseTimeout=1000&httpClient.connectionKeepAlive=" + IDLE_TIMEOUT);

        assertInstanceOf(SocketTimeoutException.class, second.getException());
        assertEquals(1, proxy.getAcceptedConnections());
    }

    @Test
    public void connectionIdleTimeoutDiscardsConnectionDroppedSilently() {
        context.getComponent("http", HttpComponent.class).setConnectionIdleTimeout(IDLE_TIMEOUT);

        Exchange second = sendAfterSilentDrop("http://localhost:" + proxy.getPort() + "/?responseTimeout=1000");

        assertExchange(second);
        assertEquals(2, proxy.getAcceptedConnections());
    }

    @Test
    public void postOnConnectionClosedByServerFailsWithDefaultValidation() {
        Exchange second = postAfterServerClose("http://localhost:" + proxy.getPort() + "/?responseTimeout=1000");

        assertInstanceOf(IOException.class, second.getException());
        assertEquals(1, proxy.getAcceptedConnections());
    }

    @Test
    public void validateAfterInactivityDiscardsConnectionClosedByServer() {
        context.getComponent("http", HttpComponent.class).setValidateAfterInactivity(0);

        Exchange second = postAfterServerClose("http://localhost:" + proxy.getPort() + "/?responseTimeout=1000");

        assertExchange(second);
        assertEquals(2, proxy.getAcceptedConnections());
    }

    /**
     * Sends a GET, then the connection it used goes silent while idle in the pool, as with a NAT that drops idle
     * connections, and a second GET is sent once the connection has been idle for longer than {@link #IDLE_TIMEOUT}.
     */
    private Exchange sendAfterSilentDrop(String uri) {
        assertExchange(template.request(uri, exchange -> {
        }));
        long idleSince = System.nanoTime();
        proxy.dropOpenConnections();

        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - idleSince) > 2 * IDLE_TIMEOUT);

        return template.request(uri, exchange -> {
        });
    }

    /**
     * Sends a POST, then the connection it used is closed while idle in the pool, as by a server that closes idle
     * connections, and a second POST is sent right away (within the 2 seconds after which HttpClient checks a pooled
     * connection by default).
     */
    private Exchange postAfterServerClose(String uri) {
        assertExchange(template.request(uri, exchange -> exchange.getIn().setBody("first")));
        proxy.closeOpenConnections();

        return template.request(uri, exchange -> exchange.getIn().setBody("second"));
    }

    /**
     * A TCP proxy in front of the test server. {@link #dropOpenConnections()} makes the connections open at that time
     * silent, as a NAT or firewall that drops idle connections does: what the client sends is read and discarded,
     * nothing comes back and the connection is never closed. {@link #closeOpenConnections()} closes them. New
     * connections are forwarded.
     */
    private static final class TestProxy implements Closeable {

        private final ServerSocket serverSocket;
        private final int targetPort;
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final List<Link> links = new CopyOnWriteArrayList<>();

        TestProxy(int targetPort) throws IOException {
            this.targetPort = targetPort;
            this.serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            executor.execute(this::accept);
        }

        int getPort() {
            return serverSocket.getLocalPort();
        }

        int getAcceptedConnections() {
            return links.size();
        }

        void dropOpenConnections() {
            links.forEach(link -> link.silent = true);
        }

        void closeOpenConnections() {
            links.forEach(Link::close);
        }

        private void accept() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket client = serverSocket.accept();
                    Link link = new Link(client, new Socket(InetAddress.getLoopbackAddress(), targetPort));
                    links.add(link);
                    executor.execute(() -> link.pump(link.client, link.server));
                    executor.execute(() -> link.pump(link.server, link.client));
                } catch (IOException e) {
                    // the proxy is closed
                }
            }
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
            links.forEach(Link::close);
            executor.shutdownNow();
        }
    }

    private static final class Link {

        private final Socket client;
        private final Socket server;
        private volatile boolean silent;

        Link(Socket client, Socket server) {
            this.client = client;
            this.server = server;
        }

        void pump(Socket from, Socket to) {
            byte[] buffer = new byte[8192];
            try {
                InputStream in = from.getInputStream();
                OutputStream out = to.getOutputStream();
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (!silent) {
                        out.write(buffer, 0, read);
                        out.flush();
                    }
                }
            } catch (IOException e) {
                // the connection is closed
            }
            if (!silent) {
                close();
            }
        }

        void close() {
            IOHelper.close(client, server);
        }
    }
}
