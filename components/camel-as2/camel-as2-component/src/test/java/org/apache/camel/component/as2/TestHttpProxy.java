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
package org.apache.camel.component.as2;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.util.IOHelper;

/**
 * A minimal HTTP forward proxy for tests. It forwards plain http requests sent in absolute form and opens CONNECT
 * tunnels, and records the request line and the Proxy-Authorization header of every request it receives.
 * <p>
 * It refuses a request without the required credentials with a 407. By default it then closes the connection; it can
 * also keep the connection open with a body in the 407, like a proxy waiting for the client to retry with credentials.
 */
final class TestHttpProxy implements Closeable {

    private final ServerSocket serverSocket;
    private final String requiredAuthorization;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<Socket> sockets = new CopyOnWriteArrayList<>();
    private final List<String> requestLines = new CopyOnWriteArrayList<>();
    private final List<String> proxyAuthorizations = new CopyOnWriteArrayList<>();
    private final Map<String, String> hostAliases = new ConcurrentHashMap<>();
    private final AtomicInteger connectionCount = new AtomicInteger();
    private volatile boolean keepAliveAfterRefusal;

    /**
     * @param requiredAuthorization the Proxy-Authorization value the proxy requires, or null to require none
     */
    TestHttpProxy(String requiredAuthorization) throws IOException {
        this.requiredAuthorization = requiredAuthorization;
        this.serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        executor.execute(this::acceptConnections);
    }

    int getPort() {
        return serverSocket.getLocalPort();
    }

    List<String> getRequestLines() {
        return requestLines;
    }

    List<String> getProxyAuthorizations() {
        return proxyAuthorizations;
    }

    /**
     * The number of connections on which the clients sent a request to the proxy. A client's startup check opens a
     * connection without sending anything, so it is not counted.
     */
    int getConnectionCount() {
        return connectionCount.get();
    }

    /**
     * Keeps the connection open after a 407, which carries a body, instead of closing it.
     */
    void setKeepAliveAfterRefusal(boolean keepAliveAfterRefusal) {
        this.keepAliveAfterRefusal = keepAliveAfterRefusal;
    }

    /**
     * Makes the proxy resolve a host name the client itself cannot resolve, like a partner only reachable through the
     * proxy.
     */
    void addHostAlias(String host, String actualHost) {
        hostAliases.put(host, actualHost);
    }

    @Override
    public void close() {
        IOHelper.close(serverSocket);
        sockets.forEach(IOHelper::close);
        executor.shutdownNow();
    }

    private void acceptConnections() {
        while (!serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                sockets.add(client);
                executor.execute(() -> handle(client));
            } catch (IOException e) {
                // closed
            }
        }
    }

    private void handle(Socket client) {
        try {
            InputStream in = new BufferedInputStream(client.getInputStream());
            OutputStream out = client.getOutputStream();
            // a refused request is followed by the next one on the same connection when it is kept alive
            boolean firstRequest = true;
            while (handleRequest(in, out, firstRequest)) {
                firstRequest = false;
            }
        } catch (IOException e) {
            // connection closed
        } finally {
            IOHelper.close(client);
        }
    }

    /**
     * @return true when the connection stays open for another request
     */
    private boolean handleRequest(InputStream in, OutputStream out, boolean firstRequest) throws IOException {
        String requestLine = readLine(in);
        if (requestLine == null) {
            return false;
        }
        if (firstRequest) {
            connectionCount.incrementAndGet();
        }
        List<String> headers = new ArrayList<>();
        String proxyAuthorization = null;
        long contentLength = 0;
        for (String header = readLine(in); header != null && !header.isEmpty(); header = readLine(in)) {
            String name = header.substring(0, Math.max(header.indexOf(':'), 0)).trim().toLowerCase(Locale.ROOT);
            String value = header.substring(header.indexOf(':') + 1).trim();
            if ("proxy-authorization".equals(name)) {
                proxyAuthorization = value;
            } else {
                if ("content-length".equals(name)) {
                    contentLength = Long.parseLong(value);
                }
                headers.add(header);
            }
        }
        requestLines.add(requestLine);
        proxyAuthorizations.add(String.valueOf(proxyAuthorization));

        if (requiredAuthorization != null && !requiredAuthorization.equals(proxyAuthorization)) {
            if (keepAliveAfterRefusal) {
                // read the refused request's body, so the next request on this connection can be read
                in.skipNBytes(contentLength);
                String body = "<html><body>Proxy authentication required</body></html>";
                write(out, "HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=\"test\"\r\n"
                           + "Content-Type: text/html\r\nContent-Length: " + body.length() + "\r\n\r\n" + body);
                return true;
            }
            write(out, "HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=\"test\"\r\n"
                       + "Content-Length: 0\r\nConnection: close\r\n\r\n");
            return false;
        }

        String[] parts = requestLine.split(" ");
        if ("CONNECT".equals(parts[0])) {
            String[] hostPort = parts[1].split(":");
            Socket target = connect(hostPort[0], Integer.parseInt(hostPort[1]));
            write(out, "HTTP/1.1 200 Connection established\r\n\r\n");
            relay(in, out, target);
        } else {
            // forward in origin form, without the proxy's own credentials
            URI uri = URI.create(parts[1]);
            Socket target = connect(uri.getHost(), uri.getPort());
            StringBuilder head = new StringBuilder(parts[0] + " " + uri.getRawPath() + " " + parts[2] + "\r\n");
            headers.forEach(h -> head.append(h).append("\r\n"));
            write(target.getOutputStream(), head.append("\r\n").toString());
            relay(in, out, target);
        }
        return false;
    }

    private Socket connect(String host, int port) throws IOException {
        Socket target = new Socket(hostAliases.getOrDefault(host, host), port);
        sockets.add(target);
        return target;
    }

    private void relay(InputStream clientIn, OutputStream clientOut, Socket target) throws IOException {
        InputStream targetIn = target.getInputStream();
        executor.execute(() -> {
            try {
                targetIn.transferTo(clientOut);
            } catch (IOException e) {
                // connection closed
            }
        });
        try {
            clientIn.transferTo(target.getOutputStream());
        } finally {
            IOHelper.close(target);
        }
    }

    private static void write(OutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1 && b != '\n') {
            if (b != '\r') {
                line.write(b);
            }
        }
        return b == -1 && line.size() == 0 ? null : line.toString(StandardCharsets.ISO_8859_1);
    }
}
