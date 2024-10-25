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
package org.apache.camel.component.knative.http;

import java.util.Optional;

import io.vertx.core.net.JksOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.KeyStoreOptionsBase;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.TrustOptions;
import io.vertx.ext.web.client.WebClientOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.spi.PropertiesComponent;

/**
 * Knative client options are able to autoconfigure secure Http SSL transport options. Options are automatically
 * initialized via system property or environment variable settings. System property and environment variable settings
 * use a specific property name prefix. The properties get resolved via the given Camel context property component.
 */
public class KnativeSslClientOptions extends WebClientOptions implements CamelContextAware {

    private static final String PROPERTY_PREFIX = "camel.knative.client.ssl.";

    private CamelContext camelContext;

    private boolean sslEnabled;
    private boolean verifyHostName;
    private String keystorePath;
    private String keystorePassword;
    private String[] keyPath;
    private String[] keyCertPath;
    private String truststorePath;
    private String truststorePassword;
    private String[] trustCertPath;

    private KeyCertOptions keyCertOptions;
    private TrustOptions trustOptions;

    public KnativeSslClientOptions() {
    }

    public KnativeSslClientOptions(CamelContext camelContext) {
        this.camelContext = camelContext;
        configureOptions(camelContext);
    }

    public void configureOptions() {
        if (camelContext != null) {
            configureOptions(camelContext);
        }
    }

    /**
     * Configures this web client options instance based on properties and environment variables resolved with the given
     * Camel context.
     */
    public void configureOptions(CamelContext camelContext) {
        PropertiesComponent propertiesComponent = camelContext.getPropertiesComponent();

        boolean sslEnabled = Boolean.parseBoolean(
                propertiesComponent.resolveProperty(PROPERTY_PREFIX + "enabled").orElse("false"));
        setSslEnabled(sslEnabled);

        if (sslEnabled) {
            boolean verifyHostname = Boolean.parseBoolean(
                    propertiesComponent.resolveProperty(PROPERTY_PREFIX + "verify.hostname").orElse("true"));
            setVerifyHostName(verifyHostname);

            Optional<String> keystorePath = propertiesComponent.resolveProperty(PROPERTY_PREFIX + "keystore.path");
            String keystorePassword = propertiesComponent.resolveProperty(PROPERTY_PREFIX + "keystore.password").orElse("");
            if (keystorePath.isPresent()) {
                setKeystorePath(keystorePath.get());
                setKeystorePassword(keystorePassword);
            } else {
                Optional<String> keyPath = propertiesComponent.resolveProperty(PROPERTY_PREFIX + "key.path");
                if (keyPath.isPresent()) {
                    String[] keyPathItems = keyPath.get().split(",");
                    if (keyPathItems.length > 0) {
                        setKeyPath(keyPathItems);
                    }
                }

                Optional<String> keyCertPath = propertiesComponent.resolveProperty(PROPERTY_PREFIX + "key.cert.path");
                if (keyCertPath.isPresent()) {
                    String[] keyCertPathItems = keyCertPath.get().split(",");
                    if (keyCertPathItems.length > 0) {
                        setKeyCertPath(keyCertPathItems);
                    }
                }
            }

            Optional<String> truststorePath = propertiesComponent.resolveProperty(PROPERTY_PREFIX + "truststore.path");
            String truststorePassword = propertiesComponent.resolveProperty(PROPERTY_PREFIX + "truststore.password").orElse("");
            Optional<String> trustCertPath = propertiesComponent.resolveProperty(PROPERTY_PREFIX + "trust.cert.path");
            if (truststorePath.isPresent()) {
                setTruststorePath(truststorePath.get());
                setTruststorePassword(truststorePassword);
            } else if (trustCertPath.isPresent()) {
                String[] trustCertPathItems = trustCertPath.get().split(",");
                setTrustCertPath(trustCertPathItems);
            } else {
                trustOptions = TrustAllOptions.INSTANCE;
                setTrustOptions(trustOptions);
            }
        }
    }

    private void initializeKeyCertOptions(String path) {
        if (keyCertOptions == null) {
            if (path == null) {
                keyCertOptions = new JksOptions();
            } else if (path.endsWith(".p12")) {
                keyCertOptions = new PfxOptions();
            } else if (path.endsWith(".pem")) {
                keyCertOptions = new PemKeyCertOptions();
            } else {
                keyCertOptions = new JksOptions();
            }
            this.setKeyCertOptions(keyCertOptions);
        }
    }

