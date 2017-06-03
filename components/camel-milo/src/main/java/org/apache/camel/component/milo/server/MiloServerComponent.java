/**
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import static java.util.Collections.singletonList;

import org.apache.camel.Endpoint;
import org.apache.camel.component.milo.KeyStoreLoader;
import org.apache.camel.component.milo.client.MiloClientConsumer;
import org.apache.camel.component.milo.server.internal.CamelNamespace;
import org.apache.camel.impl.DefaultComponent;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfigBuilder;
import org.eclipse.milo.opcua.sdk.server.identity.AnonymousIdentityValidator;
import org.eclipse.milo.opcua.sdk.server.identity.IdentityValidator;
import org.eclipse.milo.opcua.sdk.server.identity.UsernameIdentityValidator;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.application.CertificateManager;
import org.eclipse.milo.opcua.stack.core.application.CertificateValidator;
import org.eclipse.milo.opcua.stack.core.application.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.application.DefaultCertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.structured.BuildInfo;
import org.eclipse.milo.opcua.stack.core.types.structured.UserTokenPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS;

/**
 * OPC UA Server based component
 */
public class MiloServerComponent extends DefaultComponent {
    public static final String DEFAULT_NAMESPACE_URI = "urn:org:apache:camel";

    private static final Logger LOG = LoggerFactory.getLogger(MiloClientConsumer.class);
    private static final String URL_CHARSET = "UTF-8";
    private static final OpcUaServerConfig DEFAULT_SERVER_CONFIG;

    static {
        final OpcUaServerConfigBuilder cfg = OpcUaServerConfig.builder();

        cfg.setCertificateManager(new DefaultCertificateManager());
        cfg.setCertificateValidator(DenyAllCertificateValidator.INSTANCE);
        cfg.setSecurityPolicies(EnumSet.allOf(SecurityPolicy.class));
        cfg.setApplicationName(LocalizedText.english("Apache Camel Milo Server"));
        cfg.setApplicationUri("urn:org:apache:camel:milo:server");
        cfg.setProductUri("urn:org:apache:camel:milo");

        if (Boolean.getBoolean("org.apache.camel.milo.server.default.enableAnonymous")) {
            cfg.setUserTokenPolicies(singletonList(USER_TOKEN_POLICY_ANONYMOUS));
            cfg.setIdentityValidator(AnonymousIdentityValidator.INSTANCE);
        }

        DEFAULT_SERVER_CONFIG = cfg.build();
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
        public void verifyTrustChain(final X509Certificate certificate, final List<X509Certificate> chain) throws UaException {
            throw new UaException(StatusCodes.Bad_CertificateUseNotAllowed);
        }
    }

    private String namespaceUri = DEFAULT_NAMESPACE_URI;

    private final OpcUaServerConfigBuilder serverConfig;

    private OpcUaServer server;
    private CamelNamespace namespace;

    private final Map<String, MiloServerEndpoint> endpoints = new HashMap<>();

    private Boolean enableAnonymousAuthentication;

    private Map<String, String> userMap;

    private List<String> bindAddresses;

    private Supplier<CertificateValidator> certificateValidator;

    private final List<Runnable> runOnStop = new LinkedList<>();

    public MiloServerComponent() {
        this(DEFAULT_SERVER_CONFIG);
    }

    public MiloServerComponent(final OpcUaServerConfig serverConfig) {
        this.serverConfig = OpcUaServerConfig.copy(serverConfig != null ? serverConfig : DEFAULT_SERVER_CONFIG);
    }

    @Override
    protected void doStart() throws Exception {
        this.server = new OpcUaServer(buildServerConfig());

        this.namespace = this.server.getNamespaceManager().registerAndAdd(this.namespaceUri, index -> new CamelNamespace(index, this.namespaceUri, this.server));

        super.doStart();
        this.server.startup();
    }

    /**
     * Build the final server configuration, apply all complex configuration
     *
     * @return the new server configuration, never returns {@code null}
     */
    private OpcUaServerConfig buildServerConfig() {

        if (this.userMap != null || this.enableAnonymousAuthentication != null) {
            // set identity validator

            final Map<String, String> userMap = this.userMap != null ? new HashMap<>(this.userMap) : Collections.emptyMap();
            final boolean allowAnonymous = this.enableAnonymousAuthentication != null ? this.enableAnonymousAuthentication : false;
            final IdentityValidator identityValidator = new UsernameIdentityValidator(allowAnonymous, challenge -> {
                final String pwd = userMap.get(challenge.getUsername());
                if (pwd == null) {
                    return false;
                }
                return pwd.equals(challenge.getPassword());
            });
            this.serverConfig.setIdentityValidator(identityValidator);

            // add token policies

            final List<UserTokenPolicy> tokenPolicies = new LinkedList<>();
            if (Boolean.TRUE.equals(this.enableAnonymousAuthentication)) {
                tokenPolicies.add(OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS);
            }
            if (userMap != null) {
                tokenPolicies.add(OpcUaServerConfig.USER_TOKEN_POLICY_USERNAME);
            }
            this.serverConfig.setUserTokenPolicies(tokenPolicies);
        }

        if (this.bindAddresses != null) {
            this.serverConfig.setBindAddresses(new ArrayList<>(this.bindAddresses));
        }

        if (this.certificateValidator != null) {
            final CertificateValidator validator = this.certificateValidator.get();
            LOG.debug("Using validator: {}", validator);
            if (validator instanceof Closeable) {
                runOnStop(() -> {
                    try {
                        LOG.debug("Closing: {}", validator);
                        ((Closeable)validator).close();
                    } catch (final IOException e) {
                        LOG.warn("Failed to close", e);
                    }
                });
            }
            this.serverConfig.setCertificateValidator(validator);
        }

        // build final configuration

        return this.serverConfig.build();
    }

