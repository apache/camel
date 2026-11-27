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
package org.apache.camel.component.minio;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With proxyPort, the scheme of the endpoint decides between HTTPS and HTTP; secure only applies to a host name without
 * a scheme. A loopback socket records the first byte of each connection: 0x16 starts a TLS ClientHello, a plain HTTP
 * request starts with the letter of its method.
 */
public class MinioProxyPortSchemeTest extends CamelTestSupport {

    private static final int TLS_HANDSHAKE = 0x16;

    private ServerSocket serverSocket;
    private Thread acceptor;
    private final List<Integer> firstBytes = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startSocket() throws IOException {
        serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        acceptor = new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try (Socket socket = serverSocket.accept()) {
                    socket.setSoTimeout(10000);
                    InputStream in = socket.getInputStream();
                    firstBytes.add(in.read());
                } catch (SocketException e) {
                    // closed
                } catch (IOException e) {
                    firstBytes.add(-2);
                }
            }
        }, "minio-scheme-test");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    @AfterEach
    void stopSocket() throws Exception {
        serverSocket.close();
        acceptor.join(10000);
    }

    @Test
    public void httpsEndpointWithProxyPortUsesTls() throws Exception {
        assertEquals(TLS_HANDSHAKE, firstByteOfRequest("https://localhost", ""));
    }

    @Test
    public void httpEndpointWithProxyPortAndSecureUsesPlainHttp() throws Exception {
        int first = firstByteOfRequest("http://localhost", "&secure=true");
        assertTrue(first != TLS_HANDSHAKE && first > 0, "plain HTTP request expected, got " + first);
    }

    @Test
    public void hostNameWithProxyPortUsesSecure() throws Exception {
        assertEquals(TLS_HANDSHAKE, firstByteOfRequest("localhost", "&secure=true"));

        firstBytes.clear();
        int first = firstByteOfRequest("localhost", "");
        assertTrue(first != TLS_HANDSHAKE && first > 0, "plain HTTP request expected, got " + first);
    }

    @Test
    public void isSecureForProxyPort() {
        assertTrue(MinioEndpoint.isSecureForProxyPort("https://host", false));
        assertTrue(MinioEndpoint.isSecureForProxyPort("HTTPS://host", false));
        assertFalse(MinioEndpoint.isSecureForProxyPort("http://host", true));
        assertTrue(MinioEndpoint.isSecureForProxyPort("host", true));
        assertFalse(MinioEndpoint.isSecureForProxyPort("host", false));
    }

    private int firstByteOfRequest(String endpoint, String options) throws Exception {
        MinioEndpoint minio = (MinioEndpoint) context.getComponent("minio", MinioComponent.class).createEndpoint(
                "minio://mybucket?accessKey=key&secretKey=RAW(secret)&region=us-east-1&endpoint=" + endpoint
                                                                                                                 + "&proxyPort="
                                                                                                                 + serverSocket
                                                                                                                         .getLocalPort()
                                                                                                                 + options);
        MinioClient client = minio.createMinioClient();

        // the socket closes the connection, so the call fails; only the first byte that it sent matters
        assertThrows(Exception.class, () -> client.bucketExists(BucketExistsArgs.builder().bucket("mybucket").build()));
        await().atMost(10, TimeUnit.SECONDS).until(() -> !firstBytes.isEmpty());
        return firstBytes.get(0);
    }
}
