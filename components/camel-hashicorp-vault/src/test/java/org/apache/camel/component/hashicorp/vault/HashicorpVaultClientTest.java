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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.component.hashicorp.vault.FakeVault.Request;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.vault.support.VaultResponse;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HashicorpVaultClientTest {

    // a token that lives 6 seconds is renewed a second after the login (5 seconds before it expires)
    private static final int RENEWED_SOON = 6;
    private static final int RENEWED_LATER = 3600;

    private FakeVault vault;
    private CamelContext context;
    private HashicorpVaultClient client;

    @BeforeEach
    void startVault() throws Exception {
        vault = new FakeVault();
        vault.on("secret/data/api", request -> FakeVault.secret("pw"));
        vault.on("auth/token/revoke-self", request -> FakeVault.noContent());
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    void stopVault() {
        if (client != null) {
            client.close();
        }
        context.stop();
        vault.close();
    }

    @Test
    void approleLogsInWithTheRoleIdAndSecretIdAndRevokesTheTokenOnClose() {
        vault.on("auth/approle/login", request -> FakeVault.login("approle-token", RENEWED_LATER));
        HashicorpVaultClientSettings settings = settings("approle");
        settings.setRoleId("my-role-id");
        settings.setSecretId("my-secret-id");
        client = HashicorpVaultClient.create(context, this, settings);

        assertEquals("pw", readPassword());

        List<Request> logins = vault.requests("auth/approle/login");
        assertEquals(1, logins.size());
        assertEquals("POST", logins.get(0).method());
        assertEquals(Map.of("role_id", "my-role-id", "secret_id", "my-secret-id"), logins.get(0).json());
        assertEquals("approle-token", vault.requests("secret/data/api").get(0).token());

        client.close();
        client = null;
        List<Request> revocations = vault.requests("auth/token/revoke-self");
        assertEquals(1, revocations.size());
        assertEquals("approle-token", revocations.get(0).token());
    }

    @Test
    void logsInAtTheMountPath() {
        vault.on("auth/my-approle/login", request -> FakeVault.login("approle-token", RENEWED_LATER));
        HashicorpVaultClientSettings settings = settings("approle");
        settings.setRoleId("my-role-id");
        settings.setSecretId("my-secret-id");
        settings.setAuthMountPath("my-approle");
        client = HashicorpVaultClient.create(context, this, settings);

        assertEquals("pw", readPassword());
        assertEquals(1, vault.requests("auth/my-approle/login").size());
    }

    @Test
    void jwtLogsInWithTheJwtAndRole() {
        vault.on("auth/jwt/login", request -> FakeVault.login("jwt-token", RENEWED_LATER));
        HashicorpVaultClientSettings settings = settings("jwt");
        settings.setJwt("header.payload.signature");
        settings.setRole("camel");
        client = HashicorpVaultClient.create(context, this, settings);

        assertEquals("pw", readPassword());
        assertEquals(Map.of("jwt", "header.payload.signature", "role", "camel"),
                vault.requests("auth/jwt/login").get(0).json());
        assertEquals("jwt-token", vault.requests("secret/data/api").get(0).token());
    }

    @Test
    void jwtLogsInWithoutRoleForTheDefaultRole() {
        vault.on("auth/jwt/login", request -> FakeVault.login("jwt-token", RENEWED_LATER));
        HashicorpVaultClientSettings settings = settings("jwt");
        settings.setJwt("header.payload.signature");
        client = HashicorpVaultClient.create(context, this, settings);

        assertEquals("pw", readPassword());
        assertEquals(Map.of("jwt", "header.payload.signature"), vault.requests("auth/jwt/login").get(0).json());
    }

    @Test
    void jwtFileIsReadAtEveryLogin(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("token");
        Files.writeString(file, "jwt-1\n");
        AtomicInteger logins = new AtomicInteger();
        vault.on("auth/jwt/login", request -> {
            int login = logins.incrementAndGet();
            return FakeVault.login("jwt-token-" + login, login == 1 ? RENEWED_SOON : RENEWED_LATER);
        });
        vault.on("auth/token/renew-self", request -> FakeVault.forbidden());
        HashicorpVaultClientSettings settings = settings("jwt");
        settings.setJwtFile(file.toString());
        settings.setRole("camel");
        client = HashicorpVaultClient.create(context, this, settings);

        assertEquals("pw", readPassword());
        Files.writeString(file, "jwt-2\n");

        // the renewal of the first token fails, so a later request logs in again, with the JWT now in the file
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            readPassword();
            assertEquals(2, vault.requests("auth/jwt/login").size());
        });
        List<Request> requests = vault.requests("auth/jwt/login");
        assertEquals("jwt-1", requests.get(0).json().get("jwt"));
        assertEquals("jwt-2", requests.get(1).json().get("jwt"));
    }

    @Test
    void kubernetesLogsInWithTheServiceAccountTokenAndRole(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("token");
        Files.writeString(file, "service-account-jwt");
        vault.on("auth/kubernetes/login", request -> FakeVault.login("kubernetes-token", RENEWED_LATER));
        HashicorpVaultClientSettings settings = settings("kubernetes");
        settings.setRole("camel");
        settings.setJwtFile(file.toString());
        client = HashicorpVaultClient.create(context, this, settings);

        assertEquals("pw", readPassword());
        assertEquals(Map.of("jwt", "service-account-jwt", "role", "camel"),
                vault.requests("auth/kubernetes/login").get(0).json());
        assertEquals("kubernetes-token", vault.requests("secret/data/api").get(0).token());
    }

    @Test
    void renewsTheTokenBeforeItExpires() {
        vault.on("auth/approle/login", request -> FakeVault.login("approle-token", RENEWED_SOON));
        vault.on("auth/token/renew-self", request -> FakeVault.login("approle-token", RENEWED_LATER));
        client = HashicorpVaultClient.create(context, this, approle());

        assertEquals("pw", readPassword());
        await().atMost(10, TimeUnit.SECONDS).until(() -> !vault.requests("auth/token/renew-self").isEmpty());

        assertEquals("approle-token", vault.requests("auth/token/renew-self").get(0).token());
        assertEquals("pw", readPassword());
        assertEquals(1, vault.requests("auth/approle/login").size(), "the renewed token is used further");
    }

    @Test
    void logsInAgainWhenTheTokenCanNoLongerBeRenewed() {
        AtomicInteger logins = new AtomicInteger();
        vault.on("auth/approle/login", request -> {
            int login = logins.incrementAndGet();
            return FakeVault.login("approle-token-" + login, login == 1 ? RENEWED_SOON : RENEWED_LATER);
        });
        vault.on("auth/token/renew-self", request -> FakeVault.forbidden());
        client = HashicorpVaultClient.create(context, this, approle());

        assertEquals("pw", readPassword());
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            readPassword();
            assertEquals(2, vault.requests("auth/approle/login").size());
        });

        List<Request> reads = vault.requests("secret/data/api");
        assertEquals("approle-token-2", reads.get(reads.size() - 1).token());
    }

    @Test
    void renewsAGivenTokenButNeverRevokesIt() {
        vault.on("auth/token/lookup-self", request -> FakeVault.lookup(RENEWED_SOON));
        vault.on("auth/token/renew-self", request -> FakeVault.login("my-token", RENEWED_LATER));
        HashicorpVaultClientSettings settings = settings(null);
        settings.setToken("my-token");
        client = HashicorpVaultClient.create(context, this, settings);

        assertEquals("pw", readPassword());
        assertEquals("my-token", vault.requests("secret/data/api").get(0).token());
        await().atMost(10, TimeUnit.SECONDS).until(() -> !vault.requests("auth/token/renew-self").isEmpty());
        assertEquals("my-token", vault.requests("auth/token/renew-self").get(0).token());

        client.close();
        client = null;
        assertTrue(vault.requests("auth/token/revoke-self").isEmpty(), "a given token is never revoked");
    }

    @Test
    void logsInToTheNamespaceOnHashicorpCloud() {
        vault.on("auth/approle/login", request -> FakeVault.login("approle-token", RENEWED_LATER));
        vault.on("admin/secret/data/api", request -> FakeVault.secret("pw"));
        HashicorpVaultClientSettings settings = approle();
        settings.setCloud(true);
        settings.setNamespace("admin");
        client = HashicorpVaultClient.create(context, this, settings);

        client.getVaultTemplate().read("admin/secret/data/api");
        client.close();
        client = null;

        assertEquals("admin", vault.requests("auth/approle/login").get(0).namespace());
        assertEquals("admin", vault.requests("auth/token/revoke-self").get(0).namespace());
        // Camel reads the secrets with the namespace in their path
        assertNull(vault.requests("admin/secret/data/api").get(0).namespace());
    }

    @Test
    void usesTheNamespaceOnlyOnHashicorpCloud() {
        vault.on("auth/approle/login", request -> FakeVault.login("approle-token", RENEWED_LATER));
        HashicorpVaultClientSettings settings = approle();
        settings.setNamespace("admin");
        client = HashicorpVaultClient.create(context, this, settings);

        assertEquals("pw", readPassword());
        assertNull(vault.requests("auth/approle/login").get(0).namespace());
    }

    @Test
    void validatesTheSettingsOfTheLogin() {
        HashicorpVaultClientSettings settings = settings(null);
        assertInvalid("The token login needs a token", settings);

        settings = settings("approle");
        settings.setRoleId("my-role-id");
        assertInvalid("The approle login needs a role ID and a secret ID", settings);

        settings = settings("kubernetes");
        assertInvalid("The kubernetes login needs a role", settings);

        settings = settings("jwt");
        assertInvalid("The jwt login needs a JWT, or a file to read it from", settings);
        settings.setJwt("header.payload.signature");
        settings.setJwtFile("/var/run/token");
        assertInvalid("The jwt login takes a JWT or a file to read it from, not both", settings);

        settings = settings("ldap");
        assertInvalid(
                "Unknown HashiCorp Vault login: ldap. Use one of: token, approle, kubernetes, jwt, cert, aws-iam, azure-msi, gcp-gce",
                settings);

        settings = approle();
        settings.setHost(null);
        assertInvalid("The host, port and scheme of HashiCorp Vault must be set", settings);

        settings = approle();
        settings.setPort("http");
        assertInvalid("The port of HashiCorp Vault must be a number: http", settings);
    }

    @Test
    void acceptsTheLoginInAnyCase() {
        HashicorpVaultClientSettings settings = approle();
        settings.setAuthentication("AppRole");
        HashicorpVaultClient.validate(settings);
    }

    @Test
    void failsWhenTheJwtFileDoesNotExist(@TempDir Path dir) {
        HashicorpVaultClientSettings settings = settings("jwt");
        settings.setJwtFile(dir.resolve("missing").toString());

        IllegalArgumentException e
                = assertThrows(IllegalArgumentException.class, () -> HashicorpVaultClient.create(context, this, settings));
        assertTrue(e.getMessage().contains("does not exist"), e.getMessage());
    }

    private void assertInvalid(String message, HashicorpVaultClientSettings settings) {
        IllegalArgumentException e
                = assertThrows(IllegalArgumentException.class, () -> HashicorpVaultClient.validate(settings));
        assertEquals(message, e.getMessage());
    }

    private HashicorpVaultClientSettings settings(String authentication) {
        HashicorpVaultClientSettings settings = new HashicorpVaultClientSettings();
        settings.setHost("localhost");
        settings.setPort(String.valueOf(vault.port()));
        settings.setScheme("http");
        settings.setAuthentication(authentication);
        return settings;
    }

    private HashicorpVaultClientSettings approle() {
        HashicorpVaultClientSettings settings = settings("approle");
        settings.setRoleId("my-role-id");
        settings.setSecretId("my-secret-id");
        return settings;
    }

    private String readPassword() {
        VaultResponse response = client.getVaultTemplate().read("secret/data/api");
        return ((Map<?, ?>) response.getData().get("data")).get("password").toString();
    }
}
