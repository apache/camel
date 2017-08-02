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
package org.apache.camel.component.crypto.cms.crypt;

import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import org.apache.camel.Exchange;
import org.apache.camel.component.crypto.cms.common.DefaultCryptoCmsConfiguration;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsException;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsNoKeyOrCertificateForAliasException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * Information about the receiver of an encrypted message. The encryption public
 * key is defined via an alias referencing an entry in a keystore.
 */
@UriParams
public class DefaultKeyTransRecipientInfo extends DefaultCryptoCmsConfiguration implements TransRecipientInfo {

    @UriParam(label = "encrypt")
    private String certificateAlias;

    private String keyEncryptionAlgorithm = "RSA";

    protected String getCertificateAlias() throws CryptoCmsException {
        if (certificateAlias == null) {
            throw new CryptoCmsException("Certificate alias not configured in recipient " + this);
        }
        return certificateAlias;
    }

    /**
     * Keytstore alias for looking up the X.509 certificate whose public key is
     * used to encrypt the secret symmetric encryption key.
     * 
     * @param certificateAlias alias
     */
    public void setCertificateAlias(String certificateAlias) {
        this.certificateAlias = certificateAlias;
    }

    // /**
    // * Encryption Algorithm used for encrypting the secret key in
    // * {@link CmsEnvelopedDataEncryptor}.
    // *
    // * @param keyEncryptionAlgorithm algorithm, for example "RSA"
    // */
    // public void setKeyEncryptionAlgorithm(String keyEncryptionAlgorithm) {
    // this.keyEncryptionAlgorithm = keyEncryptionAlgorithm;
    // }

    public String toString() {
        return "certificate alias=" + certificateAlias + ", key encryption algorithm=" + keyEncryptionAlgorithm;
    }

    /** Currently, the key encryption algorithm is fixed to "RSA". */
    @Override
    public String getKeyEncryptionAlgorithm(Exchange exchange) throws CryptoCmsException {
        return keyEncryptionAlgorithm;
    }

    @Override
    public X509Certificate getCertificate(Exchange exchange) throws CryptoCmsException {
        String alias = getCertificateAlias();
        Certificate cert;
        try {
            cert = getKeyStore().getCertificate(alias);
        } catch (KeyStoreException e) {
            throw new CryptoCmsException("Problem during reading the certificate with the alias '" + alias + "' from the keystore of the recipient " + this);
        }
        if (cert instanceof X509Certificate) {
            return (X509Certificate)cert;
        }
        throw new CryptoCmsNoKeyOrCertificateForAliasException("No X509 certificate found for the alias '" + alias + "' in the keystore of the recipient " + this);
    }

}
