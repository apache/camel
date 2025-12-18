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
package org.apache.camel.component.milo.server;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.milo.KeyStoreLoader;
import org.apache.camel.component.milo.server.internal.CamelNamespace;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.eclipse.milo.opcua.sdk.server.EndpointConfig;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.OpcUaServerConfigBuilder;
import org.eclipse.milo.opcua.sdk.server.identity.AnonymousIdentityValidator;
import org.eclipse.milo.opcua.sdk.server.identity.IdentityValidator;
import org.eclipse.milo.opcua.sdk.server.identity.UsernameIdentityValidator;
import org.eclipse.milo.opcua.sdk.server.util.HostnameUtil;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.*;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.UserTokenType;
import org.eclipse.milo.opcua.stack.core.types.structured.BuildInfo;
import org.eclipse.milo.opcua.stack.core.types.structured.UserTokenPolicy;
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransport;
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransportConfig;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OPC UA Server based component
 */
@Component("milo-server")
public class MiloServerComponent extends DefaultComponent {
    public static final String DEFAULT_NAMESPACE_URI = "urn:org:apache:camel";

    private static final Logger LOG = LoggerFactory.getLogger(MiloServerComponent.class);

    private static final String URL_CHARSET = "UTF-8";

    private final List<Runnable> runOnStop = new LinkedList<>();

    private OpcUaServerConfigBuilder opcServerConfig;
    private OpcUaServer server;
    private CamelNamespace namespace;

    @Metadata
    private int port;
    @Metadata
    private List<String> bindAddresses;
    @Metadata(defaultValue = "" + DEFAULT_NAMESPACE_URI)
    private String namespaceUri = DEFAULT_NAMESPACE_URI;
    @Metadata
    private String productUri;
    @Metadata
    private String applicationUri;
    @Metadata
    private String applicationName;
    @Metadata
    private String path;
    @Metadata
    private BuildInfo buildInfo;
    @Metadata(label = "security")
    private Boolean enableAnonymousAuthentication;
    @Metadata(label = "security")
    private CertificateManager certificateManager;
    @Metadata(label = "security")
    private String securityPoliciesById;
    @Metadata(label = "security")
    private Set<SecurityPolicy> securityPolicies;
    @Metadata(label = "security", secret = true)
    private String userAuthenticationCredentials;
    @Metadata(label = "security")
    private String usernameSecurityPolicyUri = OpcUaServerConfig.USER_TOKEN_POLICY_USERNAME.getSecurityPolicyUri();
    @Metadata(label = "security")
    private String defaultCertificateValidator;
    @Metadata(label = "security")
    private CertificateValidator certificateValidator;
    @Metadata(label = "security")
    private X509Certificate certificate;

    // FIXME - extract CertificateQuarantine
    @Metadata(label = "security")
    private CertificateQuarantine certificateQuarantine;
    // FIXME - migration to CertificateGroup
    @Metadata(label = "security")
    private CertificateGroup certificateGroup;

    public MiloServerComponent() {
        this.opcServerConfig = null;
    }

    public MiloServerComponent(final OpcUaServerConfig serverConfig) {
        this.opcServerConfig = OpcUaServerConfig.copy(serverConfig);
    }

    public CamelNamespace getNamespace() {
        return namespace;
    }

    @Override
    protected void doStart() throws Exception {
        // https://github.com/eclipse-milo/milo/blob/1.0/milo-examples/server-examples/src/main/java/org/eclipse/milo/examples/server/ExampleServer.java
        OpcUaServerConfig serverConfig = buildServerConfig();
        this.server = new OpcUaServer(
                serverConfig,
                transportProfile -> {
                    if (transportProfile == TransportProfile.TCP_UASC_UABINARY
                            || transportProfile == TransportProfile.HTTPS_UABINARY) {
                        OpcTcpServerTransportConfig transportConfig = OpcTcpServerTransportConfig.newBuilder().build();
                        return new OpcTcpServerTransport(transportConfig);
                    }
                    throw new UnsupportedOperationException("Unsupported transport profile: " + transportProfile);
                });

        this.namespace = new CamelNamespace(this.namespaceUri, this.server);
        this.namespace.startup();

        super.doStart();
        this.server.startup().get();
    }

