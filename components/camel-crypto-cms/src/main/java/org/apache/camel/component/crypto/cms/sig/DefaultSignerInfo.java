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
package org.apache.camel.component.crypto.cms.sig;

import java.security.Key;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import org.apache.camel.Exchange;
import org.apache.camel.component.crypto.cms.common.DefaultCryptoCmsConfiguration;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsException;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsNoKeyOrCertificateForAliasException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.bouncycastle.cms.CMSAttributeTableGenerator;
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator;

/**
 * Reads the signer information from a Java keystore. You have to specify an
 * alias for the private key entry, the signature algorithm, and the keystore.
 */
@UriParams
public class DefaultSignerInfo extends DefaultCryptoCmsConfiguration implements SignerInfo {

    @UriParam(label = "sign")
    private String privateKeyAlias;

    @UriParam(label = "sign")
    private char[] password;
    @UriParam(label = "sign", defaultValue = "SHA256withRSA")
    private String signatureAlgorithm = "SHA256withRSA";
    @UriParam(label = "sign", defaultValue = "true")
    private boolean includeCertificates = true;

    @UriParam(label = "sign")
    private CMSAttributeTableGenerator signedAttributeGenerator = new DefaultSignedAttributeTableGenerator();

    @UriParam(label = "sign", defaultValue = "null")
    private CMSAttributeTableGenerator unsignedAttributeGenerator;

    /**
     * Password of the private key. If not set then the password set in the
     * parameter 'keystoreParameters' is used.
     */
    public void setPassword(char[] password) {
        this.password = password;
    }

    protected char[] getPassword(Exchange exchange) throws CryptoCmsException {
        if (password != null) {
            return password;
        }

        String pw = null;
        if (getKeyStoreParameters() != null) {
            pw = getKeyStoreParameters().getPassword();
        }
        if (pw == null) {
            throw new CryptoCmsException("No password for accessing the private key from the keystore found for the singer infor " + this);
        }
        return pw.toCharArray();
    }

    protected String getPrivateKeyAlias(Exchange exchange) throws CryptoCmsException {
        if (privateKeyAlias == null) {
            throw new CryptoCmsException("No alias defined for signer info " + this);
        }
        return privateKeyAlias;
    }

    /**
     * Alias of the private key entry in the keystore which is used for signing.
     */
    public void setPrivateKeyAlias(String privateKeyAlias) {
        this.privateKeyAlias = privateKeyAlias;
    }

    /**
     * Signature algorithm. The default algorithm is "SHA256withRSA".
     * <p>
     * Attention, the signature algorithm must fit to the signer private key.
     */
    public void setSignatureAlgorithm(String signatureAlgorithm) {
        this.signatureAlgorithm = signatureAlgorithm;
    }

    /**
     * If <tt>true</tt> then the certificate chain corresponding to the alias of
     * the private key is added to the certificate list of the Signed Data
     * instance.
     */
    public void setIncludeCertificates(boolean includeCertificates) {
        this.includeCertificates = includeCertificates;
    }

    @Override
    public String getSignatureAlgorithm(Exchange exchange) throws CryptoCmsException {
        return signatureAlgorithm;
    }

    @Override
    public PrivateKey getPrivateKey(Exchange exchange) throws CryptoCmsException {
        String alias = getPrivateKeyAlias(exchange);
        try {
            Key key = getKeyStore().getKey(alias, getPassword(exchange));
            if (key instanceof PrivateKey) {
                return (PrivateKey)key;
            }
        } catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException e) {
            throw new CryptoCmsException("Problem occured during accessing the private key for the alias '" + alias + "' in the keystore of signer " + this);
        }
        throw new CryptoCmsNoKeyOrCertificateForAliasException("No private key found  for the alias '" + alias + "' in the keystore of signer " + this);
    }

    @Override
    public X509Certificate getCertificate(Exchange exchange) throws CryptoCmsException {

        String alias = getPrivateKeyAlias(exchange);
        Certificate cert;
        try {
            cert = getKeyStore().getCertificate(alias);
        } catch (KeyStoreException e) {
            throw new CryptoCmsException("Problem during accessing the certificate for the alias '" + alias + "' in the signer " + this, e);
        }
        if (cert instanceof X509Certificate) {
            return (X509Certificate)cert;
        }
        throw new CryptoCmsNoKeyOrCertificateForAliasException("No X.509 certificate found for alias '" + alias + "' in the keystore of signer " + this);
    }

    @Override
    public Certificate[] getCertificateChain(Exchange exchange) throws CryptoCmsException {
        if (includeCertificates) {
            String alias = getPrivateKeyAlias(exchange);
            Certificate[] certs;
            try {
                certs = getKeyStore().getCertificateChain(alias);
            } catch (KeyStoreException e) {
                throw new CryptoCmsException("Problem during accessing the certificate chain for the alias '" + alias + "' in the keystore of signer " + this, e);
            }
            if (certs == null) {
                return new Certificate[0];
            } else {
                return certs;
            }
        } else {
            return new Certificate[0];
        }
    }

    /**
     * Signed attributes of the Signed Data instance. By default contentType,
     * signingTime, messageDigest, and id-aa-CMSAlgorithmProtection are set.
     */
    public void setSignedAttributeGenerator(CMSAttributeTableGenerator signedAttributeGenerator) {
        this.signedAttributeGenerator = signedAttributeGenerator;
    }

    /**
     * Unsigned attributes of the Signed Data instance. By default no unsigned
     * attribute is set.
     */
    public void setUnsignedAttributeGenerator(CMSAttributeTableGenerator unsignedAttributeGenerator) {
        this.unsignedAttributeGenerator = unsignedAttributeGenerator;
    }

    @Override
    public CMSAttributeTableGenerator getSignedAttributeGenerator(Exchange exchange) throws CryptoCmsException {
        return signedAttributeGenerator;
    }

    @Override
    public CMSAttributeTableGenerator getUnsignedAttributeGenerator(Exchange exchange) throws CryptoCmsException {
        return unsignedAttributeGenerator;
    }

    @Override
    public String toString() {
        return "private key alias=" + privateKeyAlias + ", signature algorithm=" + signatureAlgorithm + ", isIncludeCertificates=" + includeCertificates;
    }
}
