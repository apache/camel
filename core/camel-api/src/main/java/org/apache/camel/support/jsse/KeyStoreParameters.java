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
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Security;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A representation of configuration options for creating and loading a {@link KeyStore} instance.
 */
public class KeyStoreParameters extends JsseParameters {

    private static final Logger LOG = LoggerFactory.getLogger(KeyStoreParameters.class);

    protected String type;
    protected String password;
    protected String provider;
    protected KeyStore keyStore;
    protected String resource;

    public String getType() {
        return type;
    }

    /**
     * The type of the key store to create and load.
     *
     * See https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html
     */
    public void setType(String value) {
        this.type = value;
    }

    public String getPassword() {
        return password;
    }

    /**
     * The password for reading/opening/verifying the key store.
     */
    public void setPassword(String value) {
        this.password = value;
    }

    public String getProvider() {
        return provider;
    }

    /**
     * The provider identifier for instantiating the key store.
     *
     * @see Security#getProviders()
     */
    public void setProvider(String value) {
        this.provider = value;
    }

    public String getResource() {
        return resource;
    }

    /**
     * The keystore to load.
     *
     * The keystore is by default loaded from classpath. If you must load from file system, then use file: as prefix.
     *
     * file:nameOfFile (to refer to the file system) classpath:nameOfFile (to refer to the classpath; default) http:uri
     * (to load the resource using HTTP) ref:nameOfBean (to lookup an existing KeyStore instance from the registry, for
     * example for testing and development).
     */
    public void setResource(String value) {
        this.resource = value;
    }

    /**
     * To use an existing KeyStore instead of loading. You must also set password so this keystore can be used.
     */
    public void setKeyStore(KeyStore keyStore) {
        this.keyStore = keyStore;
    }

    /**
     * Creates a {@link KeyStoreParameters} instance based off of the configuration state of this instance. If
     * {@link #getType()} returns {@code null}, the default key store type is loaded, otherwise the type will be of that
     * specified.
     * <p/>
     * The created instance will always be loaded, but if the type requires an input stream and {@link #getResource()}
     * returns {@code null}, the instance will be empty. The loading of the resource, if not {@code null}, is attempted
     * by treating the resource as a file path, a class path resource, and a URL in that order. An exception is thrown
     * if the resource cannot be resolved to readable input stream using any of the above methods.
     *
     * @return                          a configured and loaded key store
     * @throws GeneralSecurityException if there is an error creating an instance with the given configuration
     * @throws IOException              if there is an error resolving the configured resource to an input stream
     */
    public KeyStore createKeyStore() throws GeneralSecurityException, IOException {
        if (this.resource != null) {
            this.resource = this.parsePropertyValue(this.resource);
        }

        if (this.keyStore == null && this.resource != null && this.resource.startsWith("ref:")) {
            String ref = this.resource.substring(4);
            this.keyStore = getCamelContext().getRegistry().lookupByNameAndType(ref, KeyStore.class);
        }
        if (keyStore != null) {
            if (LOG.isDebugEnabled()) {
                List<String> aliases = extractAliases(keyStore);
                LOG.debug(
                        "KeyStore [{}], initialized from [{}], is using provider [{}], has type [{}], and contains aliases {}.",
                        keyStore, this, keyStore.getProvider(), keyStore.getType(), aliases);
            }
            return keyStore;
        }

        LOG.trace("Creating KeyStore instance from KeyStoreParameters [{}].", this);

        String ksType = this.parsePropertyValue(this.type);
        if (ksType == null) {
            ksType = KeyStore.getDefaultType();
        }

        char[] ksPassword = null;
        if (this.password != null) {
            ksPassword = this.parsePropertyValue(this.password).toCharArray();
        }

        KeyStore ks;
        if (this.provider == null) {
            ks = KeyStore.getInstance(ksType);
        } else {
            ks = KeyStore.getInstance(ksType, this.parsePropertyValue(this.provider));
        }

        if (this.resource == null) {
            ks.load(null, ksPassword);
        } else {
            InputStream is = this.resolveResource(this.resource);
            if (is == null) {
                LOG.warn("No keystore could be found at {}.", this.resource);
            } else {
                try (is) {
                    ks.load(is, ksPassword);
                }
            }
        }

        if (LOG.isDebugEnabled()) {
            List<String> aliases = extractAliases(ks);
            LOG.debug("KeyStore [{}], initialized from [{}], is using provider [{}], has type [{}], and contains aliases {}.",
                    ks, this, ks.getProvider(), ks.getType(), aliases);
        }

        return ks;
    }

    private List<String> extractAliases(KeyStore ks) {
        List<String> aliases = new LinkedList<>();

        Enumeration<String> aliasEnum = null;
        try {
            aliasEnum = ks.aliases();
        } catch (KeyStoreException e) {
            // ignore - only used for logging purposes
        }
        if (aliasEnum != null) {
            while (aliasEnum.hasMoreElements()) {
                aliases.add(aliasEnum.nextElement());
            }
        }
        return aliases;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("KeyStoreParameters[type=");
        builder.append(type);
        builder.append(", password=");
        builder.append("********");
        builder.append(", provider=");
        builder.append(provider);
        builder.append(", resource=");
        builder.append(resource);
        builder.append("]");
        return builder.toString();
    }
}
