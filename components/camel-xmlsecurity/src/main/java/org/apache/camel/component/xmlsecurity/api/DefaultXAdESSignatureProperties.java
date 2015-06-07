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
package org.apache.camel.component.xmlsecurity.api;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.util.jsse.KeyStoreParameters;

/**
 * Default implementation for the XAdES signature properties which determines
 * the Signing Certificate from a keystore and an alias.
 * 
 */
public class DefaultXAdESSignatureProperties 
    extends XAdESSignatureProperties implements CamelContextAware {

    private final KeyStoreAndAlias keyStoreAndAlias = new KeyStoreAndAlias();
    
    private CamelContext context;

    public DefaultXAdESSignatureProperties() {
    }

    public void setKeystore(KeyStore keystore) {
        keyStoreAndAlias.setKeyStore(keystore);
    }

    public void setAlias(String alias) {
        keyStoreAndAlias.setAlias(alias);
    }
    
    public void setKeyStoreParameters(KeyStoreParameters parameters) 
        throws GeneralSecurityException, IOException {
        if (parameters != null) {
            keyStoreAndAlias.setKeyStore(parameters.createKeyStore());
        }
    }

    @Override
    protected X509Certificate getSigningCertificate() throws Exception { //NOPMD
        if (keyStoreAndAlias.getKeyStore() == null) {
            throw new XmlSignatureException("No keystore has been configured");
        }
        X509Certificate cert = 
            (X509Certificate) keyStoreAndAlias.getKeyStore().getCertificate(keyStoreAndAlias.getAlias());
        if (cert == null) {
            throw new XmlSignatureException(
                String.format("No certificate found in keystore for alias '%s'", keyStoreAndAlias.getAlias()));
        }
        return cert;
    }

    @Override
    protected X509Certificate[] getSigningCertificateChain() throws Exception { //NOPMD
        return null;
    }

    @Override
    public CamelContext getCamelContext() {
        return context;
    }

    @Override
    public void setCamelContext(CamelContext context) {
        this.context = context;
    }
}