    /**
     * Build the final server configuration, apply all complex configuration
     *
     * @return the new server configuration, never returns {@code null}
     */
    private OpcUaServerConfig buildServerConfig() {

        //        this.certificateGroup = createCertificateGroup();

        OpcUaServerConfigBuilder serverConfig
                = this.opcServerConfig != null ? this.opcServerConfig : createDefaultConfiguration();

        this.securityPolicies = createSecurityPolicies();

        Map<String, String> userMap = createUserMap();
        if (!userMap.isEmpty() || enableAnonymousAuthentication != null) {
            // set identity validator
            final boolean allowAnonymous = Boolean.TRUE.equals(this.enableAnonymousAuthentication);
            final IdentityValidator identityValidator = new UsernameIdentityValidator(challenge -> {
                final String pwd = userMap.get(challenge.getUsername());
                if (pwd == null) {
                    return false;
                }
                return pwd.equals(challenge.getPassword());
            });
            serverConfig.setIdentityValidator(identityValidator);

            // add token policies
            final List<UserTokenPolicy> tokenPolicies = new LinkedList<>();
            if (allowAnonymous) {
                tokenPolicies.add(OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS);
            }
            if (!userMap.isEmpty()) {
                tokenPolicies.add(getUsernamePolicy());
            }
            serverConfig.setEndpoints(createEndpointConfigs(tokenPolicies));
        } else {
            serverConfig.setEndpoints(createEndpointConfigs(null, securityPolicies));
        }

        if (certificateValidator != null) {
            LOG.debug("Using validator: {}", certificateValidator);
            if (certificateValidator instanceof Closeable) {
                runOnStop(() -> {
                    try {
                        LOG.debug("Closing: {}", certificateValidator);
                        ((Closeable) certificateValidator).close();
                    } catch (IOException e) {
                        LOG.debug("Failed to close. This exception is ignored.", e);
                    }
                });
            }
            //            serverConfig.setCertificateValidator(certificateValidator);
            //            // FIXME - moved to certificateManager
            serverConfig.setCertificateManager(
                    new DefaultCertificateManager(this.certificateQuarantine, this.certificateGroup));
        }

        // build final configuration
        return serverConfig.build();
    }

    private OpcUaServerConfigBuilder createDefaultConfiguration() {
        final OpcUaServerConfigBuilder cfg = OpcUaServerConfig.builder();

        //        cfg.setCertificateManager(new DefaultCertificateManager()); // TODO there is already der CertificateManager intialization below
        //        cfg.setCertificateValidator(DenyAllCertificateValidator.INSTANCE); // TODO moved to createCertificateGroup
        cfg.setEndpoints(createEndpointConfigs(null));
        cfg.setApplicationName(LocalizedText.english(applicationName == null ? "Apache Camel Milo Server" : applicationName));
        cfg.setApplicationUri("urn:org:apache:camel:milo:server");
        cfg.setProductUri("urn:org:apache:camel:milo");
        cfg.setCertificateManager(certificateManager);

        if (productUri != null) {
            cfg.setProductUri(productUri);
        }
        if (applicationUri != null) {
            cfg.setApplicationUri(applicationUri);
        }
        if (buildInfo != null) {
            cfg.setBuildInfo(buildInfo);
        }

        if (Boolean.getBoolean("org.apache.camel.milo.server.default.enableAnonymous")) {
            cfg.setIdentityValidator(AnonymousIdentityValidator.INSTANCE);
        }

        return cfg;
    }

