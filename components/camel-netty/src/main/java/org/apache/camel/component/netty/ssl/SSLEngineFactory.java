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
package org.apache.camel.component.netty.ssl;

import java.io.File;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;

import org.apache.camel.converter.IOConverter;

public class SSLEngineFactory {

    private static final String SSL_PROTOCOL = "TLS";
    private static SSLContext sslContext;
    
    public SSLEngineFactory(String keyStoreFormat, String securityProvider, File keyStoreFile, File trustStoreFile, char[] passphrase) throws Exception {
        KeyStore ks = KeyStore.getInstance(keyStoreFormat);

        ks.load(IOConverter.toInputStream(keyStoreFile), passphrase);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(securityProvider);
        kmf.init(ks, passphrase);

        sslContext = SSLContext.getInstance(SSL_PROTOCOL);
        
        if (trustStoreFile != null) { 
            KeyStore ts = KeyStore.getInstance(keyStoreFormat); 
            ts.load(IOConverter.toInputStream(trustStoreFile), passphrase); 
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(securityProvider); 
            tmf.init(ts); 
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null); 
        } else { 
            sslContext.init(kmf.getKeyManagers(), null, null); 
        }
    }

    public SSLEngine createServerSSLEngine() {
        SSLEngine serverEngine = sslContext.createSSLEngine();
        serverEngine.setUseClientMode(false);
        serverEngine.setNeedClientAuth(true);
        return serverEngine;
    }

    public SSLEngine createClientSSLEngine() {
        SSLEngine clientEngine = sslContext.createSSLEngine();
        clientEngine.setUseClientMode(true);
        return clientEngine;
    }
    
}
