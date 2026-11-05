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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.vault.HashicorpVaultConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The properties function and the endpoint log in as configured, against a fake Vault.
 */
class HashicorpVaultLoginTest {

    private FakeVault vault;

    @BeforeEach
    void startVault() throws Exception {
        vault = new FakeVault();
        vault.on("auth/approle/login", request -> FakeVault.login("approle-token", 3600));
        vault.on("secret/data/api", request -> FakeVault.secret("pw"));
        vault.on("auth/token/revoke-self", request -> FakeVault.noContent());
    }

    @AfterEach
    void stopVault() {
        vault.close();
    }

    @Test
    void propertiesFunctionLogsInWithTheVaultConfiguration() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            HashicorpVaultConfiguration hashicorp = context.getVaultConfiguration().hashicorp();
            hashicorp.setHost("localhost");
            hashicorp.setPort(String.valueOf(vault.port()));
            hashicorp.setScheme("http");
            hashicorp.setAuthentication("approle");
            hashicorp.setRoleId("my-role-id");
            hashicorp.setSecretId("my-secret-id");
            context.start();

            assertEquals("pw", context.resolvePropertyPlaceholders("{{hashicorp:secret:api#password}}"));
            assertEquals(Map.of("role_id", "my-role-id", "secret_id", "my-secret-id"),
                    vault.requests("auth/approle/login").get(0).json());
            assertEquals("approle-token", vault.requests("secret/data/api").get(0).token());

            context.stop();
            // the function revokes the token of its login when it stops
            assertEquals("approle-token", vault.requests("auth/token/revoke-self").get(0).token());
        }
    }

    @Test
    void endpointLogsInAsConfigured() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();

            String uri = "hashicorp-vault:secret?operation=getSecret&secretPath=api&host=localhost&port=" + vault.port()
                         + "&scheme=http&authentication=approle&roleId=my-role-id&secretId=RAW(my-secret-id)";
            Object body = context.createProducerTemplate().requestBody(uri, "");

            assertEquals(Map.of("password", "pw"), ((Map<?, ?>) body).get("data"));
            assertEquals("approle-token", vault.requests("secret/data/api").get(0).token());

            context.stop();
            // the endpoint revokes the token of its login when it shuts down
            assertEquals("approle-token", vault.requests("auth/token/revoke-self").get(0).token());
        }
    }

    @Test
    void endpointWithoutTheSettingsOfItsLoginIsRejected() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            ResolveEndpointFailedException e = assertThrows(ResolveEndpointFailedException.class,
                    () -> context
                            .getEndpoint("hashicorp-vault:secret?host=localhost&authentication=approle&roleId=my-role-id"));

            assertTrue(e.getMessage().contains("The approle login needs a role ID and a secret ID"), e.getMessage());
        }
    }
}
