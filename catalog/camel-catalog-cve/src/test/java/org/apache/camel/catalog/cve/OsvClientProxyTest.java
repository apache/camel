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
package org.apache.camel.catalog.cve;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-27342: the OSV.dev client logs in to a proxy with Basic authentication when it tunnels HTTPS through it, which
 * the JDK disables by default. A fake proxy asks for the login, records the CONNECT requests and then refuses the
 * tunnel, so no request leaves the machine.
 */
class OsvClientProxyTest {

    private ServerSocket server;
    private final List<String> connects = new CopyOnWriteArrayList<>();
    private final List<String> logins = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startProxy() throws IOException {
        server = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        Thread t = new Thread(this::serve, "fake-proxy");
        t.setDaemon(true);
        t.start();
    }

    @AfterEach
    void stopProxy() throws IOException {
        server.close();
    }

    private void serve() {
        while (!server.isClosed()) {
            try (Socket s = server.accept()) {
                s.setSoTimeout(5000);
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1));
                String requestLine = in.readLine();
                String login = null;
                for (String line = in.readLine(); line != null && !line.isEmpty(); line = in.readLine()) {
                    if (line.regionMatches(true, 0, "Proxy-Authorization:", 0, 20)) {
                        login = line.substring(20).trim();
                    }
                }
                connects.add(requestLine);
                OutputStream out = s.getOutputStream();
                if (login == null) {
                    out.write(("HTTP/1.1 407 Proxy Authentication Required\r\n"
                               + "Proxy-Authenticate: Basic realm=\"corp\"\r\n"
                               + "Content-Length: 0\r\n\r\n")
                            .getBytes(StandardCharsets.ISO_8859_1));
                } else {
                    logins.add(login);
                    out.write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                            .getBytes(StandardCharsets.ISO_8859_1));
                }
                out.flush();
            } catch (IOException e) {
                // the server is closed, or the client went away
            }
        }
    }

    @Test
    void logsInToTheProxyOfTheHttpsTunnel() {
        HttpProxy proxy = new HttpProxy("127.0.0.1", server.getLocalPort(), "scott", "tiger", List.of());
        OsvClient osv = new OsvClient(proxy);

        osv.queryBatch(List.of(new Dependency("org.yaml", "snakeyaml", "1.33")));

        assertThat(connects).isNotEmpty().allMatch(r -> r.startsWith("CONNECT api.osv.dev:443 "));
        assertThat(logins)
                .contains("Basic " + Base64.getEncoder().encodeToString("scott:tiger".getBytes(StandardCharsets.UTF_8)));
        assertThat(osv.lastError()).contains("through the proxy scott@127.0.0.1:" + server.getLocalPort());
    }
}
