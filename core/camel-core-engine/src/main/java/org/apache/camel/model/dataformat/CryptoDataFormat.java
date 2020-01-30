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
 * Crypto data format is used for encrypting and decrypting of messages using
 * Java Cryptographic Extension.
 */
@Metadata(firstVersion = "2.3.0", label = "dataformat,transformation,security", title = "Crypto (Java Cryptographic Extension)")
@XmlRootElement(name = "crypto")
@XmlAccessorType(XmlAccessType.FIELD)
public class CryptoDataFormat extends DataFormatDefinition {
    @XmlAttribute
    private String algorithm;
    @XmlAttribute
    private String cryptoProvider;
    @XmlAttribute
    private String keyRef;
    @XmlAttribute
    private String initVectorRef;
    @XmlAttribute
    private String algorithmParameterRef;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer")
    private String buffersize;
    @XmlAttribute
    @Metadata(defaultValue = "HmacSHA1")
    private String macAlgorithm = "HmacSHA1";
    @XmlAttribute
    @Metadata(defaultValue = "true", javaType = "java.lang.Boolean")
    private String shouldAppendHMAC;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String inline;

    public CryptoDataFormat() {
        super("crypto");
    }

    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * The JCE algorithm name indicating the cryptographic algorithm that will
     * be used.
     * <p/>
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
     * Refers to a byte array containing the Initialization Vector that will be
     * used to initialize the Cipher.
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
     * Will lookup the type using the given name as a
     * {@link java.security.spec.AlgorithmParameterSpec} type.
     */
    public void setAlgorithmParameterRef(String algorithmParameterRef) {
        this.algorithmParameterRef = algorithmParameterRef;
    }

    public String getBuffersize() {
        return buffersize;
    }

    /**
     * The size of the buffer used in the signature process.
     */
    public void setBuffersize(String buffersize) {
        this.buffersize = buffersize;
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
     * Flag indicating that a Message Authentication Code should be calculated
     * and appended to the encrypted data.
     */
    public void setShouldAppendHMAC(String shouldAppendHMAC) {
        this.shouldAppendHMAC = shouldAppendHMAC;
    }

    public String getInline() {
        return inline;
    }

    /**
     * Flag indicating that the configured IV should be inlined into the
     * encrypted data stream.
     * <p/>
     * Is by default false.
     */
    public void setInline(String inline) {
        this.inline = inline;
    }
}
