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
package org.apache.camel.component.crypto;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.Certificate;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.jsse.KeyStoreParameters;

@UriParams
public class DigitalSignatureConfiguration implements Cloneable, CamelContextAware {

    private CamelContext context;

    @UriPath(description = "The operation: sign creates a signature of the message body, and verify checks the signature of"
                           + " the message body. It is the part of the URI after crypto:, as in crypto:sign:name.")
    @Metadata(required = true)
    private CryptoOperation cryptoOperation;
    @UriPath(description = "A name of your choice that tells the endpoints apart, as in crypto:sign:name. The component does"
                           + " not use it.")
    @Metadata(required = true)
    private String name;
    @UriParam(description = "The private key that the sign operation signs with. A private key found in the keystore under the"
                            + " alias is used instead. The verify operation does not use it.",
              security = "secret")
    private PrivateKey privateKey;
    @UriParam(description = "The KeyStoreParameters (resource, type, password and provider of the keystore) that create the"
                            + " keystore of the keystore option. The keystore is loaded when this option is set.",
              label = "advanced")
    private KeyStoreParameters keyStoreParameters;
    @UriParam(description = "The keystore that holds the private key for the sign operation and the certificate for the verify"
                            + " operation. The alias option selects the entry, or the only entry is used when the keystore"
                            + " holds exactly one. An entry found in the keystore is used instead of the privateKey,"
                            + " certificate and publicKey options.")
    private KeyStore keystore;
    @UriParam(description = "The java.security.SecureRandom that the sign operation passes to Signature.initSign. If not set,"
                            + " the signature provider uses its own source of randomness.",
              label = "advanced", security = "secret")
    private SecureRandom secureRandom;
    @UriParam(description = "The standard name of the signature algorithm of the Java Cryptography Architecture, for example"
                            + " SHA256withRSA or SHA256withECDSA. It must match the type of the key, and the sign and verify"
                            + " endpoints must use the same algorithm.",
              defaultValue = "SHA256withRSA")
    private String algorithm = "SHA256withRSA";
    @UriParam(description = "The size in bytes of the buffer that reads the message body into the signature. It must be at"
                            + " least 1.",
              label = "advanced", defaultValue = "" + 2048)
    private Integer bufferSize = 2048;
    @UriParam(description = "The name of the security provider that provides the signature algorithm, for example BC for"
                            + " Bouncy Castle. The provider must be installed. If not set, the first installed provider that"
                            + " supports the algorithm is used.")
    private String provider;
    @UriParam(description = "The name of the header in which the sign operation stores the Base64 encoded signature, and from"
                            + " which the verify operation reads it. If not set, CamelDigitalSignature is used.")
    private String signatureHeaderName;
    @UriParam(description = "The alias of the keystore entry: the sign operation signs with its private key, and the verify"
                            + " operation verifies with its certificate. If not set and the keystore holds exactly one entry,"
                            + " that entry is used.")
    private String alias;
    @UriParam(description = "The password of the private key entry in the keystore, which the sign operation needs to read the"
                            + " key. It is not the password of the keystore itself.",
              label = "security", security = "secret")
    private String password;
    @UriParam(description = "The public key that the verify operation checks the signature with, when neither the keystore nor"
                            + " the certificate option gives a certificate. The sign operation does not use it.",
              label = "advanced")
    private PublicKey publicKey;
    @UriParam(description = "The certificate whose public key the verify operation checks the signature with. A certificate"
                            + " found in the keystore under the alias is used instead, and this option is used instead of the"
                            + " publicKey option.",
              label = "advanced")
    private Certificate certificate;

    @UriParam(description = "The name of a java.security.PublicKey bean in the registry to use as the publicKey option. A name"
                            + " with no such bean is ignored.")
    private String publicKeyName;
    @UriParam(description = "The name of a java.security.cert.Certificate bean in the registry to use as the certificate"
                            + " option. A name with no such bean is ignored.")
    private String certificateName;
    @UriParam(description = "The name of a java.security.PrivateKey bean in the registry to use as the privateKey option. A"
                            + " name with no such bean is ignored.",
              security = "secret")
    private String privateKeyName;
    @UriParam(description = "The name of a java.security.KeyStore bean in the registry to use as the keystore option. A name"
                            + " with no such bean is ignored.")
    private String keystoreName;
    @UriParam(description = "The name of a java.security.SecureRandom bean in the registry to use as the secureRandom option."
                            + " A name with no such bean is ignored.")
    private String secureRandomName;
    @UriParam(description = "Whether to remove the headers whose names start with CamelSignature or CamelDigitalSignature"
                            + " after the operation succeeds. The sign operation removes them before it sets the signature"
                            + " header, and the verify operation also removes the signature header when its name matches.",
              label = "advanced", defaultValue = "true")
    private boolean clearHeaders = true;

