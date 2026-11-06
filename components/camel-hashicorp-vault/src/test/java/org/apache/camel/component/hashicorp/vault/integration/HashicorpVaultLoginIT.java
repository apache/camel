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
package org.apache.camel.component.hashicorp.vault.integration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.hashicorp.vault.services.HashicorpServiceFactory;
import org.apache.camel.test.infra.hashicorp.vault.services.HashicorpVaultService;
import org.apache.camel.vault.HashicorpVaultConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultKeyValueOperationsSupport;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultMount;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The AppRole and JWT logins against a Vault server, with the login methods mounted at other than their default path.
 */
class HashicorpVaultLoginIT {

    @RegisterExtension
    static HashicorpVaultService service = HashicorpServiceFactory.createSingletonService();

    private static final String APPROLE_PATH = "approle-login-it";
    private static final String JWT_PATH = "jwt-login-it";

    private static String roleId;
    private static String secretId;
    private static PrivateKey jwtKey;

    @BeforeAll
    static void configureVault() throws Exception {
        VaultEndpoint endpoint = VaultEndpoint.create(service.host(), service.port());
        endpoint.setScheme("http");
        VaultTemplate root = new VaultTemplate(endpoint, new TokenAuthentication(service.token()));

        root.opsForKeyValue("secret", VaultKeyValueOperationsSupport.KeyValueBackend.versioned())
                .put("login-it", Map.of("password", "pw"));
        root.write("sys/policies/acl/login-it",
                Map.of("policy", "path \"secret/data/login-it\" { capabilities = [\"read\"] }"));

        // the container is shared by the integration tests, and may already have the login methods
        Map<String, VaultMount> mounts = root.opsForSys().getAuthMounts();
        if (!mounts.containsKey(APPROLE_PATH + "/")) {
            root.opsForSys().authMount(APPROLE_PATH, VaultMount.create("approle"));
        }
        root.write("auth/" + APPROLE_PATH + "/role/camel", Map.of("token_policies", "login-it"));
        roleId = (String) root.read("auth/" + APPROLE_PATH + "/role/camel/role-id").getRequiredData().get("role_id");
        secretId = (String) root.write("auth/" + APPROLE_PATH + "/role/camel/secret-id", Map.of())
                .getRequiredData().get("secret_id");

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        jwtKey = keyPair.getPrivate();
        String publicKey = "-----BEGIN PUBLIC KEY-----\n"
                           + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                                   .encodeToString(keyPair.getPublic().getEncoded())
                           + "\n-----END PUBLIC KEY-----\n";
        if (!mounts.containsKey(JWT_PATH + "/")) {
            root.opsForSys().authMount(JWT_PATH, VaultMount.create("jwt"));
        }
        root.write("auth/" + JWT_PATH + "/config", Map.of("jwt_validation_pubkeys", List.of(publicKey)));
        root.write("auth/" + JWT_PATH + "/role/camel", Map.of(
                "role_type", "jwt", "user_claim", "sub", "bound_audiences", List.of("camel"),
                "token_policies", "login-it"));
    }

    @Test
    void propertiesFunctionLogsInWithAppRole() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            HashicorpVaultConfiguration hashicorp = vaultConfiguration(context, "approle");
            hashicorp.setRoleId(roleId);
            hashicorp.setSecretId(secretId);
            hashicorp.setAuthMountPath(APPROLE_PATH);
            context.start();

            assertEquals("pw", context.resolvePropertyPlaceholders("{{hashicorp:secret:login-it#password}}"));
        }
    }

    @Test
    void propertiesFunctionLogsInWithJwtFromFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("token");
        Files.writeString(file, jwt());
        try (CamelContext context = new DefaultCamelContext()) {
            HashicorpVaultConfiguration hashicorp = vaultConfiguration(context, "jwt");
            hashicorp.setRole("camel");
            hashicorp.setJwtFile(file.toString());
            hashicorp.setAuthMountPath(JWT_PATH);
            context.start();

            assertEquals("pw", context.resolvePropertyPlaceholders("{{hashicorp:secret:login-it#password}}"));
        }
    }

    @Test
    void endpointLogsInWithJwt() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();

            String uri = "hashicorp-vault:secret?operation=getSecret&secretPath=login-it&host=" + service.host() + "&port="
                         + service.port() + "&scheme=http&authentication=jwt&role=camel&jwt=RAW(" + jwt()
                         + ")&authMountPath=" + JWT_PATH;
            Object body = context.createProducerTemplate().requestBody(uri, "");

            assertEquals(Map.of("password", "pw"), ((Map<?, ?>) body).get("data"));
        }
    }

    @Test
    void endpointLogsInWithAppRole() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();

            String uri = "hashicorp-vault:secret?operation=getSecret&secretPath=login-it&host=" + service.host() + "&port="
                         + service.port() + "&scheme=http&authentication=approle&roleId=" + roleId + "&secretId=RAW("
                         + secretId + ")&authMountPath=" + APPROLE_PATH;
            Object body = context.createProducerTemplate().requestBody(uri, "");

            assertEquals(Map.of("password", "pw"), ((Map<?, ?>) body).get("data"));
        }
    }

    private static HashicorpVaultConfiguration vaultConfiguration(CamelContext context, String authentication) {
        HashicorpVaultConfiguration hashicorp = context.getVaultConfiguration().hashicorp();
        hashicorp.setHost(service.host());
        hashicorp.setPort(String.valueOf(service.port()));
        hashicorp.setScheme("http");
        hashicorp.setAuthentication(authentication);
        return hashicorp;
    }

    /**
     * A JWT for the audience camel, signed with the key that the JWT login trusts.
     */
    private static String jwt() throws Exception {
        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        long now = Instant.now().getEpochSecond();
        String header = base64.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String claims = "{\"sub\":\"camel-it\",\"aud\":\"camel\",\"iat\":" + now + ",\"nbf\":" + now
                        + ",\"exp\":" + (now + 600) + "}";
        String payload = base64.encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(jwtKey);
        signature.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
        return header + "." + payload + "." + base64.encodeToString(signature.sign());
    }
}
