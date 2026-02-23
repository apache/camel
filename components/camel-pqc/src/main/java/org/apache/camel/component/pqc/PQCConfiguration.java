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
package org.apache.camel.component.pqc;

import java.security.KeyPair;
import java.security.KeyStore;
import java.security.Signature;

import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class PQCConfiguration implements Cloneable {

    @UriPath(description = "Logical name")
    @Metadata(required = true)
    private String label;
    @UriParam
    @Metadata(label = "advanced", autowired = true)
    private KeyPair keyPair;
    @UriParam
    @Metadata(required = true)
    private PQCOperations operation;
    @UriParam
    @Metadata(label = "advanced", autowired = true)
    private Signature signer;
    @UriParam(enums = "MLDSA,SLHDSA,LMS,HSS,XMSS,XMSSMT,DILITHIUM,FALCON,PICNIC,SNOVA,MAYO,SPHINCSPLUS")
    @Metadata(label = "advanced")
    private String signatureAlgorithm;
    @UriParam
    @Metadata(label = "advanced", autowired = true)
    private KeyGenerator keyGenerator;
    @UriParam(enums = "MLKEM,BIKE,HQC,CMCE,SABER,FRODO,NTRU,NTRULPRime,SNTRUPrime,KYBER")
    @Metadata(label = "advanced")
    private String keyEncapsulationAlgorithm;
    @UriParam(enums = "AES,ARIA,RC2,RC5,CAMELLIA,CAST5,CAST6,CHACHA7539,DSTU7624,GOST28147,GOST3412_2015,GRAIN128,HC128,HC256,SALSA20,SEED,SM4,DESEDE")
    @Metadata(label = "advanced")
    private String symmetricKeyAlgorithm;
    @UriParam
    @Metadata(label = "advanced", defaultValue = "128")
    private int symmetricKeyLength = 128;
    @UriParam
    @Metadata(label = "advanced", defaultValue = "false")
    private boolean storeExtractedSecretKeyAsHeader = false;
    @UriParam
    @Metadata(label = "advanced", autowired = true)
    private KeyStore keyStore;
    @UriParam
    @Metadata(label = "advanced")
    private String keyPairAlias;
    @UriParam
    @Metadata(label = "advanced", secret = true)
    private String keyStorePassword;

    // Hybrid cryptography configuration
    @UriParam(enums = "ECDSA_P256,ECDSA_P384,ECDSA_P521,ED25519,ED448,RSA_2048,RSA_3072,RSA_4096",
              description = "The classical signature algorithm to use in hybrid operations")
    @Metadata(label = "advanced")
    private String classicalSignatureAlgorithm;

    @UriParam(enums = "ECDH_P256,ECDH_P384,ECDH_P521,X25519,X448",
              description = "The classical key agreement algorithm to use in hybrid KEM operations")
    @Metadata(label = "advanced")
    private String classicalKEMAlgorithm;

    @UriParam(description = "The classical KeyPair to be used in hybrid operations")
    @Metadata(label = "advanced", autowired = true)
    private KeyPair classicalKeyPair;

    @UriParam(description = "The classical Signature instance to be used in hybrid signature operations")
    @Metadata(label = "advanced", autowired = true)
    private Signature classicalSigner;

    @UriParam(description = "The classical KeyAgreement instance to be used in hybrid KEM operations")
    @Metadata(label = "advanced", autowired = true)
    private KeyAgreement classicalKeyAgreement;

    @UriParam(defaultValue = "HKDF-SHA256", enums = "HKDF-SHA256,HKDF-SHA384,HKDF-SHA512",
              description = "The KDF algorithm to use for combining secrets in hybrid KEM operations")
    @Metadata(label = "advanced")
    private String hybridKdfAlgorithm = "HKDF-SHA256";

    public PQCOperations getOperation() {
        return operation;
    }

    /**
     * The operation to perform
     */
    public void setOperation(PQCOperations operation) {
        this.operation = operation;
    }

    public KeyPair getKeyPair() {
        return keyPair;
    }

    /**
     * The KeyPair to be used
     */
    public void setKeyPair(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    public Signature getSigner() {
        return signer;
    }

    /**
     * The Signer to be used
     */
    public void setSigner(Signature signer) {
        this.signer = signer;
    }

    public String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    /**
     * In case there is no signer, we specify an algorithm to build the KeyPair or the Signer
     */
    public void setSignatureAlgorithm(String signatureAlgorithm) {
        this.signatureAlgorithm = signatureAlgorithm;
    }

    public KeyGenerator getKeyGenerator() {
        return keyGenerator;
    }

    /**
     * The Key Generator to be used in encapsulation and extraction
     */
    public void setKeyGenerator(KeyGenerator keyGenerator) {
        this.keyGenerator = keyGenerator;
    }

    public String getKeyEncapsulationAlgorithm() {
        return keyEncapsulationAlgorithm;
    }

    /**
     * In case there is no keyGenerator, we specify an algorithm to build the KeyGenerator
     */
    public void setKeyEncapsulationAlgorithm(String keyEncapsulationAlgorithm) {
        this.keyEncapsulationAlgorithm = keyEncapsulationAlgorithm;
    }

    public String getSymmetricKeyAlgorithm() {
        return symmetricKeyAlgorithm;
    }

    /**
     * In case we are using KEM operations, we need a Symmetric algorithm to be defined for the flow to work.
     */
    public void setSymmetricKeyAlgorithm(String symmetricKeyAlgorithm) {
        this.symmetricKeyAlgorithm = symmetricKeyAlgorithm;
    }

    public int getSymmetricKeyLength() {
        return symmetricKeyLength;
    }

    /**
     * The required length of the symmetric key used
     */
    public void setSymmetricKeyLength(int symmetricKeyLength) {
        this.symmetricKeyLength = symmetricKeyLength;
    }

    public boolean isStoreExtractedSecretKeyAsHeader() {
        return storeExtractedSecretKeyAsHeader;
    }

    /**
     * In the context of extractSecretKeyFromEncapsulation operation, this option define if we want to have the key set
     * as header
     */
    public void setStoreExtractedSecretKeyAsHeader(boolean storeExtractedSecretKeyAsHeader) {
        this.storeExtractedSecretKeyAsHeader = storeExtractedSecretKeyAsHeader;
    }

    public KeyStore getKeyStore() {
        return keyStore;
    }

    /**
     * A KeyStore where we could get Cryptographic material
     */
    public void setKeyStore(KeyStore keyStore) {
        this.keyStore = keyStore;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    /**
     * The KeyStore password to use in combination with KeyStore Parameter
     */
    public void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    public String getKeyPairAlias() {
        return keyPairAlias;
    }

    /**
     * A KeyPair alias to use in combination with KeyStore parameter
     */
    public void setKeyPairAlias(String keyPairAlias) {
        this.keyPairAlias = keyPairAlias;
    }

    public String getClassicalSignatureAlgorithm() {
        return classicalSignatureAlgorithm;
    }

    /**
     * The classical signature algorithm to use in hybrid operations
     */
    public void setClassicalSignatureAlgorithm(String classicalSignatureAlgorithm) {
        this.classicalSignatureAlgorithm = classicalSignatureAlgorithm;
    }

    public String getClassicalKEMAlgorithm() {
        return classicalKEMAlgorithm;
    }

    /**
     * The classical key agreement algorithm to use in hybrid KEM operations
     */
    public void setClassicalKEMAlgorithm(String classicalKEMAlgorithm) {
        this.classicalKEMAlgorithm = classicalKEMAlgorithm;
    }

    public KeyPair getClassicalKeyPair() {
        return classicalKeyPair;
    }

    /**
     * The classical KeyPair to be used in hybrid operations
     */
    public void setClassicalKeyPair(KeyPair classicalKeyPair) {
        this.classicalKeyPair = classicalKeyPair;
    }

    public Signature getClassicalSigner() {
        return classicalSigner;
    }

    /**
     * The classical Signature instance to be used in hybrid signature operations
     */
    public void setClassicalSigner(Signature classicalSigner) {
        this.classicalSigner = classicalSigner;
    }

    public KeyAgreement getClassicalKeyAgreement() {
        return classicalKeyAgreement;
    }

    /**
     * The classical KeyAgreement instance to be used in hybrid KEM operations
     */
    public void setClassicalKeyAgreement(KeyAgreement classicalKeyAgreement) {
        this.classicalKeyAgreement = classicalKeyAgreement;
    }

    public String getHybridKdfAlgorithm() {
        return hybridKdfAlgorithm;
    }

    /**
     * The KDF algorithm to use for combining secrets in hybrid KEM operations
     */
    public void setHybridKdfAlgorithm(String hybridKdfAlgorithm) {
        this.hybridKdfAlgorithm = hybridKdfAlgorithm;
    }

    // *************************************************
    //
    // *************************************************

    public PQCConfiguration copy() {
        try {
            return (PQCConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
