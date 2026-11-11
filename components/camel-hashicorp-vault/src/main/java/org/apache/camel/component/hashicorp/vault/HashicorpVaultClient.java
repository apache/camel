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

import java.io.Closeable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.util.ObjectHelper;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.vault.authentication.AppRoleAuthentication;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.RoleId;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.SecretId;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.ClientCertificateAuthentication;
import org.springframework.vault.authentication.ClientCertificateAuthenticationOptions;
import org.springframework.vault.authentication.JwtAuthentication;
import org.springframework.vault.authentication.JwtAuthenticationOptions;
import org.springframework.vault.authentication.KubernetesAuthentication;
import org.springframework.vault.authentication.KubernetesAuthenticationOptions;
import org.springframework.vault.authentication.KubernetesServiceAccountTokenFile;
import org.springframework.vault.authentication.LifecycleAwareSessionManager;
import org.springframework.vault.authentication.ResourceCredentialSupplier;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.ClientOptions;

/**
 * A client of HashiCorp Vault that logs in as its {@link HashicorpVaultClientSettings} say, and keeps its token valid:
 * it renews the token before the token expires, and logs in again when the token can no longer be renewed. Closing the
 * client stops the renewal and revokes the token it logged in with. A token given for the token login is renewed too,
 * but never revoked.
 * <p/>
 * The endpoint, the properties function and the secret refresh task each create their client with
 * {@link #create(CamelContext, Object, HashicorpVaultClientSettings)}, and close it when they stop.
 */
public final class HashicorpVaultClient implements Closeable {

    private final VaultTemplate vaultTemplate;
    private final LifecycleAwareSessionManager sessionManager;
    private final ExecutorServiceManager executorServiceManager;
    private final ScheduledExecutorService renewal;

    private HashicorpVaultClient(VaultTemplate vaultTemplate, LifecycleAwareSessionManager sessionManager,
                                 ExecutorServiceManager executorServiceManager, ScheduledExecutorService renewal) {
        this.vaultTemplate = vaultTemplate;
        this.sessionManager = sessionManager;
        this.executorServiceManager = executorServiceManager;
        this.renewal = renewal;
    }

    /**
     * Creates a client. It logs in at its first request to Vault.
     *
     * @param  camelContext             the context, whose executor service manager runs the renewal of the token
     * @param  source                   the owner of the client, which names the thread that renews the token
     * @param  settings                 where Vault is and how to log in to it
     * @return                          the client, to close when it is no longer used
     * @throws IllegalArgumentException when the settings are incomplete or not valid
     */
    public static HashicorpVaultClient create(CamelContext camelContext, Object source, HashicorpVaultClientSettings settings) {
        validate(settings);
        VaultEndpoint endpoint = new VaultEndpoint();
        endpoint.setHost(settings.getHost());
        endpoint.setPort(Integer.parseInt(settings.getPort()));
        endpoint.setScheme(settings.getScheme());

        SimpleClientHttpRequestFactory requestFactory = requestFactory(HashicorpVaultTls.sslContext(settings));
        VaultClient client = vaultClient(endpoint, requestFactory, null);
        // on HashiCorp Cloud the login methods are in the namespace, while Camel reads the secrets with the namespace in
        // their path: so only the login, and the renewal and revocation of its token, are sent to the namespace
        String namespace = settings.getCloudNamespace();
        VaultClient loginClient = namespace != null ? vaultClient(endpoint, requestFactory, namespace) : client;
        ClientAuthentication authentication = createAuthentication(settings, loginClient);

        ExecutorServiceManager executorServiceManager = camelContext.getExecutorServiceManager();
        ScheduledExecutorService renewal
                = executorServiceManager.newSingleThreadScheduledExecutor(source, "HashicorpVaultTokenRenewal");
        try {
            LifecycleAwareSessionManager sessionManager
                    = new LifecycleAwareSessionManager(authentication, new ConcurrentTaskScheduler(renewal), loginClient);
            return new HashicorpVaultClient(
                    new VaultTemplate(client, sessionManager), sessionManager, executorServiceManager, renewal);
        } catch (RuntimeException e) {
            // nothing is scheduled on the executor yet, so there is nothing to wait for
            executorServiceManager.shutdownNow(renewal);
            throw e;
        }
    }

