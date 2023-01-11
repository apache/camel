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
 * Encrypt and decrypt messages using Java Cryptography Extension (JCE).
 */
@Metadata(firstVersion = "2.3.0", label = "dataformat,transformation,security", title = "Crypto (Java Cryptographic Extension)")
@XmlRootElement(name = "crypto")
@XmlAccessorType(XmlAccessType.FIELD)
public class CryptoDataFormat extends DataFormatDefinition {

    @XmlAttribute
    private String algorithm;
    @XmlAttribute
    private String keyRef;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String cryptoProvider;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String initVectorRef;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String algorithmParameterRef;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer", defaultValue = "4096")
    private String bufferSize;
    @XmlAttribute
    @Metadata(defaultValue = "HmacSHA1")
    private String macAlgorithm = "HmacSHA1";
    @XmlAttribute
    @Metadata(defaultValue = "true", javaType = "java.lang.Boolean")
    private String shouldAppendHMAC;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "false", javaType = "java.lang.Boolean")
    private String inline;

    public CryptoDataFormat() {
        super("crypto");
    }

    private CryptoDataFormat(Builder builder) {
        this();
        this.algorithm = builder.algorithm;
        this.keyRef = builder.keyRef;
        this.cryptoProvider = builder.cryptoProvider;
        this.initVectorRef = builder.initVectorRef;
        this.algorithmParameterRef = builder.algorithmParameterRef;
        this.bufferSize = builder.bufferSize;
        this.macAlgorithm = builder.macAlgorithm;
        this.shouldAppendHMAC = builder.shouldAppendHMAC;
        this.inline = builder.inline;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * The JCE algorithm name indicating the cryptographic algorithm that will be used.
     */
    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getCryptoProvider() {
        return cryptoProvider;
    }

    /**
     * The name of the JCE Security Provider that should be used.
     */
    public void setCryptoProvider(String cryptoProvider) {
        this.cryptoProvider = cryptoProvider;
    }

    public String getKeyRef() {
        return keyRef;
    }

    /**
     * Refers to the secret key to lookup from the register to use.
     */
    public void setKeyRef(String keyRef) {
        this.keyRef = keyRef;
    }

    public String getInitVectorRef() {
        return initVectorRef;
    }

    /**
     * Refers to a byte array containing the Initialization Vector that will be used to initialize the Cipher.
     */
    public void setInitVectorRef(String initVectorRef) {
        this.initVectorRef = initVectorRef;
    }

    public String getAlgorithmParameterRef() {
        return algorithmParameterRef;
    }

    /**
     * A JCE AlgorithmParameterSpec used to initialize the Cipher.
     * <p/>
     * Will lookup the type using the given name as a {@link java.security.spec.AlgorithmParameterSpec} type.
     */
    public void setAlgorithmParameterRef(String algorithmParameterRef) {
        this.algorithmParameterRef = algorithmParameterRef;
    }

    public String getBufferSize() {
        return bufferSize;
    }

    /**
     * The size of the buffer used in the signature process.
     */
    public void setBufferSize(String bufferSize) {
        this.bufferSize = bufferSize;
    }

    public String getMacAlgorithm() {
        return macAlgorithm;
    }

    /**
     * The JCE algorithm name indicating the Message Authentication algorithm.
     */
    public void setMacAlgorithm(String macAlgorithm) {
        this.macAlgorithm = macAlgorithm;
    }

    public String getShouldAppendHMAC() {
        return shouldAppendHMAC;
    }

    /**
     * Flag indicating that a Message Authentication Code should be calculated and appended to the encrypted data.
     */
    public void setShouldAppendHMAC(String shouldAppendHMAC) {
        this.shouldAppendHMAC = shouldAppendHMAC;
    }

    public String getInline() {
        return inline;
    }

    /**
     * Flag indicating that the configured IV should be inlined into the encrypted data stream.
     * <p/>
     * Is by default false.
     */
    public void setInline(String inline) {
        this.inline = inline;
    }

    /**
     * {@code Builder} is a specific builder for {@link CryptoDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<CryptoDataFormat> {

        private String algorithm;
        private String keyRef;
        private String cryptoProvider;
        private String initVectorRef;
        private String algorithmParameterRef;
        private String bufferSize;
        private String macAlgorithm = "HmacSHA1";
        private String shouldAppendHMAC;
        private String inline;

        /**
         * The JCE algorithm name indicating the cryptographic algorithm that will be used.
         */
        public Builder algorithm(String algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        /**
         * The name of the JCE Security Provider that should be used.
         */
        public Builder cryptoProvider(String cryptoProvider) {
            this.cryptoProvider = cryptoProvider;
            return this;
        }

        /**
         * Refers to the secret key to lookup from the register to use.
         */
        public Builder keyRef(String keyRef) {
            this.keyRef = keyRef;
            return this;
        }

        /**
         * Refers to a byte array containing the Initialization Vector that will be used to initialize the Cipher.
         */
        public Builder initVectorRef(String initVectorRef) {
            this.initVectorRef = initVectorRef;
            return this;
        }

        /**
         * A JCE AlgorithmParameterSpec used to initialize the Cipher.
         * <p/>
         * Will lookup the type using the given name as a {@link java.security.spec.AlgorithmParameterSpec} type.
         */
        public Builder algorithmParameterRef(String algorithmParameterRef) {
            this.algorithmParameterRef = algorithmParameterRef;
            return this;
        }

        /**
         * The size of the buffer used in the signature process.
         */
        public Builder bufferSize(String bufferSize) {
            this.bufferSize = bufferSize;
            return this;
        }

        /**
         * The size of the buffer used in the signature process.
         */
        public Builder bufferSize(int bufferSize) {
            this.bufferSize = Integer.toString(bufferSize);
            return this;
        }

        /**
         * The JCE algorithm name indicating the Message Authentication algorithm.
         */
        public Builder macAlgorithm(String macAlgorithm) {
            this.macAlgorithm = macAlgorithm;
            return this;
        }

        /**
         * Flag indicating that a Message Authentication Code should be calculated and appended to the encrypted data.
         */
        public Builder shouldAppendHMAC(String shouldAppendHMAC) {
            this.shouldAppendHMAC = shouldAppendHMAC;
            return this;
        }

        /**
         * Flag indicating that a Message Authentication Code should be calculated and appended to the encrypted data.
         */
        public Builder shouldAppendHMAC(boolean shouldAppendHMAC) {
            this.shouldAppendHMAC = Boolean.toString(shouldAppendHMAC);
            return this;
        }

        /**
         * Flag indicating that the configured IV should be inlined into the encrypted data stream.
         * <p/>
         * Is by default false.
         */
        public Builder inline(String inline) {
            this.inline = inline;
            return this;
        }

        /**
         * Flag indicating that the configured IV should be inlined into the encrypted data stream.
         * <p/>
         * Is by default false.
         */
        public Builder inline(boolean inline) {
            this.inline = Boolean.toString(inline);
            return this;
        }

        @Override
        public CryptoDataFormat end() {
            return new CryptoDataFormat(this);
        }
    }
}
