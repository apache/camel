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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.builder.DataFormatBuilder;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Encrypt and decrypt messages using Post-Quantum Cryptography Key Encapsulation Mechanisms (KEM).
 */
@Metadata(firstVersion = "4.16.0", label = "dataformat,transformation,security", title = "PQC (Post-Quantum Cryptography)")
@XmlRootElement(name = "pqc")
@XmlAccessorType(XmlAccessType.FIELD)
public class PQCDataFormat extends DataFormatDefinition {

    @XmlAttribute
    @Metadata(defaultValue = "MLKEM", enums = "MLKEM,BIKE,HQC,CMCE,SABER,FRODO,NTRU,NTRULPRime,SNTRUPrime,KYBER")
    private String keyEncapsulationAlgorithm;
    @XmlAttribute
    @Metadata(defaultValue = "AES",
              enums = "AES,ARIA,RC2,RC5,CAMELLIA,CAST5,CAST6,CHACHA7539,DSTU7624,GOST28147,GOST3412_2015,GRAIN128,HC128,HC256,SALSA20,SEED,SM4,DESEDE")
    private String symmetricKeyAlgorithm;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer", defaultValue = "128")
    private String symmetricKeyLength;
    @XmlAttribute
    @Metadata(javaType = "java.security.KeyPair")
    private String keyPair;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Integer", defaultValue = "4096")
    private String bufferSize;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String provider;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "javax.crypto.KeyGenerator")
    private String keyGenerator;

    public PQCDataFormat() {
        super("pqc");
    }

    protected PQCDataFormat(PQCDataFormat source) {
        super(source);
        this.keyEncapsulationAlgorithm = source.keyEncapsulationAlgorithm;
        this.symmetricKeyAlgorithm = source.symmetricKeyAlgorithm;
        this.symmetricKeyLength = source.symmetricKeyLength;
        this.keyPair = source.keyPair;
        this.bufferSize = source.bufferSize;
        this.provider = source.provider;
        this.keyGenerator = source.keyGenerator;
    }

    private PQCDataFormat(Builder builder) {
        this();
        this.keyEncapsulationAlgorithm = builder.keyEncapsulationAlgorithm;
        this.symmetricKeyAlgorithm = builder.symmetricKeyAlgorithm;
        this.symmetricKeyLength = builder.symmetricKeyLength;
        this.keyPair = builder.keyPair;
        this.bufferSize = builder.bufferSize;
        this.provider = builder.provider;
        this.keyGenerator = builder.keyGenerator;
    }

    @Override
    public PQCDataFormat copyDefinition() {
        return new PQCDataFormat(this);
    }

    public String getKeyEncapsulationAlgorithm() {
        return keyEncapsulationAlgorithm;
    }

    /**
     * The Post-Quantum KEM algorithm to use for key encapsulation. Supported values: MLKEM, BIKE, HQC, CMCE, SABER,
     * FRODO, NTRU, NTRULPRime, SNTRUPrime, KYBER
     */
    public void setKeyEncapsulationAlgorithm(String keyEncapsulationAlgorithm) {
        this.keyEncapsulationAlgorithm = keyEncapsulationAlgorithm;
    }

    public String getSymmetricKeyAlgorithm() {
        return symmetricKeyAlgorithm;
    }

    /**
     * The symmetric encryption algorithm to use with the shared secret. Supported values: AES, ARIA, RC2, RC5,
     * CAMELLIA, CAST5, CAST6, CHACHA7539, etc.
     */
    public void setSymmetricKeyAlgorithm(String symmetricKeyAlgorithm) {
        this.symmetricKeyAlgorithm = symmetricKeyAlgorithm;
    }

    public String getSymmetricKeyLength() {
        return symmetricKeyLength;
    }

    /**
     * The length (in bits) of the symmetric key.
     */
    public void setSymmetricKeyLength(String symmetricKeyLength) {
        this.symmetricKeyLength = symmetricKeyLength;
    }

    public String getKeyPair() {
        return keyPair;
    }

    /**
     * Refers to the KeyPair to lookup from the register to use for KEM operations.
     */
    public void setKeyPair(String keyPair) {
        this.keyPair = keyPair;
    }

    public String getBufferSize() {
        return bufferSize;
    }

    /**
     * The size of the buffer used for streaming encryption/decryption.
     */
    public void setBufferSize(String bufferSize) {
        this.bufferSize = bufferSize;
    }

    public String getProvider() {
        return provider;
    }

    /**
     * The JCE security provider to use.
     */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getKeyGenerator() {
        return keyGenerator;
    }

    /**
     * Refers to a custom KeyGenerator to lookup from the register for KEM operations.
     */
    public void setKeyGenerator(String keyGenerator) {
        this.keyGenerator = keyGenerator;
    }

    /**
     * {@code Builder} is a specific builder for {@link PQCDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<PQCDataFormat> {

        private String keyEncapsulationAlgorithm = "MLKEM";
        private String symmetricKeyAlgorithm = "AES";
        private String symmetricKeyLength;
        private String keyPair;
        private String bufferSize;
        private String provider;
        private String keyGenerator;

        /**
         * The Post-Quantum KEM algorithm to use for key encapsulation. Supported values: MLKEM, BIKE, HQC, CMCE, SABER,
         * FRODO, NTRU, NTRULPRime, SNTRUPrime, KYBER
         */
        public Builder keyEncapsulationAlgorithm(String keyEncapsulationAlgorithm) {
            this.keyEncapsulationAlgorithm = keyEncapsulationAlgorithm;
            return this;
        }

        /**
         * The symmetric encryption algorithm to use with the shared secret. Supported values: AES, ARIA, RC2, RC5,
         * CAMELLIA, CAST5, CAST6, CHACHA7539, etc.
         */
        public Builder symmetricKeyAlgorithm(String symmetricKeyAlgorithm) {
            this.symmetricKeyAlgorithm = symmetricKeyAlgorithm;
            return this;
        }

        /**
         * The length (in bits) of the symmetric key.
         */
        public Builder symmetricKeyLength(String symmetricKeyLength) {
            this.symmetricKeyLength = symmetricKeyLength;
            return this;
        }

        /**
         * The length (in bits) of the symmetric key.
         */
        public Builder symmetricKeyLength(int symmetricKeyLength) {
            this.symmetricKeyLength = Integer.toString(symmetricKeyLength);
            return this;
        }

        /**
         * Refers to the KeyPair to lookup from the register to use for KEM operations.
         */
        public Builder keyPair(String keyPair) {
            this.keyPair = keyPair;
            return this;
        }

        /**
         * The size of the buffer used for streaming encryption/decryption.
         */
        public Builder bufferSize(String bufferSize) {
            this.bufferSize = bufferSize;
            return this;
        }

        /**
         * The size of the buffer used for streaming encryption/decryption.
         */
        public Builder bufferSize(int bufferSize) {
            this.bufferSize = Integer.toString(bufferSize);
            return this;
        }

        /**
         * The JCE security provider to use.
         */
        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        /**
         * Refers to a custom KeyGenerator to lookup from the register for KEM operations.
         */
        public Builder keyGenerator(String keyGenerator) {
            this.keyGenerator = keyGenerator;
            return this;
        }

        @Override
        public PQCDataFormat end() {
            return new PQCDataFormat(this);
        }
    }
}