    /**
     * Checks that the settings have what their login needs.
     *
     * @throws IllegalArgumentException when the settings are incomplete or not valid
     */
    public static void validate(HashicorpVaultClientSettings settings) {
        if (ObjectHelper.isEmpty(settings.getHost()) || ObjectHelper.isEmpty(settings.getPort())
                || ObjectHelper.isEmpty(settings.getScheme())) {
            throw new IllegalArgumentException("The host, port and scheme of HashiCorp Vault must be set");
        }
        try {
            Integer.parseInt(settings.getPort());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("The port of HashiCorp Vault must be a number: " + settings.getPort(), e);
        }
        switch (HashicorpVaultAuthentication.of(settings.getAuthentication())) {
            case TOKEN -> {
                if (ObjectHelper.isEmpty(settings.getToken())) {
                    throw new IllegalArgumentException("The token login needs a token");
                }
            }
            case APPROLE -> {
                if (ObjectHelper.isEmpty(settings.getRoleId()) || ObjectHelper.isEmpty(settings.getSecretId())) {
                    throw new IllegalArgumentException("The approle login needs a role ID and a secret ID");
                }
            }
            case KUBERNETES -> {
                if (ObjectHelper.isEmpty(settings.getRole())) {
                    throw new IllegalArgumentException("The kubernetes login needs a role");
                }
                validateJwt(settings, "kubernetes");
            }
            case JWT -> {
                if (ObjectHelper.isEmpty(settings.getJwt()) && ObjectHelper.isEmpty(settings.getJwtFile())) {
                    throw new IllegalArgumentException("The jwt login needs a JWT, or a file to read it from");
                }
                validateJwt(settings, "jwt");
            }
            case CERT -> {
                if (ObjectHelper.isEmpty(settings.getKeyStore())) {
                    throw new IllegalArgumentException("The cert login needs a key store with the client certificate");
                }
                if (!"https".equalsIgnoreCase(settings.getScheme())) {
                    // the client certificate is presented in the TLS handshake
                    throw new IllegalArgumentException("The cert login needs the https scheme");
                }
            }
            default -> throw new IllegalStateException("Unexpected login: " + settings.getAuthentication());
        }
    }

    private static void validateJwt(HashicorpVaultClientSettings settings, String login) {
        if (ObjectHelper.isNotEmpty(settings.getJwt()) && ObjectHelper.isNotEmpty(settings.getJwtFile())) {
            throw new IllegalArgumentException(
                    "The " + login + " login takes a JWT or a file to read it from, not both");
        }
    }

    /**
     * The login of the settings, which logs in with the given client.
     */
    static ClientAuthentication createAuthentication(HashicorpVaultClientSettings settings, VaultClient loginClient) {
        return switch (HashicorpVaultAuthentication.of(settings.getAuthentication())) {
            case TOKEN -> new TokenAuthentication(settings.getToken());
            case APPROLE -> new AppRoleAuthentication(appRoleOptions(settings), loginClient);
            case KUBERNETES -> new KubernetesAuthentication(kubernetesOptions(settings), loginClient);
            case JWT -> new JwtAuthentication(jwtOptions(settings), loginClient);
            case CERT -> new ClientCertificateAuthentication(certOptions(settings), loginClient);
        };
    }

    static AppRoleAuthenticationOptions appRoleOptions(HashicorpVaultClientSettings settings) {
        AppRoleAuthenticationOptions.AppRoleAuthenticationOptionsBuilder builder = AppRoleAuthenticationOptions.builder()
                .roleId(RoleId.provided(settings.getRoleId()))
                .secretId(SecretId.provided(settings.getSecretId()));
        if (ObjectHelper.isNotEmpty(settings.getAuthMountPath())) {
            builder.path(settings.getAuthMountPath());
        }
        return builder.build();
    }

