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
package org.apache.camel.model.dataformat;

import java.security.Key;
import java.security.spec.AlgorithmParameterSpec;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

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
    private Integer buffersize;
    @XmlAttribute
    private String macAlgorithm = "HmacSHA1";
    @XmlAttribute
    private Boolean shouldAppendHMAC;
    @XmlAttribute
    private Boolean inline;

    public CryptoDataFormat() {
        super("crypto");
    }

    @Override
    protected DataFormat createDataFormat(RouteContext routeContext) {
        DataFormat cryptoFormat = super.createDataFormat(routeContext);

        if (ObjectHelper.isNotEmpty(keyRef)) {
            Key key = CamelContextHelper.mandatoryLookup(routeContext.getCamelContext(), keyRef, Key.class);
            setProperty(cryptoFormat, "key", key);
        }
        if (ObjectHelper.isNotEmpty(algorithmParameterRef)) {
            AlgorithmParameterSpec spec = CamelContextHelper.mandatoryLookup(routeContext.getCamelContext(),
                    algorithmParameterRef, AlgorithmParameterSpec.class);
            setProperty(cryptoFormat, "AlgorithmParameterSpec", spec);
        }
        if (ObjectHelper.isNotEmpty(initVectorRef)) {
            byte[] iv = CamelContextHelper.mandatoryLookup(routeContext.getCamelContext(), initVectorRef, byte[].class);
            setProperty(cryptoFormat, "InitializationVector", iv);
        }
        return cryptoFormat;
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat) {
        Boolean answer = ObjectHelper.toBoolean(shouldAppendHMAC);
        if (answer != null && !answer) {
            setProperty(dataFormat, "shouldAppendHMAC", Boolean.FALSE);
        } else {
            setProperty(dataFormat, "shouldAppendHMAC", Boolean.TRUE);
        }
        answer = ObjectHelper.toBoolean(inline);
        if (answer != null && answer) {
            setProperty(dataFormat, "shouldInlineInitializationVector", Boolean.TRUE);
        } else {
            setProperty(dataFormat, "shouldInlineInitializationVector", Boolean.FALSE);
        }
        if (algorithm != null) {
            setProperty(dataFormat, "algorithm", algorithm);
        }
        if (cryptoProvider != null) {
            setProperty(dataFormat, "cryptoProvider", cryptoProvider);
        }
        if (macAlgorithm != null) {
            setProperty(dataFormat, "macAlgorithm", macAlgorithm);
        }
        if (buffersize != null) {
            setProperty(dataFormat, "buffersize", buffersize);
        }
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getCryptoProvider() {
        return cryptoProvider;
    }

    public void setCryptoProvider(String cryptoProvider) {
        this.cryptoProvider = cryptoProvider;
    }

    public String getKeyRef() {
        return keyRef;
    }

    public void setKeyRef(String keyRef) {
        this.keyRef = keyRef;
    }

    public String getInitVectorRef() {
        return initVectorRef;
    }

    public void setInitVectorRef(String initVectorRef) {
        this.initVectorRef = initVectorRef;
    }

    public String getAlgorithmParameterRef() {
        return algorithmParameterRef;
    }

    public void setAlgorithmParameterRef(String algorithmParameterRef) {
        this.algorithmParameterRef = algorithmParameterRef;
    }

    public Integer getBuffersize() {
        return buffersize;
    }

    public void setBuffersize(Integer buffersize) {
        this.buffersize = buffersize;
    }

    public String getMacAlgorithm() {
        return macAlgorithm;
    }

    public void setMacAlgorithm(String macAlgorithm) {
        this.macAlgorithm = macAlgorithm;
    }

    public Boolean getShouldAppendHMAC() {
        return shouldAppendHMAC;
    }

    public void setShouldAppendHMAC(Boolean shouldAppendHMAC) {
        this.shouldAppendHMAC = shouldAppendHMAC;
    }

    public Boolean getInline() {
        return inline;
    }

    public void setInline(Boolean inline) {
        this.inline = inline;
    }
}
