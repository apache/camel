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
package org.apache.camel.component.as2.api;

import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AS2AsyncMDNServerConnectionTest {

    private int port;
    private AS2AsyncMDNServerConnection connection;

    @BeforeEach
    void setUp() throws Exception {
        try (AvailablePortFinder.Port p = AvailablePortFinder.find()) {
            port = p.getPort();
        }
        connection = new AS2AsyncMDNServerConnection(port, null);
    }

    @AfterEach
    void tearDown() {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void asyncMdnServerAcceptsRequestWithAnyHostHeader() throws Exception {
        final boolean[] handled = { false };

        connection.receive("/test-mdn", new HttpRequestHandler() {
            @Override
            public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
                    throws HttpException {
                handled[0] = true;
                response.setCode(200);
            }
        });

        // Wait for server to be ready
        Thread.sleep(200);

        // Send a request with a Host header that doesn't match the server's hostname
        // This previously caused HTTP 421 due to RequestValidateHost
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + port + "/test-mdn").openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Host", "different-host.example.com");
        conn.setDoOutput(true);
        conn.getOutputStream().write("test".getBytes());
        conn.getOutputStream().flush();

        int responseCode = conn.getResponseCode();
        conn.disconnect();

        // Should NOT get 421 (Misdirected Request) anymore
        assertNotEquals(421, responseCode, "Should not reject requests with non-matching Host header");
    }
}
