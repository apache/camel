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
import org.apache.camel.component.milo.KeyStoreLoader;
import org.apache.camel.component.milo.server.internal.CamelNamespace;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfigBuilder;
import org.eclipse.milo.opcua.sdk.server.identity.AnonymousIdentityValidator;
import org.eclipse.milo.opcua.sdk.server.identity.IdentityValidator;
import org.eclipse.milo.opcua.sdk.server.identity.UsernameIdentityValidator;
import org.eclipse.milo.opcua.sdk.server.util.HostnameUtil;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.CertificateManager;
import org.eclipse.milo.opcua.stack.core.security.CertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.DefaultTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.UserTokenType;
import org.eclipse.milo.opcua.stack.core.types.structured.BuildInfo;
import org.eclipse.milo.opcua.stack.core.types.structured.UserTokenPolicy;
import org.eclipse.milo.opcua.stack.server.EndpointConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS;
import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.USER_TOKEN_POLICY_USERNAME;
import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.USER_TOKEN_POLICY_X509;

/**
 * OPC UA Server based component
 */
@Component("milo-server")
public class MiloServerComponent extends DefaultComponent {
    public static final String DEFAULT_NAMESPACE_URI = "urn:org:apache:camel";

    private static final Logger LOG = LoggerFactory.getLogger(MiloServerComponent.class);

    private static final String URL_CHARSET = "UTF-8";

    private final List<Runnable> runOnStop = new LinkedList<>();

    private int port;
    private String namespaceUri = DEFAULT_NAMESPACE_URI;
    private OpcUaServerConfigBuilder opcServerConfig;
    private OpcUaServer server;
    private CamelNamespace namespace;
    private Boolean enableAnonymousAuthentication;
    private CertificateManager certificateManager;
    private String securityPoliciesById;
    private Set<SecurityPolicy> securityPolicies;
    private String userAuthenticationCredentials;
    private String usernameSecurityPolicyUri = OpcUaServerConfig.USER_TOKEN_POLICY_USERNAME.getSecurityPolicyUri();
    private List<String> bindAddresses;
    private String defaultCertificateValidator;
    private CertificateValidator certificateValidator;
    private X509Certificate certificate;
    private String productUri;
    private String applicationUri;
    private String applicationName;
    private String path;
    private BuildInfo buildInfo;

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
        this.server = new OpcUaServer(buildServerConfig());

        this.namespace = new CamelNamespace(this.namespaceUri, this.server);
        this.namespace.startup();

