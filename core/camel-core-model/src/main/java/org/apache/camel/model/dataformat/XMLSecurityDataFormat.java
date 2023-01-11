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
package org.apache.camel.model.dataformat;

import java.util.HashMap;
import java.util.Map;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.builder.DataFormatBuilder;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.support.jsse.KeyStoreParameters;

/**
 * Encrypt and decrypt XML payloads using Apache Santuario.
 */
@Metadata(firstVersion = "2.0.0", label = "dataformat,transformation,xml", title = "XML Security")
@XmlRootElement(name = "xmlSecurity")
@XmlAccessorType(XmlAccessType.FIELD)
public class XMLSecurityDataFormat extends DataFormatDefinition implements NamespaceAware {

    @XmlAttribute
    @Metadata(defaultValue = "AES-256-GCM",
              enums = "TRIPLEDES,AES_128,AES_128_GCM,AES_192,AES_192_GCM,AES_256,AES_256_GCM,SEED_128,CAMELLIA_128,CAMELLIA_192,CAMELLIA_256")
    private String xmlCipherAlgorithm;
    @XmlAttribute
    private String passPhrase;
    @XmlAttribute
    @Metadata(label = "advanced")
    private byte[] passPhraseByte;
    @XmlAttribute
    private String secureTag;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String secureTagContents;
    @XmlAttribute
    @Metadata(defaultValue = "RSA_OAEP", enums = "RSA_v1dot5,RSA_OAEP,RSA_OAEP_11")
    private String keyCipherAlgorithm;
    @XmlAttribute
    private String recipientKeyAlias;
    @XmlAttribute
    private String keyOrTrustStoreParametersRef;
    @XmlAttribute
    private String keyPassword;
    @XmlAttribute
    @Metadata(defaultValue = "SHA1", enums = "SHA1,SHA256,SHA512")
    private String digestAlgorithm;
    @XmlAttribute
    @Metadata(defaultValue = "MGF1_SHA1", enums = "MGF1_SHA1,MGF1_SHA256,MGF1_SHA512")
    private String mgfAlgorithm;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "true")
    private String addKeyValueForEncryptedKey;
    @XmlTransient
    private KeyStoreParameters keyOrTrustStoreParameters;
    @XmlTransient
    private Map<String, String> namespaces;

    public XMLSecurityDataFormat() {
        super("xmlSecurity");
    }

    private XMLSecurityDataFormat(Builder builder) {
        this();
        this.xmlCipherAlgorithm = builder.xmlCipherAlgorithm;
        this.passPhrase = builder.passPhrase;
        this.passPhraseByte = builder.passPhraseByte;
        this.secureTag = builder.secureTag;
        this.secureTagContents = builder.secureTagContents;
        this.keyCipherAlgorithm = builder.keyCipherAlgorithm;
        this.recipientKeyAlias = builder.recipientKeyAlias;
        this.keyOrTrustStoreParametersRef = builder.keyOrTrustStoreParametersRef;
        this.keyPassword = builder.keyPassword;
        this.digestAlgorithm = builder.digestAlgorithm;
        this.mgfAlgorithm = builder.mgfAlgorithm;
        this.addKeyValueForEncryptedKey = builder.addKeyValueForEncryptedKey;
        this.keyOrTrustStoreParameters = builder.keyOrTrustStoreParameters;
        this.namespaces = builder.namespaces;
    }

    public String getXmlCipherAlgorithm() {
        return xmlCipherAlgorithm;
    }

    /**
     * The cipher algorithm to be used for encryption/decryption of the XML message content. The available choices are:
     * <ul>
     * <li>XMLCipher.TRIPLEDES</li>
     * <li>XMLCipher.AES_128</li>
     * <li>XMLCipher.AES_128_GCM</li>
     * <li>XMLCipher.AES_192</li>
     * <li>XMLCipher.AES_192_GCM</li>
     * <li>XMLCipher.AES_256</li>
     * <li>XMLCipher.AES_256_GCM</li>
     * <li>XMLCipher.SEED_128</li>
     * <li>XMLCipher.CAMELLIA_128</li>
     * <li>XMLCipher.CAMELLIA_192</li>
     * <li>XMLCipher.CAMELLIA_256</li>
     * </ul>
     * The default value is XMLCipher.AES_256_GCM
     */
    public void setXmlCipherAlgorithm(String xmlCipherAlgorithm) {
        this.xmlCipherAlgorithm = xmlCipherAlgorithm;
    }

    public String getPassPhrase() {
        return passPhrase;
    }

    /**
     * A String used as passPhrase to encrypt/decrypt content. The passPhrase has to be provided. The passPhrase needs
     * to be put together in conjunction with the appropriate encryption algorithm. For example using TRIPLEDES the
     * passPhase can be a "Only another 24 Byte key"
     */
    public void setPassPhrase(String passPhrase) {
        this.passPhrase = passPhrase;
    }

    public byte[] getPassPhraseByte() {
        return passPhraseByte;
    }

    /**
     * A byte[] used as passPhrase to encrypt/decrypt content. The passPhrase has to be provided. The passPhrase needs
     * to be put together in conjunction with the appropriate encryption algorithm. For example using TRIPLEDES the
     * passPhase can be a "Only another 24 Byte key"
     */
    public void setPassPhraseByte(byte[] passPhraseByte) {
        this.passPhraseByte = passPhraseByte;
    }

    public String getSecureTag() {
        return secureTag;
    }

    /**
     * The XPath reference to the XML Element selected for encryption/decryption. If no tag is specified, the entire
     * payload is encrypted/decrypted.
     */
    public void setSecureTag(String secureTag) {
        this.secureTag = secureTag;
    }

    public String getSecureTagContents() {
        return secureTagContents;
    }

    /**
     * A boolean value to specify whether the XML Element is to be encrypted or the contents of the XML Element. false =
     * Element Level. true = Element Content Level.
     */
    public void setSecureTagContents(String secureTagContents) {
        this.secureTagContents = secureTagContents;
    }

    /**
     * The cipher algorithm to be used for encryption/decryption of the asymmetric key. The available choices are:
     * <ul>
     * <li>XMLCipher.RSA_v1dot5</li>
     * <li>XMLCipher.RSA_OAEP</li>
     * <li>XMLCipher.RSA_OAEP_11</li>
     * </ul>
     * The default value is XMLCipher.RSA_OAEP
     */
    public void setKeyCipherAlgorithm(String keyCipherAlgorithm) {
        this.keyCipherAlgorithm = keyCipherAlgorithm;
    }

    public String getKeyCipherAlgorithm() {
        return keyCipherAlgorithm;
    }

    /**
     * The key alias to be used when retrieving the recipient's public or private key from a KeyStore when performing
     * asymmetric key encryption or decryption.
     */
    public void setRecipientKeyAlias(String recipientKeyAlias) {
        this.recipientKeyAlias = recipientKeyAlias;
    }

    public String getRecipientKeyAlias() {
        return recipientKeyAlias;
    }

    /**
     * Refers to a KeyStore instance to lookup in the registry, which is used for configuration options for creating and
     * loading a KeyStore instance that represents the sender's trustStore or recipient's keyStore.
     */
    public void setKeyOrTrustStoreParametersRef(String id) {
        this.keyOrTrustStoreParametersRef = id;
    }

    public String getKeyOrTrustStoreParametersRef() {
        return this.keyOrTrustStoreParametersRef;
    }

    public KeyStoreParameters getKeyOrTrustStoreParameters() {
        return keyOrTrustStoreParameters;
    }

    /**
     * Configuration options for creating and loading a KeyStore instance that represents the sender's trustStore or
     * recipient's keyStore.
     */
    public void setKeyOrTrustStoreParameters(KeyStoreParameters keyOrTrustStoreParameters) {
        this.keyOrTrustStoreParameters = keyOrTrustStoreParameters;
    }

    public String getKeyPassword() {
        return this.keyPassword;
    }

    /**
     * The password to be used for retrieving the private key from the KeyStore. This key is used for asymmetric
     * decryption.
     */
    public void setKeyPassword(String keyPassword) {
        this.keyPassword = keyPassword;
    }

    public String getDigestAlgorithm() {
        return digestAlgorithm;
    }

    /**
     * The digest algorithm to use with the RSA OAEP algorithm. The available choices are:
     * <ul>
     * <li>XMLCipher.SHA1</li>
     * <li>XMLCipher.SHA256</li>
     * <li>XMLCipher.SHA512</li>
     * </ul>
     * The default value is XMLCipher.SHA1
     */
    public void setDigestAlgorithm(String digestAlgorithm) {
        this.digestAlgorithm = digestAlgorithm;
    }

    public String getMgfAlgorithm() {
        return mgfAlgorithm;
    }

    /**
     * The MGF Algorithm to use with the RSA OAEP algorithm. The available choices are:
     * <ul>
     * <li>EncryptionConstants.MGF1_SHA1</li>
     * <li>EncryptionConstants.MGF1_SHA256</li>
     * <li>EncryptionConstants.MGF1_SHA512</li>
     * </ul>
     * The default value is EncryptionConstants.MGF1_SHA1
     */
    public void setMgfAlgorithm(String mgfAlgorithm) {
        this.mgfAlgorithm = mgfAlgorithm;
    }

    public String getAddKeyValueForEncryptedKey() {
        return addKeyValueForEncryptedKey;
    }

    /**
     * Whether to add the public key used to encrypt the session key as a KeyValue in the EncryptedKey structure or not.
     */
    public void setAddKeyValueForEncryptedKey(String addKeyValueForEncryptedKey) {
        this.addKeyValueForEncryptedKey = addKeyValueForEncryptedKey;
    }

    @Override
    public void setNamespaces(Map<String, String> nspaces) {
        if (this.namespaces == null) {
            this.namespaces = new HashMap<>();
        }
        this.namespaces.putAll(nspaces);
    }

    @Override
    public Map<String, String> getNamespaces() {
        return namespaces;
    }

    /**
     * {@code Builder} is a specific builder for {@link XMLSecurityDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<XMLSecurityDataFormat> {

        private String xmlCipherAlgorithm;
        private String passPhrase;
        private byte[] passPhraseByte;
        private String secureTag;
        private String secureTagContents;
        private String keyCipherAlgorithm;
        private String recipientKeyAlias;
        private String keyOrTrustStoreParametersRef;
        private String keyPassword;
        private String digestAlgorithm;
        private String mgfAlgorithm;
        private String addKeyValueForEncryptedKey;
        private KeyStoreParameters keyOrTrustStoreParameters;
        private Map<String, String> namespaces;

        /**
         * The cipher algorithm to be used for encryption/decryption of the XML message content. The available choices
         * are:
         * <ul>
         * <li>XMLCipher.TRIPLEDES</li>
         * <li>XMLCipher.AES_128</li>
         * <li>XMLCipher.AES_128_GCM</li>
         * <li>XMLCipher.AES_192</li>
         * <li>XMLCipher.AES_192_GCM</li>
         * <li>XMLCipher.AES_256</li>
         * <li>XMLCipher.AES_256_GCM</li>
         * <li>XMLCipher.SEED_128</li>
         * <li>XMLCipher.CAMELLIA_128</li>
         * <li>XMLCipher.CAMELLIA_192</li>
         * <li>XMLCipher.CAMELLIA_256</li>
         * </ul>
         * The default value is XMLCipher.AES_256_GCM
         */
        public Builder xmlCipherAlgorithm(String xmlCipherAlgorithm) {
            this.xmlCipherAlgorithm = xmlCipherAlgorithm;
            return this;
        }

        /**
         * A String used as passPhrase to encrypt/decrypt content. The passPhrase has to be provided. The passPhrase
         * needs to be put together in conjunction with the appropriate encryption algorithm. For example using
         * TRIPLEDES the passPhase can be a "Only another 24 Byte key"
         */
        public Builder passPhrase(String passPhrase) {
            this.passPhrase = passPhrase;
            return this;
        }

        /**
         * A byte[] used as passPhrase to encrypt/decrypt content. The passPhrase has to be provided. The passPhrase
         * needs to be put together in conjunction with the appropriate encryption algorithm. For example using
         * TRIPLEDES the passPhase can be a "Only another 24 Byte key"
         */
        public Builder passPhraseByte(byte[] passPhraseByte) {
            this.passPhraseByte = passPhraseByte;
            return this;
        }

        /**
         * The XPath reference to the XML Element selected for encryption/decryption. If no tag is specified, the entire
         * payload is encrypted/decrypted.
         */
        public Builder secureTag(String secureTag) {
            this.secureTag = secureTag;
            return this;
        }

        /**
         * A boolean value to specify whether the XML Element is to be encrypted or the contents of the XML Element.
         * false = Element Level. true = Element Content Level.
         */
        public Builder secureTagContents(String secureTagContents) {
            this.secureTagContents = secureTagContents;
            return this;
        }

        /**
         * A boolean value to specify whether the XML Element is to be encrypted or the contents of the XML Element.
         * false = Element Level. true = Element Content Level.
         */
        public Builder secureTagContents(boolean secureTagContents) {
            this.secureTagContents = Boolean.toString(secureTagContents);
            return this;
        }

        /**
         * The cipher algorithm to be used for encryption/decryption of the asymmetric key. The available choices are:
         * <ul>
         * <li>XMLCipher.RSA_v1dot5</li>
         * <li>XMLCipher.RSA_OAEP</li>
         * <li>XMLCipher.RSA_OAEP_11</li>
         * </ul>
         * The default value is XMLCipher.RSA_OAEP
         */
        public Builder keyCipherAlgorithm(String keyCipherAlgorithm) {
            this.keyCipherAlgorithm = keyCipherAlgorithm;
            return this;
        }

        /**
         * The key alias to be used when retrieving the recipient's public or private key from a KeyStore when
         * performing asymmetric key encryption or decryption.
         */
        public Builder recipientKeyAlias(String recipientKeyAlias) {
            this.recipientKeyAlias = recipientKeyAlias;
            return this;
        }

        /**
         * Refers to a KeyStore instance to lookup in the registry, which is used for configuration options for creating
         * and loading a KeyStore instance that represents the sender's trustStore or recipient's keyStore.
         */
        public Builder keyOrTrustStoreParametersRef(String keyOrTrustStoreParametersRef) {
            this.keyOrTrustStoreParametersRef = keyOrTrustStoreParametersRef;
            return this;
        }

        /**
         * Configuration options for creating and loading a KeyStore instance that represents the sender's trustStore or
         * recipient's keyStore.
         */
        public Builder keyOrTrustStoreParameters(KeyStoreParameters keyOrTrustStoreParameters) {
            this.keyOrTrustStoreParameters = keyOrTrustStoreParameters;
            return this;
        }

        /**
         * The password to be used for retrieving the private key from the KeyStore. This key is used for asymmetric
         * decryption.
         */
        public Builder keyPassword(String keyPassword) {
            this.keyPassword = keyPassword;
            return this;
        }

        /**
         * The digest algorithm to use with the RSA OAEP algorithm. The available choices are:
         * <ul>
         * <li>XMLCipher.SHA1</li>
         * <li>XMLCipher.SHA256</li>
         * <li>XMLCipher.SHA512</li>
         * </ul>
         * The default value is XMLCipher.SHA1
         */
        public Builder digestAlgorithm(String digestAlgorithm) {
            this.digestAlgorithm = digestAlgorithm;
            return this;
        }

        /**
         * The MGF Algorithm to use with the RSA OAEP algorithm. The available choices are:
         * <ul>
         * <li>EncryptionConstants.MGF1_SHA1</li>
         * <li>EncryptionConstants.MGF1_SHA256</li>
         * <li>EncryptionConstants.MGF1_SHA512</li>
         * </ul>
         * The default value is EncryptionConstants.MGF1_SHA1
         */
        public Builder mgfAlgorithm(String mgfAlgorithm) {
            this.mgfAlgorithm = mgfAlgorithm;
            return this;
        }

        /**
         * Whether to add the public key used to encrypt the session key as a KeyValue in the EncryptedKey structure or
         * not.
         */
        public Builder addKeyValueForEncryptedKey(String addKeyValueForEncryptedKey) {
            this.addKeyValueForEncryptedKey = addKeyValueForEncryptedKey;
            return this;
        }

        /**
         * Whether to add the public key used to encrypt the session key as a KeyValue in the EncryptedKey structure or
         * not.
         */
        public Builder addKeyValueForEncryptedKey(boolean addKeyValueForEncryptedKey) {
            this.addKeyValueForEncryptedKey = Boolean.toString(addKeyValueForEncryptedKey);
            return this;
        }

        /**
         * Injects the XML Namespaces of prefix -> uri mappings
         *
         * @param namespaces the XML namespaces with the key of prefixes and the value the URIs
         */
        public Builder namespaces(Map<String, String> namespaces) {
            this.namespaces = namespaces;
            return this;
        }

        @Override
        public XMLSecurityDataFormat end() {
            return new XMLSecurityDataFormat(this);
        }
    }
}