    public DigitalSignatureConfiguration copy() {
        try {
            return (DigitalSignatureConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    @Override
    public CamelContext getCamelContext() {
        return context;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        // TODO: this is wrong a configuration should not have CamelContext
        this.context = camelContext;
        // try to retrieve the references once the context is available.
        setKeystoreName(keystoreName);
        setPublicKeyName(publicKeyName);
        setPrivateKeyName(privateKeyName);
        setCertificateName(certificateName);
        setSecureRandomName(secureRandomName);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the JCE name of the Algorithm that should be used for the signer.
     */
    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * Gets the alias used to query the KeyStore for keys and {@link java.security.cert.Certificate Certificates} to be
     * used in signing and verifying exchanges. This value can be provided at runtime via the message header
     * {@link org.apache.camel.component.crypto.DigitalSignatureConstants#KEYSTORE_ALIAS}
     */
    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    /**
     * Get the PrivateKey that should be used to sign the exchange
     */
    public PrivateKey getPrivateKey() {
        return getPrivateKey(alias, password.toCharArray());
    }

    /**
     * Get the PrivateKey that should be used to sign the signature in the exchange using the supplied alias.
     *
     * @param alias the alias used to retrieve the Certificate from the keystore.
     */
    public PrivateKey getPrivateKey(String alias) {
        return getPrivateKey(alias, password.toCharArray());
    }

    /**
     * Get the PrivateKey that should be used to sign the signature in the exchange using the supplied alias.
     *
     * @param alias the alias used to retrieve the Certificate from the keystore.
     */
    public PrivateKey getPrivateKey(String alias, char[] password) {
        PrivateKey pk = null;
        if (alias != null && keystore != null) {
            try {
                pk = (PrivateKey) keystore.getKey(alias, password);
            } catch (Exception e) {
                throw new RuntimeCamelException(e);
            }
        }
        if (pk == null) {
            pk = privateKey;
        }
        return pk;
    }

    public void setPrivateKey(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    public String getPrivateKeyName() {
        return privateKeyName;
    }

    public void setPrivateKeyName(String privateKeyName) {
        if (context != null && privateKeyName != null) {
            PrivateKey pk = context.getRegistry().lookupByNameAndType(privateKeyName, PrivateKey.class);
            if (pk != null) {
                setPrivateKey(pk);
            }
        }
        if (privateKeyName != null) {
            this.privateKeyName = privateKeyName;
        }
    }

    public void setPublicKey(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public String getPublicKeyName() {
        return publicKeyName;
    }

    public void setPublicKeyName(String publicKeyName) {
        if (context != null && publicKeyName != null) {
            PublicKey pk = context.getRegistry().lookupByNameAndType(publicKeyName, PublicKey.class);
            if (pk != null) {
                setPublicKey(pk);
            }
        }
        if (publicKeyName != null) {
            this.publicKeyName = publicKeyName;
        }
    }

    /**
     * get the PublicKey that should be used to verify the signature in the exchange.
     */
    public PublicKey getPublicKey() {
        return publicKey;
    }

    /**
     * Set the Certificate that should be used to verify the signature in the exchange. If a {@link KeyStore} has been
     * configured then this will attempt to retrieve the {@link Certificate}from it using the supplied alias. If either
     * the alias or the Keystore is invalid then the configured certificate will be returned
     *
     * @param alias the alias used to retrieve the Certificate from the keystore.
     */
    public Certificate getCertificate(String alias) throws Exception {
        Certificate cert = null;
        if (alias != null && keystore != null) {
            cert = keystore.getCertificate(alias);
        }
        if (cert == null) {
            cert = certificate;
        }
        return cert;
    }

    /**
     * Get the explicitly configured {@link Certificate} that should be used to verify the signature in the exchange.
     */
    public Certificate getCertificate() {
        return certificate;
    }

    public void setCertificate(Certificate certificate) {
        this.certificate = certificate;
    }

    public String getCertificateName() {
        return certificateName;
    }

    public void setCertificateName(String certificateName) {
        if (context != null && certificateName != null) {
            Certificate certificate = context.getRegistry().lookupByNameAndType(certificateName, Certificate.class);
            if (certificate != null) {
                setCertificate(certificate);
            }
        }
        if (certificateName != null) {
            this.certificateName = certificateName;
        }
    }

    /**
     * Gets the KeyStore that can contain keys and Certificates for use in signing and verifying exchanges. A
     * {@link KeyStore} is typically used with an alias, either one supplied in the Route definition or dynamically via
     * the message header "CamelSignatureKeyStoreAlias". If no alias is supplied and there is only a single entry in the
     * Keystore, then this single entry will be used.
     */
    public KeyStore getKeystore() {
        return keystore;
    }

    public void setKeystore(KeyStore keystore) {
        this.keystore = keystore;
    }

    public String getKeystoreName() {
        return keystoreName;
    }

    public void setKeystoreName(String keystoreName) {
        if (context != null && keystoreName != null) {
            KeyStore keystore = context.getRegistry().lookupByNameAndType(keystoreName, KeyStore.class);
            if (keystore != null) {
                setKeystore(keystore);
            }
        }
        if (keystoreName != null) {
            this.keystoreName = keystoreName;
        }
    }

    /**
     * Gets the password used to access an aliased {@link PrivateKey} in the KeyStore.
     */
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public KeyStoreParameters getKeyStoreParameters() {
        return keyStoreParameters;
    }

    public void setKeyStoreParameters(KeyStoreParameters keyStoreParameters) {
        this.keyStoreParameters = keyStoreParameters;
        if (keyStoreParameters != null) {
            try {
                this.keystore = keyStoreParameters.createKeyStore();
            } catch (Exception e) {
                throw new RuntimeCamelException(e);
            }
        }
    }

    /**
     * Get the SecureRandom used to initialize the Signature service
     */
    public SecureRandom getSecureRandom() {
        return secureRandom;
    }

    public String getSecureRandomName() {
        return secureRandomName;
    }

    public void setSecureRandomName(String randomName) {
        if (context != null && randomName != null) {
            SecureRandom random = context.getRegistry().lookupByNameAndType(randomName, SecureRandom.class);
            if (random != null) {
                setSecureRandom(random);
            }
        }
        if (randomName != null) {
            this.secureRandomName = randomName;
        }
    }

    public void setSecureRandom(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    /**
     * Get the size of the buffer used to read in the Exchange payload data.
     */
    public Integer getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(Integer bufferSize) {
        this.bufferSize = bufferSize;
    }

    /**
     * Get the id of the security provider that provides the configured {@link Signature} algorithm.
     */
    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    /**
     * Get the name of the message header that should be used to store the base64 encoded signature. This defaults to
     * 'CamelDigitalSignature'
     */
    public String getSignatureHeaderName() {
        return signatureHeaderName != null ? signatureHeaderName : DigitalSignatureConstants.SIGNATURE;
    }

    public void setSignatureHeaderName(String signatureHeaderName) {
        this.signatureHeaderName = signatureHeaderName;
    }

    /**
     * Determines if the Signature specific headers be cleared after signing and verification. Defaults to true, and
     * should only be made otherwise at your extreme peril as vital private information such as Keys and passwords may
     * escape if unset.
     *
     * @return true if the Signature headers should be unset, false otherwise
     */
    public boolean isClearHeaders() {
        return clearHeaders;
    }

    public void setClearHeaders(boolean clearHeaders) {
        this.clearHeaders = clearHeaders;
    }

    public void setCryptoOperation(String operation) {
        this.cryptoOperation = CryptoOperation.valueOf(operation);
    }

    public void setCryptoOperation(CryptoOperation operation) {
        this.cryptoOperation = operation;
    }

    /**
     * Gets the Crypto operation that was supplied in the crypto scheme in the endpoint uri
     */
    public CryptoOperation getCryptoOperation() {
        return cryptoOperation;
    }

}