    private Set<EndpointConfig> createEndpointConfigs(List<UserTokenPolicy> userTokenPolicies) {
        return createEndpointConfigs(userTokenPolicies, securityPolicies);
    }

    private Set<EndpointConfig> createEndpointConfigs(
            List<UserTokenPolicy> userTokenPolicies, Set<SecurityPolicy> securityPolicies) {
        Set<EndpointConfig> endpointConfigs = new LinkedHashSet<>();

        //if address is not defined, use localhost as default
        if (bindAddresses == null) {
            bindAddresses = Arrays.asList("localhost");
        }

        for (String bindAddress : bindAddresses) {
            Set<String> hostnames = new LinkedHashSet<>();
            hostnames.add(HostnameUtil.getHostname());
            hostnames.addAll(HostnameUtil.getHostnames(bindAddress));

            boolean anonymous = this.enableAnonymousAuthentication != null && this.enableAnonymousAuthentication
                    || Boolean.getBoolean("org.apache.camel.milo.server.default.enableAnonymous");

            UserTokenPolicy[] tokenPolicies
                    = userTokenPolicies != null ? userTokenPolicies.toArray(new UserTokenPolicy[userTokenPolicies.size()])
                            : anonymous
                                    ? new UserTokenPolicy[] {
                                            OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS,
                                            OpcUaServerConfig.USER_TOKEN_POLICY_USERNAME,
                                            OpcUaServerConfig.USER_TOKEN_POLICY_X509
                                    }
                            : new UserTokenPolicy[] {
                                    OpcUaServerConfig.USER_TOKEN_POLICY_USERNAME, OpcUaServerConfig.USER_TOKEN_POLICY_X509 };

            for (String hostname : hostnames) {
                EndpointConfig.Builder builder = EndpointConfig.newBuilder()
                        .setBindAddress(bindAddress)
                        .setHostname(hostname)
                        .setCertificate(certificate)
                        .setPath(this.path == null ? "" : this.path)
                        .addTokenPolicies(tokenPolicies);

                if (securityPolicies == null || securityPolicies.contains(SecurityPolicy.None)) {
                    EndpointConfig.Builder noSecurityBuilder = builder.copy()
                            .setSecurityPolicy(SecurityPolicy.None)
                            .setSecurityMode(MessageSecurityMode.None);

                    endpointConfigs.add(buildTcpEndpoint(noSecurityBuilder));
                    endpointConfigs.add(buildHttpsEndpoint(noSecurityBuilder));
                } else if (securityPolicies.contains(SecurityPolicy.Basic256Sha256)) {

                    // TCP Basic256Sha256 / SignAndEncrypt
                    endpointConfigs.add(buildTcpEndpoint(
                            builder.copy()
                                    .setSecurityPolicy(SecurityPolicy.Basic256Sha256)
                                    .setSecurityMode(MessageSecurityMode.SignAndEncrypt)));
                } else if (securityPolicies.contains(SecurityPolicy.Basic256Sha256)) {
                    // HTTPS Basic256Sha256 / Sign (SignAndEncrypt not allowed for HTTPS)
                    endpointConfigs.add(buildHttpsEndpoint(
                            builder.copy()
                                    .setSecurityPolicy(SecurityPolicy.Basic256Sha256)
                                    .setSecurityMode(MessageSecurityMode.Sign)));
                }

                /*
                 * It's good practice to provide a discovery-specific endpoint with no security.
                 * It's required practice if all regular endpoints have security configured.
                 *
                 * Usage of the  "/discovery" suffix is defined by OPC UA Part 6:
                 *
                 * Each OPC UA Server Application implements the Discovery Service Set. If the OPC UA Server requires a
                 * different address for this Endpoint it shall create the address by appending the path "/discovery" to
                 * its base address.
                 */
                EndpointConfig.Builder discoveryBuilder = builder.copy()
                        .setPath("/discovery")
                        .setSecurityPolicy(SecurityPolicy.None)
                        .setSecurityMode(MessageSecurityMode.None);

                endpointConfigs.add(buildTcpEndpoint(discoveryBuilder));
                endpointConfigs.add(buildHttpsEndpoint(discoveryBuilder));
            }
        }

        return endpointConfigs;
    }

