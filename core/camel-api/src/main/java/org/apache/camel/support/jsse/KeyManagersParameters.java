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
package org.apache.camel.support.jsse;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.Security;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A representation of configuration options for creating and loading {@link KeyManager} instance(s).
 */
public class KeyManagersParameters extends JsseParameters {

    private static final Logger LOG = LoggerFactory.getLogger(KeyManagersParameters.class);

    protected KeyStoreParameters keyStore;
    protected String keyPassword;
    protected String provider;
    protected String algorithm;

    /**
     * Creates {@link KeyManager}s based on this instance's configuration and the {@code KeyStore} produced by the
     * configuration returned from {@link #getKeyStore()}. The {@code KeyManager}s are produced from a factory created
     * by using the provider and algorithm identifiers returned by {@link #getProvider()} and {@link #getAlgorithm()},
     * respectively. If either of these methods returns null, the default JSSE value is used instead.
     *
     * @return                          the initialized {@code KeyManager}s
     * @throws GeneralSecurityException if there is an error creating the {@code KeyManager}s or in creating the
     *                                  {@code KeyStore}
     * @throws IOException              if there is an error loading the {@code KeyStore}
     *
     * @see                             KeyStoreParameters#createKeyStore()
     */
    public KeyManager[] createKeyManagers() throws GeneralSecurityException, IOException {
        LOG.trace("Creating KeyManager[] from KeyManagersParameters [{}].", this);
        KeyManager[] keyManagers;

        String kmfAlgorithm = this.parsePropertyValue(this.getAlgorithm());
        if (kmfAlgorithm == null) {
            kmfAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
        }

        KeyManagerFactory kmf;
        if (this.getProvider() == null) {
            kmf = KeyManagerFactory.getInstance(kmfAlgorithm);
        } else {
            kmf = KeyManagerFactory.getInstance(kmfAlgorithm, this.parsePropertyValue(this.getProvider()));
        }

        LOG.debug("KeyManagerFactory [{}], initialized from [{}], is using provider [{}] and algorithm [{}].",
                kmf, this, kmf.getProvider(), kmf.getAlgorithm());

        char[] kmfPassword = null;
        if (this.getKeyPassword() != null) {
            kmfPassword = this.parsePropertyValue(this.getKeyPassword()).toCharArray();
        }

        KeyStore ks = this.getKeyStore() == null ? null : this.getKeyStore().createKeyStore();

        kmf.init(ks, kmfPassword);
        keyManagers = kmf.getKeyManagers();

        LOG.debug("KeyManager[] [{}], initialized from KeyManagerFactory [{}].", keyManagers, kmf);

        return keyManagers;
    }

    public KeyStoreParameters getKeyStore() {
        return keyStore;
    }

    /**
     * The key store configuration used to create the {@link KeyStore} that the {@link KeyManager}s produced by this
     * object's configuration expose.
     *
     * @param value the configuration to use
     */
    public void setKeyStore(KeyStoreParameters value) {
        this.keyStore = value;
    }

    public String getKeyPassword() {
        return keyPassword;
    }

    /**
     * The password for recovering keys in the key store. Used by the {@link KeyManagerFactory} that creates the
     * {@link KeyManager}s represented by this object's configuration.
     *
     * @param value the value to use
     */
    public void setKeyPassword(String value) {
        this.keyPassword = value;
    }

    public String getProvider() {
        return provider;
    }

    /**
     * The provider identifier for the {@link KeyManagerFactory} used to create the {@link KeyManager}s represented by
     * this object's configuration.
     *
     * @param value the desired provider identifier or {@code null} to use the highest priority provider implementing
     *              the algorithm
     *
     * @see         Security#getProviders()
     */
    public void setProvider(String value) {
        this.provider = value;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * The algorithm name for the {@link KeyManagerFactory} used to create the {@link KeyManager}s represented by this
     * object's configuration. See the
     *
     * See https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html
     *
     * @param value the desired algorithm or {@code null} to use default
     * @see         KeyManagerFactory#getDefaultAlgorithm()
     */
    public void setAlgorithm(String value) {
        this.algorithm = value;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("KeyManagersParameters[keyStore=");
        builder.append(keyStore);
        builder.append(", keyPassword=");
        builder.append("********");
        builder.append(", provider=");
        builder.append(provider);
        builder.append(", algorithm=");
        builder.append(algorithm);
        builder.append("]");
        return builder.toString();
    }
}
