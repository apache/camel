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
package org.apache.camel.component.cyberark.vault;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Endpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An endpoint without usable credentials fails to start, with a message that names the options to set, instead of
 * failing on the first message. An endpoint with an authToken uses only that token.
 */
class CyberArkVaultEndpointCredentialsTest extends CamelTestSupport {

    private static final String CONJUR = "url=http://localhost:1&account=myaccount";

    @Test
    void apiKeyWithoutUsernameFailsAtStart() throws Exception {
        Endpoint endpoint = context.getComponent("cyberark-vault")
                .createEndpoint("cyberark-vault:secret?secretId=db/password&" + CONJUR + "&apiKey=my-api-key");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, endpoint::start);
        assertNamesTheOptions(e);
        assertFalse(e.getMessage().contains("my-api-key"), e.getMessage());
    }

    @Test
    void noCredentialsFailsAtStart() {
        CyberArkVaultConfiguration configuration = new CyberArkVaultConfiguration();
        configuration.setUrl("http://localhost:1");
        configuration.setAccount("myaccount");
        configuration.setSecretId("db/password");
        CyberArkVaultEndpoint endpoint = new CyberArkVaultEndpoint(
                "cyberark-vault:secret", context.getComponent("cyberark-vault"), configuration);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, endpoint::start);
        assertNamesTheOptions(e);
    }

    @Test
    void usernameAndApiKeyStart() throws Exception {
        CyberArkVaultEndpoint endpoint = (CyberArkVaultEndpoint) context.getComponent("cyberark-vault")
                .createEndpoint("cyberark-vault:secret?secretId=db/password&" + CONJUR
                                + "&username=host/myapp&apiKey=my-api-key");

        endpoint.start();
        try {
            assertNotNull(endpoint.getConjurClient());
        } finally {
            endpoint.stop();
        }
    }

    @Test
    void authTokenTakesPrecedenceOverTheLoginCredentials() throws Exception {
        AtomicInteger logins = new AtomicInteger();
        HttpServer conjur = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        conjur.createContext("/authn/", exchange -> {
            logins.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] token = "new-token".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, token.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(token);
            }
        });
        conjur.createContext("/secrets/", exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        conjur.start();
        try {
            String uri = "cyberark-vault:secret?secretId=db/password&url=http://localhost:" + conjur.getAddress().getPort()
                         + "&account=myaccount&authToken=my-token&username=host/myapp&apiKey=my-api-key";

            // the endpoint uses only the token, so the 401 fails at once, without a login with the API key
            CamelExecutionException e = assertThrows(CamelExecutionException.class,
                    () -> template.requestBody(uri, null, String.class));
            String message = e.getCause().getCause().getMessage();
            assertTrue(message.contains("HTTP 401"), message);
            assertEquals(0, logins.get());
        } finally {
            conjur.stop(0);
        }
    }

    private static void assertNamesTheOptions(IllegalArgumentException e) {
        String message = e.getMessage();
        assertTrue(message.contains("authToken"), message);
        assertTrue(message.contains("username and apiKey"), message);
        assertTrue(message.contains("username and password"), message);
        assertTrue(message.contains("conjurClient"), message);
    }
}