    private void initializeTrustOptions(String path) {
        if (trustOptions == null) {
            if (path == null) {
                trustOptions = new JksOptions();
            } else if (path.endsWith(".p12")) {
                trustOptions = new PfxOptions();
            } else if (path.endsWith(".pem")) {
                trustOptions = new PemTrustOptions();
            } else {
                trustOptions = new JksOptions();
            }
            this.setTrustOptions(trustOptions);
        }
    }

    public void setSslEnabled(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
        this.setSsl(sslEnabled);
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public void setVerifyHostName(boolean verifyHostName) {
        this.verifyHostName = verifyHostName;
        this.setVerifyHost(verifyHostName);
    }

    public boolean isVerifyHostName() {
        return verifyHostName;
    }

    public void setKeystorePath(String keystorePath) {
        this.keystorePath = keystorePath;
        initializeKeyCertOptions(keystorePath);
        if (keyCertOptions instanceof KeyStoreOptionsBase keyStoreOptionsBase) {
            keyStoreOptionsBase.setPath(keystorePath);
        }
    }

    public String getKeystorePath() {
        return keystorePath;
    }

    public void setKeystorePassword(String keystorePassword) {
        this.keystorePassword = keystorePassword;
        initializeKeyCertOptions(keystorePath);
        if (keyCertOptions instanceof KeyStoreOptionsBase keyStoreOptionsBase) {
            keyStoreOptionsBase.setPassword(keystorePassword);
        }
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public void setKeyPath(String keyPath) {
        this.keyPath = new String[] { keyPath };
        initializeKeyCertOptions(keyPath);
        if (keyCertOptions instanceof PemKeyCertOptions pemKeyCertOptions) {
            pemKeyCertOptions.setKeyPath(keyPath);
        }
    }

    public void setKeyPath(String... keyPaths) {
        this.keyPath = keyPaths;
        initializeKeyCertOptions(keyPaths[0]);
        if (keyCertOptions instanceof PemKeyCertOptions pemKeyCertOptions) {
            for (String path : keyPaths) {
                pemKeyCertOptions.addKeyPath(path.trim());
            }
        }
    }

    public String[] getKeyPath() {
        return keyPath;
    }

    public void setKeyCertPath(String keyCertPath) {
        this.keyCertPath = new String[] { keyCertPath };
        initializeKeyCertOptions(keyCertPath);
        if (keyCertOptions instanceof PemKeyCertOptions pemKeyCertOptions) {
            pemKeyCertOptions.addCertPath(keyCertPath);
        }
    }

    public void setKeyCertPath(String... keyCertPaths) {
        this.keyCertPath = keyCertPaths;
        initializeKeyCertOptions(keyCertPaths[0]);
        if (keyCertOptions instanceof PemKeyCertOptions pemKeyCertOptions) {
            for (String certPath : keyCertPaths) {
                pemKeyCertOptions.addCertPath(certPath.trim());
            }
        }
    }

    public String[] getKeyCertPath() {
        return keyCertPath;
    }

    public void setTruststorePath(String truststorePath) {
        this.truststorePath = truststorePath;
        initializeTrustOptions(truststorePath);
        if (trustOptions instanceof KeyStoreOptionsBase keyStoreOptionsBase) {
            keyStoreOptionsBase.setPath(truststorePath);
        }
    }

    public String getTruststorePath() {
        return truststorePath;
    }

    public void setTruststorePassword(String truststorePassword) {
        this.truststorePassword = truststorePassword;
        initializeTrustOptions(truststorePath);
        if (trustOptions instanceof KeyStoreOptionsBase keyStoreOptionsBase) {
            keyStoreOptionsBase.setPassword(truststorePassword);
        }
    }

    public String getTruststorePassword() {
        return truststorePassword;
    }

    public void setTrustCertPath(String trustCertPath) {
        this.trustCertPath = new String[] { trustCertPath };
        initializeTrustOptions(trustCertPath);
        if (trustOptions instanceof PemTrustOptions pemTrustOptions) {
            pemTrustOptions.addCertPath(trustCertPath);
        }
    }

    public void setTrustCertPath(String... trustCertPaths) {
        this.trustCertPath = trustCertPaths;
        initializeTrustOptions(trustCertPaths[0]);
        if (trustOptions instanceof PemTrustOptions pemTrustOptions) {
            for (String certPath : trustCertPaths) {
                pemTrustOptions.addCertPath(certPath.trim());
            }
        }
    }

    public String[] getTrustCertPath() {
        return trustCertPath;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
        configureOptions();
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }
}
