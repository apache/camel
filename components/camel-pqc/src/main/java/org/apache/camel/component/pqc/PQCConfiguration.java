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
import java.security.Signature;

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
    @UriParam(enums = "MLDSA,SLHDSA,LMS,XMSS,FALCON,PICNIC,RAINBOW")
    @Metadata(label = "advanced")
    private String signatureAlgorithm;
    @UriParam
    @Metadata(label = "advanced", autowired = true)
    private KeyGenerator keyGenerator;
    @UriParam(enums = "MLKEM,BIKE,HQC,CMCE,SABER,FRODO,NTRU,NTRULPRime")
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
