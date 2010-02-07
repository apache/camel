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
package org.apache.camel.component.gae.auth;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.PrivateKey;

import org.springframework.core.io.Resource;

/**
 * A Java keystore specific key loader. 
 */
public class GAuthJksLoader implements GAuthKeyLoader {

    private Resource keyStoreLocation;

    private String storePass;

    private String keyPass;

    private String keyAlias;

    public GAuthJksLoader() {
        this(null, null, null, null);
    }

    public GAuthJksLoader(Resource keyStoreLocation, String storePass, String keyPass, String keyAlias) {
        this.keyStoreLocation = keyStoreLocation;
        this.storePass = storePass;
        this.keyPass = keyPass;
        this.keyAlias = keyAlias;
    }

    /**
     * Set the location of the Java keystore.
     * 
     * @param keyStoreLocation
     */
    public void setKeyStoreLocation(Resource keyStoreLocation) {
        this.keyStoreLocation = keyStoreLocation;
    }

    /**
     * Sets the password used to open the key store.
     * 
     * @param storePass
     */
    public void setStorePass(String storePass) {
        this.storePass = storePass;
    }

    /**
     * Sets the password used to get access to a specific key.
     * 
     * @param keyPass
     */
    public void setKeyPass(String keyPass) {
        this.keyPass = keyPass;
    }

    /**
     * Sets the alias of the key to be loaded.
     * 
     * @param keyAlias
     */
    public void setKeyAlias(String keyAlias) {
        this.keyAlias = keyAlias;
    }

    /**
     * Loads a private key from a Java keystore depending on this loader's 
     * properties.
     */
    public PrivateKey loadPrivateKey() throws Exception {
        InputStream input = keyStoreLocation.getInputStream();
        try {
            return loadPrivateKey(input);
        } finally {
            input.close();
        }
    }

    private PrivateKey loadPrivateKey(InputStream input) throws Exception {
        // Load keystore
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        keystore.load(input, storePass.toCharArray());

        // Retrieve private key
        PrivateKeyEntry entry = (PrivateKeyEntry)keystore.getEntry(keyAlias, new PasswordProtection(keyPass.toCharArray()));
        return entry.getPrivateKey();
    }

}
