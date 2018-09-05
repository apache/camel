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
package org.apache.camel.component.crypto.cms.sig;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.crypto.cms.common.DefaultCryptoCmsUnMarshallerConfiguration;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * Fetches the X.509 certificates which can be used for the verification from a
 * Java keystore.
 */
@UriParams
public class DefaultSignedDataVerifierConfiguration extends DefaultCryptoCmsUnMarshallerConfiguration implements SignedDataVerifierConfiguration, Cloneable {

    @UriParam(label = "verify", defaultValue = "false")
    private Boolean signedDataHeaderBase64 = Boolean.FALSE;

    @UriParam(label = "verify", defaultValue = "true")
    private Boolean verifySignaturesOfAllSigners = Boolean.TRUE;

    /**
     * Indicates whether the value in the header CamelCryptoCmsSignedData is
     * base64 encoded. Default value is <code>false</code>.
     * <p>
     * Only relevant for detached signatures. In the detached signature case,
     * the header contains the Signed Data object.
     */
    public void setSignedDataHeaderBase64(Boolean signedDataHeaderBase64) {
        this.signedDataHeaderBase64 = signedDataHeaderBase64;
    }

    @Override
    public Boolean isSignedDataHeaderBase64(Exchange exchange) throws CryptoCmsException {
        return signedDataHeaderBase64;
    }

    /**
     * If <code>true</code> then the signatures of all signers contained in the
     * Signed Data object are verified. If <code>false</code> then only one
     * signature whose signer info matches with one of the specified
     * certificates is verified. Default value is <code>true</code>.
     */
    public void setVerifySignaturesOfAllSigners(Boolean verifySignaturesOfAllSigners) {
        this.verifySignaturesOfAllSigners = verifySignaturesOfAllSigners;
    }

    @Override
    public Boolean isVerifySignaturesOfAllSigners(Exchange exchange) throws CryptoCmsException {
        return verifySignaturesOfAllSigners;
    }

    @Override
    public Collection<X509Certificate> getCertificates(Exchange exchange) throws CryptoCmsException {
        KeyStore keystore = getKeyStore();
        try {
            List<X509Certificate> certs = new ArrayList<>(keystore.size());
            for (Enumeration<String> aliases = keystore.aliases(); aliases.hasMoreElements();) {
                String alias = aliases.nextElement();
                Certificate cert = keystore.getCertificate(alias);
                if (cert instanceof X509Certificate) {
                    certs.add((X509Certificate)cert);
                }
            }
            return certs;
        } catch (KeyStoreException e) {
            throw new CryptoCmsException("Problem during reading the certificates of the verifier keystore");
        }
    }

    public DefaultSignedDataVerifierConfiguration copy() {
        try {
            return (DefaultSignedDataVerifierConfiguration)clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e); // should never happen
        }
    }

}
