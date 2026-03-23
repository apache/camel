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
package org.apache.camel.model.app;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlType;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.jsse.CipherSuitesParameters;
import org.apache.camel.support.jsse.FilterParameters;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.NamedGroupsParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.SSLContextServerParameters;
import org.apache.camel.support.jsse.SecureRandomParameters;
import org.apache.camel.support.jsse.SignatureSchemesParameters;
import org.apache.camel.support.jsse.TrustAllTrustManager;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SSL/TLS context parameters configuration.
 *
 * <p>
 * This provides a simplified way to configure SSL/TLS in XML and YAML DSL, with flat attributes instead of the complex
 * nested structure used in Spring XML DSL.
 * </p>
 *
 * <p>
 * Example usage in XML DSL:
 *
 * <pre>
 * &lt;sslContextParameters id="mySSL" keyStore="server.p12" keystorePassword="changeit"
 *     trustStore="truststore.p12" trustStorePassword="changeit"/&gt;
 * </pre>
 *
 * Example usage in YAML DSL:
 *
 * <pre>
 * - sslContextParameters:
 *     id: mySSL
 *     keyStore: server.p12
 *     keystorePassword: changeit
 *     trustStore: truststore.p12
 *     trustStorePassword: changeit
 * </pre>
 */
@Metadata(label = "configuration,security")
@XmlType(name = "")
@XmlAccessorType(XmlAccessType.FIELD)
public class SSLContextParametersDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(SSLContextParametersDefinition.class);

    @XmlAttribute
    @Metadata(description = "The id of this SSL configuration.")
    private String id;

    @XmlAttribute
    @Metadata(label = "advanced",
              description = "To use a specific provider for creating SSLContext."
                            + " The list of available providers returned by java.security.Security.getProviders()"
                            + " or null to use the highest priority provider implementing the secure socket protocol.")
    private String provider;

    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "TLSv1.3",
              description = "The protocol for the secure sockets created by the SSLContext."
                            + " See https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html")
    private String secureSocketProtocol;

    @XmlAttribute
    @Metadata(label = "advanced",
              description = "An optional certificate alias to use. This is useful when the keystore has multiple certificates.")
    private String certAlias;

    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "86400",
              description = "Timeout in seconds to use for SSLContext. The default is 24 hours.")
    private String sessionTimeout;

    @XmlAttribute
    @Metadata(label = "advanced",
              description = "List of TLS/SSL cipher suite algorithm names. Multiple names can be separated by comma.")
    private String cipherSuites;

    @XmlAttribute
    @Metadata(label = "advanced",
              description = "Filters TLS/SSL cipher suites algorithms names."
                            + " This filter is used for including algorithms that matches the naming pattern."
                            + " Multiple names can be separated by comma."
                            + " Notice that if the cipherSuites option has been configured then the include/exclude filters are not in use.")
    private String cipherSuitesInclude;

    @XmlAttribute
    @Metadata(label = "advanced",
              description = "Filters TLS/SSL cipher suites algorithms names."
                            + " This filter is used for excluding algorithms that matches the naming pattern."
                            + " Multiple names can be separated by comma."
                            + " Notice that if the cipherSuites option has been configured then the include/exclude filters are not in use.")
    private String cipherSuitesExclude;

    @XmlAttribute
    @Metadata(label = "advanced",
              description = "List of TLS/SSL named groups (key exchange groups). Multiple names can be separated by comma."
                            + " Named groups control which key exchange algorithms are available during the TLS handshake,"
                            + " including post-quantum hybrid groups such as X25519MLKEM768.")
    private String namedGroups;

    @XmlAttribute
    @Metadata(label = "advanced",
              description = "Filters TLS/SSL named groups."
                            + " This filter is used for including named groups that match the naming pattern."
                            + " Multiple names can be separated by comma."
                            + " Notice that if the namedGroups option has been configured then the include/exclude filters are not in use.")
    private String namedGroupsInclude;

    @XmlAttribute
    @Metadata(label = "advanced",
              description = "Filters TLS/SSL named groups."
                            + " This filter is used for excluding named groups that match the naming pattern."
                            + " Multiple names can be separated by comma."
                            + " Notice that if the namedGroups option has been configured then the include/exclude filters are not in use.")
    private String namedGroupsExclude;

    @XmlAttribute
    @Metadata(label = "advanced",
              description = "List of TLS/SSL signature schemes. Multiple names can be separated by comma."
                            + " Signature schemes control which signature algorithms are available during the TLS handshake,"
                            + " including post-quantum signature algorithms such as ML-DSA.")
    private String signatureSchemes;

    @XmlAttribute
    @Metadata(label = "advanced",
              description = "Filters TLS/SSL signature schemes."
                            + " This filter is used for including signature schemes that match the naming pattern."
                            + " Multiple names can be separated by comma."
                            + " Notice that if the signatureSchemes option has been configured then the include/exclude filters are not in use.")
    private String signatureSchemesInclude;

    @XmlAttribute
    @Metadata(label = "advanced",
              description = "Filters TLS/SSL signature schemes."
                            + " This filter is used for excluding signature schemes that match the naming pattern."
                            + " Multiple names can be separated by comma."
                            + " Notice that if the signatureSchemes option has been configured then the include/exclude filters are not in use.")
    private String signatureSchemesExclude;

    @XmlAttribute
    @Metadata(description = "The key store to load."
                            + " The key store is by default loaded from classpath. If you must load from file system, then use file: as prefix."
                            + " file:nameOfFile (to refer to the file system)"
                            + " classpath:nameOfFile (to refer to the classpath; default)"
                            + " http:uri (to load the resource using HTTP)"
                            + " ref:nameOfBean (to lookup an existing KeyStore instance from the registry, for example for testing and development).")
    private String keyStore;

    @XmlAttribute
    @Metadata(label = "advanced",
              description = "The type of the key store to load."
                            + " See https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html")
    private String keyStoreType;

    @XmlAttribute
    @Metadata(label = "advanced",
              description = "To use a specific provider for creating KeyStore."
                            + " The list of available providers returned by java.security.Security.getProviders()"
                            + " or null to use the highest priority provider implementing the secure socket protocol.")
    private String keyStoreProvider;

    @XmlAttribute
    @Metadata(description = "Sets the SSL Keystore password.")
    private String keystorePassword;

    @XmlAttribute
    @Metadata(description = "The trust store to load."
                            + " The trust store is by default loaded from classpath. If you must load from file system, then use file: as prefix."
                            + " file:nameOfFile (to refer to the file system)"
                            + " classpath:nameOfFile (to refer to the classpath; default)"
                            + " http:uri (to load the resource using HTTP)"
                            + " ref:nameOfBean (to lookup an existing KeyStore instance from the registry, for example for testing and development).")
    private String trustStore;

    @XmlAttribute
    @Metadata(description = "Sets the SSL Truststore password.")
    private String trustStorePassword;

    @XmlAttribute
    @Metadata(description = "Allows to trust all SSL certificates without performing certificate validation."
                            + " This can be used in development environment but may expose the system to security risks."
                            + " Notice that if the trustAllCertificates option is set to true"
                            + " then the trustStore/trustStorePassword options are not in use.")
    private String trustAllCertificates;

    @XmlAttribute
    @Metadata(label = "advanced",
              description = "Algorithm name used for creating the KeyManagerFactory."
                            + " See https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html")
    private String keyManagerAlgorithm;

    @XmlAttribute
    @Metadata(label = "advanced",
              description = "To use a specific provider for creating KeyManagerFactory."
                            + " The list of available providers returned by java.security.Security.getProviders()"
                            + " or null to use the highest priority provider implementing the secure socket protocol.")
    private String keyManagerProvider;

    @XmlAttribute
    @Metadata(label = "advanced",
              description = "Algorithm name used for creating the SecureRandom."
                            + " See https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html")
    private String secureRandomAlgorithm;

    @XmlAttribute
    @Metadata(label = "advanced",
              description = "To use a specific provider for creating SecureRandom."
                            + " The list of available providers returned by java.security.Security.getProviders()"
                            + " or null to use the highest priority provider implementing the secure socket protocol.")
    private String secureRandomProvider;

    @XmlAttribute
    @Metadata(defaultValue = "NONE", enums = "NONE,WANT,REQUIRE",
              description = "Sets the configuration for server-side client-authentication requirements.")
    private String clientAuthentication;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getSecureSocketProtocol() {
        return secureSocketProtocol;
    }

    public void setSecureSocketProtocol(String secureSocketProtocol) {
        this.secureSocketProtocol = secureSocketProtocol;
    }

    public String getCertAlias() {
        return certAlias;
    }

    public void setCertAlias(String certAlias) {
        this.certAlias = certAlias;
    }

    public String getSessionTimeout() {
        return sessionTimeout;
    }

    public void setSessionTimeout(String sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    public String getCipherSuites() {
        return cipherSuites;
    }

    public void setCipherSuites(String cipherSuites) {
        this.cipherSuites = cipherSuites;
    }

    public String getCipherSuitesInclude() {
        return cipherSuitesInclude;
    }

    public void setCipherSuitesInclude(String cipherSuitesInclude) {
        this.cipherSuitesInclude = cipherSuitesInclude;
    }

    public String getCipherSuitesExclude() {
        return cipherSuitesExclude;
    }

    public void setCipherSuitesExclude(String cipherSuitesExclude) {
        this.cipherSuitesExclude = cipherSuitesExclude;
    }

    public String getNamedGroups() {
        return namedGroups;
    }

    public void setNamedGroups(String namedGroups) {
        this.namedGroups = namedGroups;
    }

    public String getNamedGroupsInclude() {
        return namedGroupsInclude;
    }

    public void setNamedGroupsInclude(String namedGroupsInclude) {
        this.namedGroupsInclude = namedGroupsInclude;
    }

    public String getNamedGroupsExclude() {
        return namedGroupsExclude;
    }

    public void setNamedGroupsExclude(String namedGroupsExclude) {
        this.namedGroupsExclude = namedGroupsExclude;
    }

    public String getSignatureSchemes() {
        return signatureSchemes;
    }

    public void setSignatureSchemes(String signatureSchemes) {
        this.signatureSchemes = signatureSchemes;
    }

    public String getSignatureSchemesInclude() {
        return signatureSchemesInclude;
    }

    public void setSignatureSchemesInclude(String signatureSchemesInclude) {
        this.signatureSchemesInclude = signatureSchemesInclude;
    }

    public String getSignatureSchemesExclude() {
        return signatureSchemesExclude;
    }

    public void setSignatureSchemesExclude(String signatureSchemesExclude) {
        this.signatureSchemesExclude = signatureSchemesExclude;
    }

    public String getKeyStore() {
        return keyStore;
    }

    public void setKeyStore(String keyStore) {
        this.keyStore = keyStore;
    }

    public String getKeyStoreType() {
        return keyStoreType;
    }

    public void setKeyStoreType(String keyStoreType) {
        this.keyStoreType = keyStoreType;
    }

    public String getKeyStoreProvider() {
        return keyStoreProvider;
    }

    public void setKeyStoreProvider(String keyStoreProvider) {
        this.keyStoreProvider = keyStoreProvider;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public void setKeystorePassword(String keystorePassword) {
        this.keystorePassword = keystorePassword;
    }

    public String getTrustStore() {
        return trustStore;
    }

    public void setTrustStore(String trustStore) {
        this.trustStore = trustStore;
    }

    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    public void setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    public String getTrustAllCertificates() {
        return trustAllCertificates;
    }

    public void setTrustAllCertificates(String trustAllCertificates) {
        this.trustAllCertificates = trustAllCertificates;
    }

    public String getKeyManagerAlgorithm() {
        return keyManagerAlgorithm;
    }

    public void setKeyManagerAlgorithm(String keyManagerAlgorithm) {
        this.keyManagerAlgorithm = keyManagerAlgorithm;
    }

    public String getKeyManagerProvider() {
        return keyManagerProvider;
    }

    public void setKeyManagerProvider(String keyManagerProvider) {
        this.keyManagerProvider = keyManagerProvider;
    }

    public String getSecureRandomAlgorithm() {
        return secureRandomAlgorithm;
    }

    public void setSecureRandomAlgorithm(String secureRandomAlgorithm) {
        this.secureRandomAlgorithm = secureRandomAlgorithm;
    }

    public String getSecureRandomProvider() {
        return secureRandomProvider;
    }

    public void setSecureRandomProvider(String secureRandomProvider) {
        this.secureRandomProvider = secureRandomProvider;
    }

    public String getClientAuthentication() {
        return clientAuthentication;
    }

    public void setClientAuthentication(String clientAuthentication) {
        this.clientAuthentication = clientAuthentication;
    }

    /**
     * Creates a runtime {@link SSLContextParameters} object from this definition.
     *
     * @param  camelContext the CamelContext
     * @return              the SSL context parameters
     */
    public SSLContextParameters createSSLContextParameters(CamelContext camelContext) {
        KeyManagersParameters kmp = null;
        if (keyStore != null) {
            KeyStoreParameters ksp = new KeyStoreParameters();
            ksp.setCamelContext(camelContext);
            ksp.setResource(keyStore);
            ksp.setType(keyStoreType);
            ksp.setPassword(keystorePassword);
            ksp.setProvider(keyStoreProvider);

            kmp = new KeyManagersParameters();
            kmp.setCamelContext(camelContext);
            kmp.setKeyPassword(keystorePassword);
            kmp.setKeyStore(ksp);
            kmp.setAlgorithm(keyManagerAlgorithm);
            kmp.setProvider(keyManagerProvider);
        }

        // resolve property placeholder for trustAllCertificates since we check it before the JSSE layer
        String resolvedTrustAll = trustAllCertificates;
        if (resolvedTrustAll != null) {
            try {
                resolvedTrustAll = camelContext.resolvePropertyPlaceholders(resolvedTrustAll);
            } catch (Exception e) {
                // ignore
            }
        }

        TrustManagersParameters tmp = null;
        if ("true".equalsIgnoreCase(resolvedTrustAll)) {
            tmp = new TrustManagersParameters();
            tmp.setCamelContext(camelContext);
            tmp.setTrustManager(TrustAllTrustManager.INSTANCE);
            LOG.warn(
                    "Trust all certificates enabled. Using this in production can expose the application to man-in-the-middle attacks");
        } else if (trustStore != null) {
            KeyStoreParameters tsp = new KeyStoreParameters();
            tsp.setCamelContext(camelContext);
            tsp.setResource(trustStore);
            tsp.setPassword(trustStorePassword);
            tmp = new TrustManagersParameters();
            tmp.setCamelContext(camelContext);
            tmp.setKeyStore(tsp);
        }

        SSLContextServerParameters scsp = null;
        if (clientAuthentication != null) {
            scsp = new SSLContextServerParameters();
            scsp.setCamelContext(camelContext);
            scsp.setClientAuthentication(clientAuthentication);
        }

        SecureRandomParameters srp = null;
        if (secureRandomAlgorithm != null || secureRandomProvider != null) {
            srp = new SecureRandomParameters();
            srp.setCamelContext(camelContext);
            srp.setAlgorithm(secureRandomAlgorithm);
            srp.setProvider(secureRandomProvider);
        }

        SSLContextParameters scp = new SSLContextParameters();
        scp.setCamelContext(camelContext);
        scp.setProvider(provider);
        scp.setSecureSocketProtocol(secureSocketProtocol);
        scp.setCertAlias(certAlias);
        if (sessionTimeout != null) {
            scp.setSessionTimeout(sessionTimeout);
        }
        if (cipherSuites != null) {
            CipherSuitesParameters csp = new CipherSuitesParameters();
            for (String c : cipherSuites.split(",")) {
                csp.addCipherSuite(c.trim());
            }
            scp.setCipherSuites(csp);
        }
        if (cipherSuitesInclude != null || cipherSuitesExclude != null) {
            FilterParameters fp = new FilterParameters();
            if (cipherSuitesInclude != null) {
                for (String c : cipherSuitesInclude.split(",")) {
                    fp.addInclude(c.trim());
                }
            }
            if (cipherSuitesExclude != null) {
                for (String c : cipherSuitesExclude.split(",")) {
                    fp.addExclude(c.trim());
                }
            }
            scp.setCipherSuitesFilter(fp);
        }
        if (namedGroups != null) {
            NamedGroupsParameters ngp = new NamedGroupsParameters();
            for (String g : namedGroups.split(",")) {
                ngp.addNamedGroup(g.trim());
            }
            scp.setNamedGroups(ngp);
        }
        if (namedGroupsInclude != null || namedGroupsExclude != null) {
            FilterParameters fp = new FilterParameters();
            if (namedGroupsInclude != null) {
                for (String g : namedGroupsInclude.split(",")) {
                    fp.addInclude(g.trim());
                }
            }
            if (namedGroupsExclude != null) {
                for (String g : namedGroupsExclude.split(",")) {
                    fp.addExclude(g.trim());
                }
            }
            scp.setNamedGroupsFilter(fp);
        }
        if (signatureSchemes != null) {
            SignatureSchemesParameters ssp = new SignatureSchemesParameters();
            for (String s : signatureSchemes.split(",")) {
                ssp.addSignatureScheme(s.trim());
            }
            scp.setSignatureSchemes(ssp);
        }
        if (signatureSchemesInclude != null || signatureSchemesExclude != null) {
            FilterParameters fp = new FilterParameters();
            if (signatureSchemesInclude != null) {
                for (String s : signatureSchemesInclude.split(",")) {
                    fp.addInclude(s.trim());
                }
            }
            if (signatureSchemesExclude != null) {
                for (String s : signatureSchemesExclude.split(",")) {
                    fp.addExclude(s.trim());
                }
            }
            scp.setSignatureSchemesFilter(fp);
        }
        scp.setKeyManagers(kmp);
        scp.setTrustManagers(tmp);
        scp.setServerParameters(scsp);
        scp.setSecureRandom(srp);
        return scp;
    }

}
