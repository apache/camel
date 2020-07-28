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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Encrypt and decrypt messages using Java Cryptographic Extension (JCE) and PGP.
 */
@Metadata(firstVersion = "2.9.0", label = "dataformat,transformation,security", title = "PGP")
@XmlRootElement(name = "pgp")
@XmlAccessorType(XmlAccessType.FIELD)
public class PGPDataFormat extends DataFormatDefinition {
    @XmlAttribute
    private String keyUserid;
    @XmlAttribute
    private String signatureKeyUserid;
    @XmlAttribute
    private String password;
    @XmlAttribute
    private String signaturePassword;
    @XmlAttribute
    private String keyFileName;
    @XmlAttribute
    private String signatureKeyFileName;
    @XmlAttribute
    private String signatureKeyRing;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String armored;
    @XmlAttribute
    @Metadata(defaultValue = "true", javaType = "java.lang.Boolean")
    private String integrity;
    @XmlAttribute
    private String provider;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer")
    private String algorithm;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer")
    private String compressionAlgorithm;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer")
    private String hashAlgorithm;
    @XmlAttribute
    private String signatureVerificationOption;

    public PGPDataFormat() {
        super("pgp");
    }

    public String getSignatureKeyUserid() {
        return signatureKeyUserid;
    }

    /**
     * User ID of the key in the PGP keyring used for signing (during encryption) or signature verification (during
     * decryption). During the signature verification process the specified User ID restricts the public keys from the
     * public keyring which can be used for the verification. If no User ID is specified for the signature verficiation
     * then any public key in the public keyring can be used for the verification. Can also be only a part of a user ID.
     * For example, if the user ID is "Test User <test@camel.com>" then you can use the part "Test User" or
     * "<test@camel.com>" to address the User ID.
     */
    public void setSignatureKeyUserid(String signatureKeyUserid) {
        this.signatureKeyUserid = signatureKeyUserid;
    }

    public String getSignaturePassword() {
        return signaturePassword;
    }

    /**
     * Password used when opening the private key used for signing (during encryption).
     */
    public void setSignaturePassword(String signaturePassword) {
        this.signaturePassword = signaturePassword;
    }

    public String getSignatureKeyFileName() {
        return signatureKeyFileName;
    }

    /**
     * Filename of the keyring to use for signing (during encryption) or for signature verification (during decryption);
     * must be accessible as a classpath resource (but you can specify a location in the file system by using the
     * "file:" prefix).
     */
    public void setSignatureKeyFileName(String signatureKeyFileName) {
        this.signatureKeyFileName = signatureKeyFileName;
    }

    public String getSignatureKeyRing() {
        return signatureKeyRing;
    }

    /**
     * Keyring used for signing/verifying as byte array. You can not set the signatureKeyFileName and signatureKeyRing
     * at the same time.
     */
    public void setSignatureKeyRing(String signatureKeyRing) {
        this.signatureKeyRing = signatureKeyRing;
    }

    public String getHashAlgorithm() {
        return hashAlgorithm;
    }

    /**
     * Signature hash algorithm; possible values are defined in org.bouncycastle.bcpg.HashAlgorithmTags; for example 2
     * (= SHA1), 8 (= SHA256), 9 (= SHA384), 10 (= SHA512), 11 (=SHA224). Only relevant for signing.
     */
    public void setHashAlgorithm(String hashAlgorithm) {
        this.hashAlgorithm = hashAlgorithm;
    }

    public String getArmored() {
        return armored;
    }

    /**
     * This option will cause PGP to base64 encode the encrypted text, making it available for copy/paste, etc.
     */
    public void setArmored(String armored) {
        this.armored = armored;
    }

    public String getIntegrity() {
        return integrity;
    }

    /**
     * Adds an integrity check/sign into the encryption file.
     * <p/>
     * The default value is true.
     */
    public void setIntegrity(String integrity) {
        this.integrity = integrity;
    }

    public String getKeyFileName() {
        return keyFileName;
    }

    /**
     * Filename of the keyring; must be accessible as a classpath resource (but you can specify a location in the file
     * system by using the "file:" prefix).
     */
    public void setKeyFileName(String keyFileName) {
        this.keyFileName = keyFileName;
    }

    public String getKeyUserid() {
        return keyUserid;
    }

    /**
     * The user ID of the key in the PGP keyring used during encryption. Can also be only a part of a user ID. For
     * example, if the user ID is "Test User <test@camel.com>" then you can use the part "Test User" or
     * "<test@camel.com>" to address the user ID.
     */
    public void setKeyUserid(String keyUserid) {
        this.keyUserid = keyUserid;
    }

    public String getPassword() {
        return password;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * Symmetric key encryption algorithm; possible values are defined in
     * org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags; for example 2 (= TRIPLE DES), 3 (= CAST5), 4 (= BLOWFISH), 6 (=
     * DES), 7 (= AES_128). Only relevant for encrypting.
     */
    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getCompressionAlgorithm() {
        return compressionAlgorithm;
    }

    /**
     * Compression algorithm; possible values are defined in org.bouncycastle.bcpg.CompressionAlgorithmTags; for example
     * 0 (= UNCOMPRESSED), 1 (= ZIP), 2 (= ZLIB), 3 (= BZIP2). Only relevant for encrypting.
     */
    public void setCompressionAlgorithm(String compressionAlgorithm) {
        this.compressionAlgorithm = compressionAlgorithm;
    }

    /**
     * Password used when opening the private key (not used for encryption).
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getProvider() {
        return provider;
    }

    /**
     * Java Cryptography Extension (JCE) provider, default is Bouncy Castle ("BC"). Alternatively you can use, for
     * example, the IAIK JCE provider; in this case the provider must be registered beforehand and the Bouncy Castle
     * provider must not be registered beforehand. The Sun JCE provider does not work.
     */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getSignatureVerificationOption() {
        return signatureVerificationOption;
    }

    /**
     * Controls the behavior for verifying the signature during unmarshaling. There are 4 values possible: "optional":
     * The PGP message may or may not contain signatures; if it does contain signatures, then a signature verification
     * is executed. "required": The PGP message must contain at least one signature; if this is not the case an
     * exception (PGPException) is thrown. A signature verification is executed. "ignore": Contained signatures in the
     * PGP message are ignored; no signature verification is executed. "no_signature_allowed": The PGP message must not
     * contain a signature; otherwise an exception (PGPException) is thrown.
     */
    public void setSignatureVerificationOption(String signatureVerificationOption) {
        this.signatureVerificationOption = signatureVerificationOption;
    }
}
