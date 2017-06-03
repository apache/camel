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
package org.apache.camel.util.jsse;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;

/**
 * KeyManager to select a key with desired alias while delegating processing to specified KeyManager Can be
 * used both with server and client sockets
 */
public class AliasedX509ExtendedKeyManager extends X509ExtendedKeyManager {
    private String keyAlias;
    private X509KeyManager keyManager;

    /**
     * Construct KeyManager instance
     * 
     * @param keyAlias Alias of the key to be selected
     * @param keyManager Instance of KeyManager to be wrapped
     * @throws Exception
     */
    public AliasedX509ExtendedKeyManager(String keyAlias, X509KeyManager keyManager) throws Exception {
        this.keyAlias = keyAlias;
        this.keyManager = keyManager;
    }

    public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
        return keyAlias == null ? keyManager.chooseClientAlias(keyType, issuers, socket) : keyAlias;
    }

    public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
        return keyAlias == null ? keyManager.chooseServerAlias(keyType, issuers, socket) : keyAlias;
    }

    public String[] getClientAliases(String keyType, Principal[] issuers) {
        return keyManager.getClientAliases(keyType, issuers);
    }

    public String[] getServerAliases(String keyType, Principal[] issuers) {
        return keyManager.getServerAliases(keyType, issuers);
    }

    public X509Certificate[] getCertificateChain(String alias) {
        return keyManager.getCertificateChain(alias);
    }

    public PrivateKey getPrivateKey(String alias) {
        return keyManager.getPrivateKey(alias);
    }

    @Override
    public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
        return keyAlias == null ? super.chooseEngineServerAlias(keyType, issuers, engine) : keyAlias;
    }

    @Override
    public String chooseEngineClientAlias(String keyType[], Principal[] issuers, SSLEngine engine) {
        return keyAlias == null ? super.chooseEngineClientAlias(keyType, issuers, engine) : keyAlias;
    }
}
