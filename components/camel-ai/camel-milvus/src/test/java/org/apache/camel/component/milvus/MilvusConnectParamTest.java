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
package org.apache.camel.component.milvus;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The TLS options of the endpoint reach the ConnectParam of the Milvus SDK. No Milvus server is needed.
 */
public class MilvusConnectParamTest extends CamelTestSupport {

    @Test
    public void plaintextByDefault() {
        ConnectParam param = connectParam("milvus:test?host=milvus.example.com&port=19531&token=user:secret");

        assertThat(param.getHost()).isEqualTo("milvus.example.com");
        assertThat(param.getPort()).isEqualTo(19531);
        assertThat(param.isSecure()).isFalse();
        assertThat(param.getServerPemPath()).isNull();
        assertThat(param.getServerName()).isNull();
        assertThat(param.getCaPemPath()).isNull();
        assertThat(param.getClientPemPath()).isNull();
        assertThat(param.getClientKeyPath()).isNull();
    }

    @Test
    public void secure() {
        ConnectParam param = connectParam("milvus:test?host=milvus.example.com&secure=true");

        assertThat(param.isSecure()).isTrue();
        assertThat(param.getServerPemPath()).isNull();
        assertThat(param.getCaPemPath()).isNull();
    }

    @Test
    public void serverPemPath() {
        ConnectParam param = connectParam("milvus:test?host=milvus.example.com&secure=true&serverPemPath=/certs/server.pem");

        assertThat(param.isSecure()).isTrue();
        assertThat(param.getServerPemPath()).isEqualTo("/certs/server.pem");
        // the SDK overrides the authority with the server name, so the host is the default
        assertThat(param.getServerName()).isEqualTo("milvus.example.com");
    }

    @Test
    public void serverPemPathAndServerName() {
        ConnectParam param = connectParam(
                "milvus:test?host=10.0.0.1&secure=true&serverPemPath=/certs/server.pem&serverName=milvus.example.com");

        assertThat(param.getServerPemPath()).isEqualTo("/certs/server.pem");
        assertThat(param.getServerName()).isEqualTo("milvus.example.com");
    }

    @Test
    public void mutualTls() {
        ConnectParam param = connectParam("milvus:test?host=10.0.0.1&secure=true&caPemPath=/certs/ca.pem"
                                          + "&clientPemPath=/certs/client.pem&clientKeyPath=/certs/client.key"
                                          + "&serverName=milvus.example.com");

        assertThat(param.isSecure()).isTrue();
        assertThat(param.getCaPemPath()).isEqualTo("/certs/ca.pem");
        assertThat(param.getClientPemPath()).isEqualTo("/certs/client.pem");
        assertThat(param.getClientKeyPath()).isEqualTo("/certs/client.key");
        assertThat(param.getServerName()).isEqualTo("milvus.example.com");
        assertThat(param.getServerPemPath()).isNull();
    }

    @Test
    public void mutualTlsWithoutClientKey() {
        MilvusEndpoint endpoint = context.getEndpoint(
                "milvus:test?secure=true&caPemPath=/certs/ca.pem&clientPemPath=/certs/client.pem", MilvusEndpoint.class);

        assertThatThrownBy(endpoint::createConnectParam)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("caPemPath, clientPemPath and clientKeyPath must be set together");
    }

    @Test
    public void serverPemPathWithMutualTls() {
        MilvusEndpoint endpoint = context.getEndpoint(
                "milvus:test?serverPemPath=/certs/server.pem&caPemPath=/certs/ca.pem&clientPemPath=/certs/client.pem"
                                                      + "&clientKeyPath=/certs/client.key",
                MilvusEndpoint.class);

        assertThatThrownBy(endpoint::createConnectParam)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("serverPemPath");
    }

    @Test
    public void serverNameAlone() {
        MilvusEndpoint endpoint = context.getEndpoint("milvus:test?secure=true&serverName=milvus.example.com",
                MilvusEndpoint.class);

        assertThatThrownBy(endpoint::createConnectParam)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("serverName");
    }

    @Test
    public void plaintextConnection() throws Exception {
        // a plaintext gRPC channel starts with the HTTP/2 connection preface
        byte[] first = firstBytes("milvus:test?host=127.0.0.1&timeout=2000");

        assertThat(new String(first, StandardCharsets.US_ASCII)).isEqualTo("PRI *");
    }

    @Test
    public void secureConnection() throws Exception {
        // a TLS channel starts with a TLS handshake record (content type 22)
        byte[] first = firstBytes("milvus:test?host=127.0.0.1&timeout=2000&secure=true");

        assertThat(first[0]).isEqualTo((byte) 0x16);
        assertThat(first[1]).isEqualTo((byte) 0x03);
    }

    private ConnectParam connectParam(String uri) {
        return context.getEndpoint(uri, MilvusEndpoint.class).createConnectParam();
    }

    /**
     * Lets the Milvus client connect to a local socket that is not a Milvus server, and returns the first bytes that
     * the client sends.
     */
    private byte[] firstBytes(String uri) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(20000);
            ConnectParam param = context.getEndpoint(uri + "&port=" + server.getLocalPort(), MilvusEndpoint.class)
                    .createConnectParam();
            // the client connects in the constructor and fails, as the server does not answer
            Future<?> client = executor.submit(() -> {
                try {
                    new MilvusServiceClient(param).close();
                } catch (Exception e) {
                    // expected
                }
            });
            byte[] first;
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(20000);
                InputStream in = socket.getInputStream();
                first = in.readNBytes(5);
            }
            server.close();
            client.get(30, TimeUnit.SECONDS);
            return first;
        } finally {
            executor.shutdownNow();
        }
    }
}