    private EndpointConfig buildTcpEndpoint(EndpointConfig.Builder base) {
        return base.copy()
                .setTransportProfile(TransportProfile.TCP_UASC_UABINARY)
                .setBindPort(this.port)
                .build();
    }

    private EndpointConfig buildHttpsEndpoint(EndpointConfig.Builder base) {
        return base.copy()
                .setTransportProfile(TransportProfile.HTTPS_UABINARY)
                .setBindPort(this.port)
                .build();
    }

    // May be useful in the future although unused now.
    @SuppressWarnings("unused")
    private static final class DenyAllCertificateValidator implements CertificateValidator {
        @SuppressWarnings("unused")
        public static final CertificateValidator INSTANCE = new DenyAllCertificateValidator();

        private DenyAllCertificateValidator() {
        }

        @Override
        public void validateCertificateChain(
                List<X509Certificate> certificateChain, @Nullable String applicationUri, @Nullable String[] validHostnames)
                throws UaException {
            throw new UaException(StatusCodes.Bad_CertificateUseNotAllowed);
        }
    }

    /**
     * Get the user token policy for using with username authentication
     *
     * @return the user token policy to use for username authentication
     */
    private UserTokenPolicy getUsernamePolicy() {
        if (this.usernameSecurityPolicyUri == null || this.usernameSecurityPolicyUri.isEmpty()) {
            return OpcUaServerConfig.USER_TOKEN_POLICY_USERNAME;
        }
        return new UserTokenPolicy("username", UserTokenType.UserName, null, null, this.usernameSecurityPolicyUri);
    }

    private void runOnStop(final Runnable runnable) {
        this.runOnStop.add(runnable);
    }

    private Map<String, String> createUserMap() {
        Map<String, String> userMap = null;
        if (userAuthenticationCredentials != null) {
            userMap = new HashMap<>();

            for (final String creds : userAuthenticationCredentials.split(",")) {
                final String[] toks = creds.split(":", 2);
                if (toks.length == 2) {
                    try {
                        userMap.put(URLDecoder.decode(toks[0], URL_CHARSET), URLDecoder.decode(toks[1], URL_CHARSET));
                    } catch (final UnsupportedEncodingException e) {
                        LOG.warn("Failed to decode user map entry", e);
                    }
                }
            }
        }
        return userMap != null ? userMap : Collections.emptyMap();
    }

    @Override
    protected void doStop() throws Exception {
        if (this.server != null) {
            this.server.shutdown();
        }
        super.doStop();

        this.runOnStop.forEach(runnable -> {
            try {
                runnable.run();
            } catch (final Exception e) {
                LOG.warn("Failed to run on stop", e);
            }
        });
        this.runOnStop.clear();
    }

