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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.hashicorp.vault.services.HashicorpVaultLocalContainerInfraService;
import org.apache.camel.test.infra.hashicorp.vault.services.HashicorpVaultService;
import org.apache.camel.vault.HashicorpVaultConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.ClientHttpRequestFactoryFactory;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultKeyValueOperationsSupport;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.vault.support.VaultMount;
import org.testcontainers.containers.GenericContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The trust store and the cert login against a Vault server with TLS, whose client certificate is issued by the PKI
 * secrets engine of the same server.
 */
class HashicorpVaultTlsIT {

    @RegisterExtension
    static TlsVaultService service = new TlsVaultService();

    @TempDir
    static Path files;

    // the CA certificate of the TLS certificate of Vault, and the client certificate with its key
    private static String vaultCa;
    private static String clientCertificate;

    @BeforeAll
    static void configureVault() throws Exception {
        Path ca = files.resolve("vault-ca.pem");
        service.getContainer().copyFileFromContainer("/tmp/vault-ca.pem", ca.toString());
        vaultCa = ca.toString();

        VaultEndpoint endpoint = VaultEndpoint.create(service.host(), service.port());
        endpoint.setScheme("https");
        SslConfiguration trust = SslConfiguration.forTrustStore(SslConfiguration.KeyStoreConfiguration.of(
                new FileSystemResource(ca), null, SslConfiguration.PEM_KEYSTORE_TYPE));
        VaultClient client = VaultClient.builder().endpoint(endpoint)
                .requestFactory(ClientHttpRequestFactoryFactory.create(new ClientOptions(), trust)).build();
        VaultTemplate root = new VaultTemplate(client, new TokenAuthentication(service.token()));

        root.opsForKeyValue("secret", VaultKeyValueOperationsSupport.KeyValueBackend.versioned())
                .put("tls-it", Map.of("password", "pw"));
        root.write("sys/policies/acl/tls-it", Map.of("policy", "path \"secret/data/tls-it\" { capabilities = [\"read\"] }"));

        // a CA of Vault issues the client certificate, and the cert login trusts the certificates of that CA
        root.opsForSys().mount("pki", VaultMount.create("pki"));
        String clientCa = (String) root.write("pki/root/generate/internal",
                Map.of("common_name", "Camel IT client CA", "ttl", "24h")).getRequiredData().get("certificate");
        root.write("pki/roles/client", Map.of("allow_any_name", true, "client_flag", true, "server_flag", false));
        Map<String, Object> issued = root.write("pki/issue/client",
                Map.of("common_name", "camel-client", "private_key_format", "pkcs8", "ttl", "1h")).getRequiredData();
        Path certificate = files.resolve("client.pem");
        Files.writeString(certificate, issued.get("certificate") + "\n" + issued.get("private_key") + "\n");
        clientCertificate = certificate.toString();

        root.opsForSys().authMount("cert", VaultMount.create("cert"));
        root.write("auth/cert/certs/camel", Map.of("certificate", clientCa, "token_policies", "tls-it"));
    }

    @Test
    void propertiesFunctionLogsInWithTheClientCertificate() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            HashicorpVaultConfiguration hashicorp = context.getVaultConfiguration().hashicorp();
            hashicorp.setHost(service.host());
            hashicorp.setPort(String.valueOf(service.port()));
            hashicorp.setScheme("https");
            hashicorp.setTrustStore(vaultCa);
            hashicorp.setAuthentication("cert");
            hashicorp.setRole("camel");
            hashicorp.setKeyStore(clientCertificate);
            context.start();

            assertEquals("pw", context.resolvePropertyPlaceholders("{{hashicorp:secret:tls-it#password}}"));
        }
    }

    @Test
    void endpointTrustsVaultWithTheTrustStore() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();

            String uri = "hashicorp-vault:secret?operation=getSecret&secretPath=tls-it&host=" + service.host() + "&port="
                         + service.port() + "&scheme=https&token=RAW(" + service.token() + ")&trustStore=" + vaultCa;
            Object body = context.createProducerTemplate().requestBody(uri, "");

            assertEquals(Map.of("password", "pw"), ((Map<?, ?>) body).get("data"));
        }
    }

    /**
     * Vault in development mode with a TLS listener: its CA certificate is /tmp/vault-ca.pem in the container. OpenBao
     * names it the same.
     */
    static class TlsVaultService extends HashicorpVaultLocalContainerInfraService implements HashicorpVaultService {

        @Override
        protected GenericContainer<?> initContainer(String imageName, String containerName) {
            return super.initContainer(imageName, containerName)
                    .withCommand("server", "-dev", "-dev-tls", "-dev-tls-cert-dir=/tmp");
        }

        @Override
        public String scheme() {
            return "https";
        }
    }
}