    private void runOnStop(final Runnable runnable) {
        this.runOnStop.add(runnable);
    }

    @Override
    protected void doStop() throws Exception {
        this.server.shutdown();
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
        synchronized (this) {
            if (remaining == null || remaining.isEmpty()) {
                return null;
            }

            MiloServerEndpoint endpoint = this.endpoints.get(remaining);

            if (endpoint == null) {
                endpoint = new MiloServerEndpoint(uri, remaining, this.namespace, this);
                setProperties(endpoint, parameters);
                this.endpoints.put(remaining, endpoint);
            }

            return endpoint;
        }
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
        this.serverConfig.setApplicationName(LocalizedText.english(applicationName));
    }

    /**
     * The application URI
     */
    public void setApplicationUri(final String applicationUri) {
        Objects.requireNonNull(applicationUri);
        this.serverConfig.setApplicationUri(applicationUri);
    }

    /**
     * The product URI
     */
    public void setProductUri(final String productUri) {
        Objects.requireNonNull(productUri);
        this.serverConfig.setProductUri(productUri);
    }

    /**
     * The TCP port the server binds to
     */
    public void setBindPort(final int port) {
        this.serverConfig.setBindPort(port);
    }

    /**
     * Set whether strict endpoint URLs are enforced
     */
    public void setStrictEndpointUrlsEnabled(final boolean strictEndpointUrlsEnforced) {
        this.serverConfig.setStrictEndpointUrlsEnabled(strictEndpointUrlsEnforced);
    }

    /**
     * Server name
     */
    public void setServerName(final String serverName) {
        this.serverConfig.setServerName(serverName);
    }

    /**
     * Server hostname
     */
    public void setHostname(final String hostname) {
        this.serverConfig.setHostname(hostname);
    }

    /**
     * Security policies
     */
    public void setSecurityPolicies(final Set<SecurityPolicy> securityPolicies) {
        if (securityPolicies == null || securityPolicies.isEmpty()) {
            this.serverConfig.setSecurityPolicies(EnumSet.noneOf(SecurityPolicy.class));
        } else {
            this.serverConfig.setSecurityPolicies(EnumSet.copyOf(securityPolicies));
        }
    }

    /**
     * Security policies by URI or name
     */
    public void setSecurityPoliciesById(final Collection<String> securityPolicies) {
        final EnumSet<SecurityPolicy> policies = EnumSet.noneOf(SecurityPolicy.class);

        if (securityPolicies != null) {
            for (final String policyName : securityPolicies) {
                final SecurityPolicy policy = SecurityPolicy.fromUriSafe(policyName).orElseGet(() -> SecurityPolicy.valueOf(policyName));
                policies.add(policy);
            }
        }

        this.serverConfig.setSecurityPolicies(policies);
    }

    /**
     * Security policies by URI or name
     */
    public void setSecurityPoliciesById(final String... ids) {
        if (ids != null) {
            setSecurityPoliciesById(Arrays.asList(ids));
        } else {
            setSecurityPoliciesById((Collection<String>)null);
        }
    }

    /**
     * Set user password combinations in the form of "user1:pwd1,user2:pwd2"
     * <p>
     * Usernames and passwords will be URL decoded
     * </p>
     */
    public void setUserAuthenticationCredentials(final String userAuthenticationCredentials) {
        if (userAuthenticationCredentials != null) {
            this.userMap = new HashMap<>();

            for (final String creds : userAuthenticationCredentials.split(",")) {
                final String[] toks = creds.split(":", 2);
                if (toks.length == 2) {
                    try {
                        this.userMap.put(URLDecoder.decode(toks[0], URL_CHARSET), URLDecoder.decode(toks[1], URL_CHARSET));
                    } catch (final UnsupportedEncodingException e) {
                        // FIXME: do log
                    }
                }
            }
        } else {
            this.userMap = null;
        }
    }

    /**
     * Enable anonymous authentication, disabled by default
     */
    public void setEnableAnonymousAuthentication(final boolean enableAnonymousAuthentication) {
        this.enableAnonymousAuthentication = enableAnonymousAuthentication;
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
        this.serverConfig.setBuildInfo(buildInfo);
    }

    /**
     * Server certificate
     */
    public void setServerCertificate(final KeyStoreLoader.Result result) {
        /*
         * We are not implicitly deactivating the server certificate manager. If
         * the key could not be found by the KeyStoreLoader, it will return
         * "null" from the load() method. So if someone calls
         * setServerCertificate ( loader.load () ); he may, by accident, disable
         * the server certificate. If disabling the server certificate is
         * desired, do it explicitly.
         */
        Objects.requireNonNull(result, "Setting a null is not supported. call setCertificateManager(null) instead.)");
        setServerCertificate(result.getKeyPair(), result.getCertificate());
    }

    /**
     * Server certificate
     */
    public void setServerCertificate(final KeyPair keyPair, final X509Certificate certificate) {
        setCertificateManager(new DefaultCertificateManager(keyPair, certificate));
    }

    /**
     * Server certificate manager
     */
    public void setCertificateManager(final CertificateManager certificateManager) {
        if (certificateManager != null) {
            this.serverConfig.setCertificateManager(certificateManager);
        } else {
            this.serverConfig.setCertificateManager(new DefaultCertificateManager());
        }
    }

    /**
     * Validator for client certificates
     */
    public void setCertificateValidator(final Supplier<CertificateValidator> certificateValidator) {
        this.certificateValidator = certificateValidator;
    }

    /**
     * Validator for client certificates using default file based approach
     */
    public void setDefaultCertificateValidator(final File certificatesBaseDir) {
        this.certificateValidator = () -> new DefaultCertificateValidator(certificatesBaseDir);
    }
}