        super.doStart();
        this.server.startup();
    }

    /**
     * Build the final server configuration, apply all complex configuration
     *
     * @return the new server configuration, never returns {@code null}
     */
    private OpcUaServerConfig buildServerConfig() {
        OpcUaServerConfigBuilder serverConfig = this.opcServerConfig  != null ? this.opcServerConfig : createDefaultConfiguration();

        this.securityPolicies = createSecurityPolicies();

        Map<String, String> userMap = createUserMap();
        if (!userMap.isEmpty() || enableAnonymousAuthentication != null) {
            // set identity validator
            final boolean allowAnonymous = Boolean.TRUE.equals(this.enableAnonymousAuthentication);
            final IdentityValidator identityValidator = new UsernameIdentityValidator(allowAnonymous, challenge -> {
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
            serverConfig.setEndpoints(createEndpointConfigurations(tokenPolicies));
        } else {
            serverConfig.setEndpoints(createEndpointConfigurations(null, securityPolicies));
        }

        if (certificateValidator != null) {
            LOG.debug("Using validator: {}", certificateValidator);
            if (certificateValidator instanceof Closeable) {
                runOnStop(() -> {
                    try {
                        LOG.debug("Closing: {}", certificateValidator);
                        ((Closeable)certificateValidator).close();
                    } catch (IOException e) {
                        LOG.debug("Failed to close. This exception is ignored.", e);
                    }
                });
            }
            serverConfig.setCertificateValidator(certificateValidator);
        }

        // build final configuration
        return serverConfig.build();
    }

    private OpcUaServerConfigBuilder createDefaultConfiguration() {
        final OpcUaServerConfigBuilder cfg = OpcUaServerConfig.builder();

        cfg.setCertificateManager(new DefaultCertificateManager());
        cfg.setCertificateValidator(DenyAllCertificateValidator.INSTANCE);
        cfg.setEndpoints(createEndpointConfigurations(null));
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

    private Set<EndpointConfiguration> createEndpointConfigurations(List<UserTokenPolicy> userTokenPolicies) {
        return createEndpointConfigurations(userTokenPolicies, securityPolicies);
    }

    private Set<EndpointConfiguration> createEndpointConfigurations(List<UserTokenPolicy> userTokenPolicies, Set<SecurityPolicy> securityPolicies) {
        Set<EndpointConfiguration> endpointConfigurations = new LinkedHashSet<>();

        //if address is not defined, return empty set
        if (bindAddresses == null) {
            return Collections.emptySet();
        }

        for (String bindAddress : bindAddresses) {
            Set<String> hostnames = new LinkedHashSet<>();
            hostnames.add(HostnameUtil.getHostname());
            hostnames.addAll(HostnameUtil.getHostnames(bindAddress));

            boolean anonymous = (this.enableAnonymousAuthentication != null && this.enableAnonymousAuthentication)
                    || Boolean.getBoolean("org.apache.camel.milo.server.default.enableAnonymous");

            UserTokenPolicy[] tokenPolicies =
                    userTokenPolicies != null ? userTokenPolicies.toArray(new UserTokenPolicy[userTokenPolicies.size()])
                            : anonymous
                                ? new UserTokenPolicy[] {USER_TOKEN_POLICY_ANONYMOUS, USER_TOKEN_POLICY_USERNAME, USER_TOKEN_POLICY_X509}
                                : new UserTokenPolicy[] {USER_TOKEN_POLICY_USERNAME, USER_TOKEN_POLICY_X509};

            for (String hostname : hostnames) {
                EndpointConfiguration.Builder builder = EndpointConfiguration.newBuilder()
                        .setBindAddress(bindAddress)
                        .setHostname(hostname)
                        .setCertificate(certificate)
                        .setPath(this.path == null ? "" : this.path)
                        .addTokenPolicies(tokenPolicies);


                if (securityPolicies == null || securityPolicies.contains(SecurityPolicy.None)) {
                    EndpointConfiguration.Builder noSecurityBuilder = builder.copy()
                            .setSecurityPolicy(SecurityPolicy.None)
                            .setSecurityMode(MessageSecurityMode.None);

                    endpointConfigurations.add(buildTcpEndpoint(noSecurityBuilder));
                    endpointConfigurations.add(buildHttpsEndpoint(noSecurityBuilder));
                } else if (securityPolicies.contains(SecurityPolicy.Basic256Sha256)) {

                    // TCP Basic256Sha256 / SignAndEncrypt
                    endpointConfigurations.add(buildTcpEndpoint(
                            builder.copy()
                                    .setSecurityPolicy(SecurityPolicy.Basic256Sha256)
                                    .setSecurityMode(MessageSecurityMode.SignAndEncrypt))
                    );
                } else if (securityPolicies.contains(SecurityPolicy.Basic256Sha256)) {
                    // HTTPS Basic256Sha256 / Sign (SignAndEncrypt not allowed for HTTPS)
                    endpointConfigurations.add(buildHttpsEndpoint(
                            builder.copy()
                                    .setSecurityPolicy(SecurityPolicy.Basic256Sha256)
                                    .setSecurityMode(MessageSecurityMode.Sign))
                    );
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
                EndpointConfiguration.Builder discoveryBuilder = builder.copy()
                        .setPath("/discovery")
                        .setSecurityPolicy(SecurityPolicy.None)
                        .setSecurityMode(MessageSecurityMode.None);

                endpointConfigurations.add(buildTcpEndpoint(discoveryBuilder));
                endpointConfigurations.add(buildHttpsEndpoint(discoveryBuilder));
            }
        }

        return endpointConfigurations;
    }

    private EndpointConfiguration buildTcpEndpoint(EndpointConfiguration.Builder base) {
        return base.copy()
                .setTransportProfile(TransportProfile.TCP_UASC_UABINARY)
                .setBindPort(this.port)
                .build();
    }

    private EndpointConfiguration buildHttpsEndpoint(EndpointConfiguration.Builder base) {
        return base.copy()
                .setTransportProfile(TransportProfile.HTTPS_UABINARY)
                .setBindPort(this.port)
                .build();
    }

    private static final class DenyAllCertificateValidator implements CertificateValidator {
        public static final CertificateValidator INSTANCE = new DenyAllCertificateValidator();

        private DenyAllCertificateValidator() {
        }

        @Override
        public void validate(final X509Certificate certificate) throws UaException {
            throw new UaException(StatusCodes.Bad_CertificateUseNotAllowed);
        }

        @Override
        public void verifyTrustChain(List<X509Certificate> certificateChain) throws UaException {
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

    private Map createUserMap() {
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
    protected Endpoint createEndpoint(final String uri, final String remaining, final Map<String, Object> parameters) throws Exception {
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
        setCertificateManager(new DefaultCertificateManager(keyPair, certificate));
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
                final SecurityPolicy policy = SecurityPolicy.fromUriSafe(policyName).orElseGet(() -> SecurityPolicy.valueOf(policyName));
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
     * Set user password combinations in the form of "user1:pwd1,user2:pwd2"
     * Usernames and passwords will be URL decoded
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
        this.certificateManager = certificateManager != null ? certificateManager : new DefaultCertificateManager();
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
            DefaultTrustListManager trustListManager = new DefaultTrustListManager(new File(defaultCertificateValidator));
            this.certificateValidator = new DefaultCertificateValidator(trustListManager);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