    static KubernetesAuthenticationOptions kubernetesOptions(HashicorpVaultClientSettings settings) {
        String file = ObjectHelper.isNotEmpty(settings.getJwtFile())
                ? settings.getJwtFile() : KubernetesServiceAccountTokenFile.DEFAULT_KUBERNETES_SERVICE_ACCOUNT_TOKEN_FILE;
        KubernetesAuthenticationOptions.KubernetesAuthenticationOptionsBuilder builder
                = KubernetesAuthenticationOptions.builder()
                        .role(settings.getRole())
                        .jwtSupplier(jwtSupplier(settings.getJwt(), file));
        if (ObjectHelper.isNotEmpty(settings.getAuthMountPath())) {
            builder.path(settings.getAuthMountPath());
        }
        return builder.build();
    }

    static JwtAuthenticationOptions jwtOptions(HashicorpVaultClientSettings settings) {
        JwtAuthenticationOptions.JwtAuthenticationOptionsBuilder builder = JwtAuthenticationOptions.builder()
                .jwtSupplier(jwtSupplier(settings.getJwt(), settings.getJwtFile()));
        if (ObjectHelper.isNotEmpty(settings.getRole())) {
            builder.role(settings.getRole());
        }
        if (ObjectHelper.isNotEmpty(settings.getAuthMountPath())) {
            builder.path(settings.getAuthMountPath());
        }
        return builder.build();
    }

    static ClientCertificateAuthenticationOptions certOptions(HashicorpVaultClientSettings settings) {
        ClientCertificateAuthenticationOptions.ClientCertificateAuthenticationOptionsBuilder builder
                = ClientCertificateAuthenticationOptions.builder();
        if (ObjectHelper.isNotEmpty(settings.getRole())) {
            builder.role(settings.getRole());
        }
        if (ObjectHelper.isNotEmpty(settings.getAuthMountPath())) {
            builder.path(settings.getAuthMountPath());
        }
        return builder.build();
    }

    /**
     * The JWT given, else the JWT in the file, read at every login, so that a token replaced in the file (such as a
     * projected service account token) is picked up.
     *
     * @throws IllegalArgumentException when the file does not exist
     */
    private static Supplier<String> jwtSupplier(String jwt, String file) {
        if (ObjectHelper.isNotEmpty(jwt)) {
            return () -> jwt;
        }
        ResourceCredentialSupplier content = new ResourceCredentialSupplier(file);
        // a JWT has no whitespace, while a file often ends with a line break
        return () -> content.get().trim();
    }

    /**
     * The HTTP client that Spring Vault uses for a VaultTemplate of an endpoint, with the timeouts it applies by
     * default, so that no request to Vault (such as the revocation of the token on stop) waits forever, and with the
     * TLS context of the trust store and key store when set.
     */
    private static SimpleClientHttpRequestFactory requestFactory(SSLContext sslContext) {
        SimpleClientHttpRequestFactory requestFactory;
        if (sslContext == null) {
            requestFactory = new SimpleClientHttpRequestFactory();
        } else {
            // one socket factory for all connections, which the JDK then keeps alive and reuses
            SSLSocketFactory socketFactory = sslContext.getSocketFactory();
            requestFactory = new SimpleClientHttpRequestFactory() {
                @Override
                protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                    super.prepareConnection(connection, httpMethod);
                    if (connection instanceof HttpsURLConnection https) {
                        https.setSSLSocketFactory(socketFactory);
                    }
                }
            };
        }
        ClientOptions options = new ClientOptions();
        requestFactory.setConnectTimeout(options.getConnectionTimeout());
        requestFactory.setReadTimeout(options.getReadTimeout());
        return requestFactory;
    }

    private static VaultClient vaultClient(
            VaultEndpoint endpoint, SimpleClientHttpRequestFactory requestFactory, String namespace) {
        VaultClient.Builder builder = VaultClient.builder().endpoint(endpoint).requestFactory(requestFactory);
        if (namespace != null) {
            builder.defaultNamespace(namespace);
        }
        return builder.build();
    }

    /**
     * The template to access Vault with, which logs in and renews its token as needed
     */
    public VaultTemplate getVaultTemplate() {
        return vaultTemplate;
    }

    /**
     * Stops renewing the token, and revokes the token of the login (but not a token given for the token login).
     */
    @Override
    public void close() {
        try {
            executorServiceManager.shutdownNow(renewal);
        } finally {
            sessionManager.destroy();
        }
    }
}