    @Override
    protected Endpoint createEndpoint(final String uri, final String remaining, final Map<String, Object> parameters)
            throws Exception {
        Endpoint endpoint = new MiloServerEndpoint(uri, remaining, this);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    /**
     * Server certificate
     */
    public void loadServerCertificate(final KeyStoreLoader.Result result) {
        /*
         * We are not implicitly deactivating the server certificate manager. If
         * the key could not be found by the KeyStoreLoader, it will return
         * "null" from the load() method. So if someone calls
         * setServerCertificate ( loader.load () ); he may, by accident, disable
         * the server certificate. If disabling the server certificate is
         * desired, do it explicitly.
         */
        Objects.requireNonNull(result, "Setting a null is not supported. call setCertificateManager(null) instead.)");
        loadServerCertificate(result.getKeyPair(), result.getCertificate());
    }

    /**
     * Server certificate
     */
    public void loadServerCertificate(final KeyPair keyPair, final X509Certificate certificate) {
        this.certificate = certificate;
        // TODO evaluate migration to CertificateGroup
        //        setCertificateManager(new DefaultCertificateManager(keyPair, certificate));
        if (this.certificateGroup != null) {
            try {
                this.certificateGroup.updateCertificate(
                        NodeIds.ServerConfiguration_CertificateGroups_DefaultApplicationGroup,
                        keyPair,
                        new X509Certificate[] { certificate });
            } catch (Exception e) {
                throw new RuntimeCamelException(e);
            }
        }
    }

    /**
     * Server certificate
     */
    public void setCertificate(X509Certificate certificate) {
        this.certificate = certificate;
    }

    private Set<SecurityPolicy> createSecurityPolicies() {
        if (securityPoliciesById != null) {
            String[] ids = securityPoliciesById.split(",");
            final EnumSet<SecurityPolicy> policies = EnumSet.noneOf(SecurityPolicy.class);

            for (final String policyName : ids) {
                final SecurityPolicy policy
                        = SecurityPolicy.fromUriSafe(policyName).orElseGet(() -> SecurityPolicy.valueOf(policyName));
                policies.add(policy);
            }

            if (this.securityPolicies == null) {
                this.securityPolicies = new HashSet<>();
            }
            this.securityPolicies.addAll(policies);
        }
        return this.securityPolicies;
    }

    /**
     * The URI of the namespace, defaults to <code>urn:org:apache:camel</code>
     */
    public void setNamespaceUri(final String namespaceUri) {
        this.namespaceUri = namespaceUri;
    }

    /**
     * The application name
     */
    public void setApplicationName(final String applicationName) {
        Objects.requireNonNull(applicationName);
        this.applicationName = applicationName;
    }

    /**
     * The path to be appended to the end of the endpoint url. (doesn't need to start with '/')
     */
    public void setPath(final String path) {
        Objects.requireNonNull(path);
        this.path = path;
    }

    /**
     * The application URI
     */
    public void setApplicationUri(final String applicationUri) {
        Objects.requireNonNull(applicationUri);
        this.applicationUri = applicationUri;
    }

    /**
     * The product URI
     */
    public void setProductUri(final String productUri) {
        Objects.requireNonNull(productUri);
        this.productUri = productUri;
    }

    /**
     * The TCP port the server binds to
     */
    public void setPort(final int port) {
        this.port = port;
    }

    /**
     * Security policies
     */
    public void setSecurityPolicies(final Set<SecurityPolicy> securityPolicies) {
        if (securityPolicies == null || securityPolicies.isEmpty()) {
            this.securityPolicies = EnumSet.noneOf(SecurityPolicy.class);
        } else {
            this.securityPolicies = EnumSet.copyOf(securityPolicies);
        }
        // clear id as we set explicit these policies
        this.securityPoliciesById = null;
    }

    /**
     * Security policies by URI or name. Multiple policies can be separated by comma.
     */
    public void setSecurityPoliciesById(String securityPoliciesById) {
        this.securityPoliciesById = securityPoliciesById;
    }

    public String getSecurityPoliciesById() {
        return securityPoliciesById;
    }

    /**
     * Set user password combinations in the form of "user1:pwd1,user2:pwd2" Usernames and passwords will be URL decoded
     */
    public void setUserAuthenticationCredentials(final String userAuthenticationCredentials) {
        this.userAuthenticationCredentials = userAuthenticationCredentials;
    }

    public String getUserAuthenticationCredentials() {
        return userAuthenticationCredentials;
    }

    /**
     * Enable anonymous authentication, disabled by default
     */
    public void setEnableAnonymousAuthentication(final boolean enableAnonymousAuthentication) {
        this.enableAnonymousAuthentication = enableAnonymousAuthentication;
    }

    /**
     * Set the {@link UserTokenPolicy} used when
     */
    public void setUsernameSecurityPolicyUri(final SecurityPolicy usernameSecurityPolicy) {
        this.usernameSecurityPolicyUri = usernameSecurityPolicy.getUri();
    }

    /**
     * Set the {@link UserTokenPolicy} used when
     */
    public void setUsernameSecurityPolicyUri(String usernameSecurityPolicyUri) {
        this.usernameSecurityPolicyUri = usernameSecurityPolicyUri;
    }

    /**
     * Set the addresses of the local addresses the server should bind to
     */
    public void setBindAddresses(final String bindAddresses) {
        if (bindAddresses != null) {
            this.bindAddresses = Arrays.asList(bindAddresses.split(","));
        } else {
            this.bindAddresses = null;
        }
    }

    /**
     * Server build info
     */
    public void setBuildInfo(final BuildInfo buildInfo) {
        this.buildInfo = buildInfo;
    }

    /**
     * Server certificate manager
     */
    public void setCertificateManager(final CertificateManager certificateManager) {
        this.certificateManager
                = certificateManager != null ? certificateManager : new DefaultCertificateManager(this.certificateQuarantine);
    }

    /**
     * Validator for client certificates
     */
    public void setCertificateValidator(final CertificateValidator certificateValidator) {
        this.certificateValidator = certificateValidator;
    }

    /**
     * Validator for client certificates using default file based approach
     */
    public void setDefaultCertificateValidator(final String defaultCertificateValidator) {
        this.defaultCertificateValidator = defaultCertificateValidator;
        try {
            final Path certificateConfigurationPath = new File(defaultCertificateValidator).toPath();
            TrustListManager trustListManager = FileBasedTrustListManager.createAndInitialize(certificateConfigurationPath);

            this.certificateQuarantine = this.createCertificateQuarantine(certificateConfigurationPath);

            this.certificateValidator = new DefaultServerCertificateValidator(trustListManager, certificateQuarantine);

            this.certificateGroup = DefaultApplicationGroup.createAndInitialize(
                    trustListManager,
                    new MemoryCertificateStore(), // alternative is KeyStoreCertificateStore
                    new RsaSha256CertificateFactory() {
                        @Override
                        protected X509Certificate[] createRsaSha256CertificateChain(KeyPair keyPair) {
                            return new X509Certificate[] { certificate };
                        }
                    },
                    certificateValidator);

        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    private CertificateQuarantine createCertificateQuarantine(Path certificateConfigurationPath)
            throws IOException {
        var certificateQuarantineDir = certificateConfigurationPath.resolve("rejected").resolve("certs");
        var certificateQuarantine = FileBasedCertificateQuarantine.create(certificateQuarantineDir);
        if (!Files.exists(certificateConfigurationPath)) {
            Files.createDirectories(certificateConfigurationPath);
        }

        return certificateQuarantine;
    }

    public String getDefaultCertificateValidator() {
        return defaultCertificateValidator;
    }

    public int getPort() {
        return port;
    }

    public String getNamespaceUri() {
        return namespaceUri;
    }

    public OpcUaServer getServer() {
        return server;
    }

    public Boolean isEnableAnonymousAuthentication() {
        return enableAnonymousAuthentication;
    }

    public CertificateManager getCertificateManager() {
        return certificateManager;
    }

    public Set<SecurityPolicy> getSecurityPolicies() {
        return securityPolicies;
    }

    public String getUsernameSecurityPolicyUri() {
        return usernameSecurityPolicyUri;
    }

    public List<String> getBindAddresses() {
        return bindAddresses;
    }

    public CertificateValidator getCertificateValidator() {
        return certificateValidator;
    }

    public X509Certificate getCertificate() {
        return certificate;
    }

    public String getProductUri() {
        return productUri;
    }

    public String getApplicationUri() {
        return applicationUri;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getPath() {
        return path;
    }

    public BuildInfo getBuildInfo() {
        return buildInfo;
    }
}
