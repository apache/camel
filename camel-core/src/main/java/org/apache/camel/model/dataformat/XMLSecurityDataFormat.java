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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;

/**
 * Represents as XML Security Encrypter/Decrypter {@link DataFormat}
 */
@XmlRootElement(name = "secureXML")
@XmlAccessorType(XmlAccessType.FIELD)
public class XMLSecurityDataFormat extends DataFormatDefinition {

    private static final transient String TRIPLEDES = "http://www.w3.org/2001/04/xmlenc#tripledes-cbc";

    @XmlAttribute(required = false)
    private String xmlCipherAlgorithm;
    @XmlAttribute(required = false)
    private String passPhrase;
    @XmlAttribute(required = false)
    private String secureTag;
    @XmlAttribute(required = false)
    private Boolean secureTagContents;

    public XMLSecurityDataFormat() {
        super("secureXML");
    }

    public XMLSecurityDataFormat(String secureTag, boolean secureTagContents) {
        this();
        this.setSecureTag(secureTag);
        this.setSecureTagContents(secureTagContents);
    }

    public XMLSecurityDataFormat(String secureTag, boolean secureTagContents, String passPhrase) {
        this();
        this.setSecureTag(secureTag);
        this.setSecureTagContents(secureTagContents);
        this.setPassPhrase(passPhrase);
    }

    public XMLSecurityDataFormat(String secureTag, boolean secureTagContents, String passPhrase,
                                 String xmlCipherAlgorithm) {
        this();
        this.setSecureTag(secureTag);
        this.setSecureTagContents(secureTagContents);
        this.setPassPhrase(passPhrase);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat) {
        if (getSecureTag() != null) {
            setProperty(dataFormat, "secureTag", getSecureTag());
        } else {
            setProperty(dataFormat, "secureTag", "");
        }

        setProperty(dataFormat, "secureTagContents", isSecureTagContents());

        if (passPhrase != null) {
            setProperty(dataFormat, "passPhrase", getPassPhrase());
        } else {
            setProperty(dataFormat, "passPhrase", "Just another 24 Byte key".getBytes());
        }
        if (getXmlCipherAlgorithm() != null) {
            setProperty(dataFormat, "xmlCipherAlgorithm", getXmlCipherAlgorithm());
        } else {
            setProperty(dataFormat, "xmlCipherAlgorithm", TRIPLEDES);
        }
    }


    public String getXmlCipherAlgorithm() {
        return xmlCipherAlgorithm;
    }

    public void setXmlCipherAlgorithm(String xmlCipherAlgorithm) {
        this.xmlCipherAlgorithm = xmlCipherAlgorithm;
    }

    public String getPassPhrase() {
        return passPhrase;
    }

    public void setPassPhrase(String passPhrase) {
        this.passPhrase = passPhrase;
    }

    public String getSecureTag() {
        return secureTag;
    }

    public void setSecureTag(String secureTag) {
        this.secureTag = secureTag;
    }

    public Boolean getSecureTagContents() {
        return secureTagContents;
    }

    public void setSecureTagContents(Boolean secureTagContents) {
        this.secureTagContents = secureTagContents;
    }

    public boolean isSecureTagContents() {
        return secureTagContents != null && secureTagContents;
    }
}
