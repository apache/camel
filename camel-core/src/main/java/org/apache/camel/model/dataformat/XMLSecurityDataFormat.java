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

import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.util.jsse.KeyStoreParameters;

/**
 * Represents as XML Security Encrypter/Decrypter {@link DataFormat}
 */
@XmlRootElement(name = "secureXML")
@XmlAccessorType(XmlAccessType.FIELD)
public class XMLSecurityDataFormat extends DataFormatDefinition implements NamespaceAware {

    private static final String TRIPLEDES = "http://www.w3.org/2001/04/xmlenc#tripledes-cbc";

    @XmlAttribute
    private String xmlCipherAlgorithm;
    @XmlAttribute
    private String passPhrase;
    @XmlAttribute
    private String secureTag;
    @XmlAttribute
    private Boolean secureTagContents;
    @XmlAttribute
    private String keyCipherAlgorithm;
    @XmlAttribute
    private String recipientKeyAlias;
    @XmlAttribute
    private String keyOrTrustStoreParametersId;
    @XmlAttribute
    private String keyPassword;
    @XmlAttribute
    private String digestAlgorithm;
    @XmlAttribute
    private String mgfAlgorithm;

    @XmlTransient
    private KeyStoreParameters keyOrTrustStoreParameters;
    
    @XmlTransient
    private Map<String, String> namespaces;
    
    
    public XMLSecurityDataFormat() {
        super("secureXML");
    }

    public XMLSecurityDataFormat(String secureTag, boolean secureTagContents) {
        this();
        this.setSecureTag(secureTag);
        this.setSecureTagContents(secureTagContents);
    }
    
    public XMLSecurityDataFormat(String secureTag, Map<String, String> namespaces, boolean secureTagContents) {
        this();
        this.setSecureTag(secureTag);
        this.setSecureTagContents(secureTagContents);
        this.setNamespaces(namespaces);
    }

    public XMLSecurityDataFormat(String secureTag, boolean secureTagContents, String passPhrase) {
        this(secureTag, secureTagContents);
        this.setPassPhrase(passPhrase);
    }
    
    public XMLSecurityDataFormat(String secureTag, Map<String, String> namespaces, boolean secureTagContents, 
                                 String passPhrase) {
        this(secureTag, secureTagContents);
        this.setPassPhrase(passPhrase);
        this.setNamespaces(namespaces);
    }
    
    public XMLSecurityDataFormat(String secureTag, boolean secureTagContents, String passPhrase,
                                 String xmlCipherAlgorithm) {
        this(secureTag, secureTagContents, passPhrase);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
    }
    
    public XMLSecurityDataFormat(String secureTag, Map<String, String> namespaces, boolean secureTagContents, String passPhrase,
                                 String xmlCipherAlgorithm) {
        this(secureTag, secureTagContents, passPhrase);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        this.setNamespaces(namespaces);
    }
    
    /**
     * @deprecated  use {{@link #XMLSecurityDataFormat(String, boolean, String, String, String, String)} or 
     *                  {{@link #XMLSecurityDataFormat(String, boolean, String, String, String, KeyStoreParameters)} instead
     */
    @Deprecated
    public XMLSecurityDataFormat(String secureTag, boolean secureTagContents, String recipientKeyAlias,
            String xmlCipherAlgorithm, String keyCipherAlgorithm) {
        this(secureTag, secureTagContents);
        this.setRecipientKeyAlias(recipientKeyAlias);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        this.setKeyCipherAlgorithm(keyCipherAlgorithm);
    }

    public XMLSecurityDataFormat(String secureTag, boolean secureTagContents, String recipientKeyAlias,
                                 String xmlCipherAlgorithm, String keyCipherAlgorithm, String keyOrTrustStoreParametersId) {
        this(secureTag, secureTagContents);
        this.setRecipientKeyAlias(recipientKeyAlias);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        this.setKeyCipherAlgorithm(keyCipherAlgorithm);
        this.setKeyOrTrustStoreParametersId(keyOrTrustStoreParametersId);
    }
    
    public XMLSecurityDataFormat(String secureTag, boolean secureTagContents, String recipientKeyAlias,
            String xmlCipherAlgorithm, String keyCipherAlgorithm, KeyStoreParameters keyOrTrustStoreParameters) {
        this(secureTag, secureTagContents);
        this.setRecipientKeyAlias(recipientKeyAlias);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        this.setKeyCipherAlgorithm(keyCipherAlgorithm);
        this.setKeyOrTrustStoreParameters(keyOrTrustStoreParameters);
    }

    public XMLSecurityDataFormat(String secureTag, boolean secureTagContents, String recipientKeyAlias,
            String xmlCipherAlgorithm, String keyCipherAlgorithm, String keyOrTrustStoreParametersId, String keyPassword) {
        this(secureTag, secureTagContents);
        this.setRecipientKeyAlias(recipientKeyAlias);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        this.setKeyCipherAlgorithm(keyCipherAlgorithm);
        this.setKeyOrTrustStoreParametersId(keyOrTrustStoreParametersId);
        this.setKeyPassword(keyPassword);
    }

    public XMLSecurityDataFormat(String secureTag, boolean secureTagContents, String recipientKeyAlias,
        String xmlCipherAlgorithm, String keyCipherAlgorithm, KeyStoreParameters keyOrTrustStoreParameters, String keyPassword) {
        this(secureTag, secureTagContents);
        this.setRecipientKeyAlias(recipientKeyAlias);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        this.setKeyCipherAlgorithm(keyCipherAlgorithm);
        this.setKeyOrTrustStoreParameters(keyOrTrustStoreParameters);
        this.setKeyPassword(keyPassword);
    }
    
    /**
     * @deprecated  use {{@link #XMLSecurityDataFormat(String, Map, boolean, String, String, String, String)} or 
     *                  {{@link #XMLSecurityDataFormat(String, Map, boolean, String, String, String, KeyStoreParameters)} instead
     */
    @Deprecated
    public XMLSecurityDataFormat(String secureTag, Map<String, String> namespaces, boolean secureTagContents, String recipientKeyAlias,
            String xmlCipherAlgorithm, String keyCipherAlgorithm) {
        this(secureTag, secureTagContents);
        this.setRecipientKeyAlias(recipientKeyAlias);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        this.setKeyCipherAlgorithm(keyCipherAlgorithm);
        this.setNamespaces(namespaces);
    }
    
    public XMLSecurityDataFormat(String secureTag, Map<String, String> namespaces, boolean secureTagContents, String recipientKeyAlias,
            String xmlCipherAlgorithm, String keyCipherAlgorithm, String keyOrTrustStoreParametersId) {
        this(secureTag, secureTagContents);
        this.setRecipientKeyAlias(recipientKeyAlias);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        this.setKeyCipherAlgorithm(keyCipherAlgorithm);
        this.setNamespaces(namespaces);
        this.setKeyOrTrustStoreParametersId(keyOrTrustStoreParametersId);
    }

    public XMLSecurityDataFormat(String secureTag, Map<String, String> namespaces, boolean secureTagContents, String recipientKeyAlias,
            String xmlCipherAlgorithm, String keyCipherAlgorithm, KeyStoreParameters keyOrTrustStoreParameters) {
        this(secureTag, secureTagContents);
        this.setRecipientKeyAlias(recipientKeyAlias);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        this.setKeyCipherAlgorithm(keyCipherAlgorithm);
        this.setNamespaces(namespaces);
        this.setKeyOrTrustStoreParameters(keyOrTrustStoreParameters);
    }
    
    public XMLSecurityDataFormat(String secureTag, Map<String, String> namespaces, boolean secureTagContents, String recipientKeyAlias,
            String xmlCipherAlgorithm, String keyCipherAlgorithm, String keyOrTrustStoreParametersId, String keyPassword) {
        this(secureTag, secureTagContents);
        this.setRecipientKeyAlias(recipientKeyAlias);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        this.setKeyCipherAlgorithm(keyCipherAlgorithm);
        this.setNamespaces(namespaces);
        this.setKeyOrTrustStoreParametersId(keyOrTrustStoreParametersId);
        this.setKeyPassword(keyPassword);
    }

    public XMLSecurityDataFormat(String secureTag, Map<String, String> namespaces, boolean secureTagContents, String recipientKeyAlias,
            String xmlCipherAlgorithm, String keyCipherAlgorithm, KeyStoreParameters keyOrTrustStoreParameters, String keyPassword) {
        this(secureTag, secureTagContents);
        this.setRecipientKeyAlias(recipientKeyAlias);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        this.setKeyCipherAlgorithm(keyCipherAlgorithm);
        this.setNamespaces(namespaces);
        this.setKeyOrTrustStoreParameters(keyOrTrustStoreParameters);
        this.setKeyPassword(keyPassword);
    }
    
    public XMLSecurityDataFormat(String secureTag, Map<String, String> namespaces, boolean secureTagContents, String recipientKeyAlias,
             String xmlCipherAlgorithm, String keyCipherAlgorithm, KeyStoreParameters keyOrTrustStoreParameters, String keyPassword,
             String digestAlgorithm) {
        this(secureTag, secureTagContents);
        this.setRecipientKeyAlias(recipientKeyAlias);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        this.setKeyCipherAlgorithm(keyCipherAlgorithm);
        this.setNamespaces(namespaces);
        this.setKeyOrTrustStoreParameters(keyOrTrustStoreParameters);
        this.setKeyPassword(keyPassword);
        this.setDigestAlgorithm(digestAlgorithm);
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
            setProperty(dataFormat, "passPhrase", getPassPhrase().getBytes());
        } else {
            setProperty(dataFormat, "passPhrase", "Just another 24 Byte key".getBytes());
        }
        if (getXmlCipherAlgorithm() != null) {
            setProperty(dataFormat, "xmlCipherAlgorithm", getXmlCipherAlgorithm());
        } else {
            setProperty(dataFormat, "xmlCipherAlgorithm", TRIPLEDES);
        }
        if (getKeyCipherAlgorithm() != null) {
            setProperty(dataFormat, "keyCipherAlgorithm", getKeyCipherAlgorithm());
        }
        if (getRecipientKeyAlias() != null) {
            setProperty(dataFormat, "recipientKeyAlias", getRecipientKeyAlias());
        }
        if (getKeyOrTrustStoreParametersId() != null) {
            setProperty(dataFormat, "keyOrTrustStoreParametersId", getKeyOrTrustStoreParametersId());
        }
        if (keyOrTrustStoreParameters != null) {
            setProperty(dataFormat, "keyOrTrustStoreParameters", this.keyOrTrustStoreParameters);
        }
        if (namespaces != null) {
            setProperty(dataFormat, "namespaces", this.namespaces);
        }
        if (keyPassword != null) {
            setProperty(dataFormat, "keyPassword", this.getKeyPassword());
        }
        if (digestAlgorithm != null) {
            setProperty(dataFormat, "digestAlgorithm", this.getDigestAlgorithm());
        }
        if (mgfAlgorithm != null) {
            setProperty(dataFormat, "mgfAlgorithm", this.getMgfAlgorithm());
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

    public void setKeyCipherAlgorithm(String keyCipherAlgorithm) {
        this.keyCipherAlgorithm = keyCipherAlgorithm;
    }

    public String getKeyCipherAlgorithm() {
        return keyCipherAlgorithm;
    }

    public void setRecipientKeyAlias(String recipientKeyAlias) {
        this.recipientKeyAlias = recipientKeyAlias;
    }

    public String getRecipientKeyAlias() {
        return recipientKeyAlias;
    }
    
    public void setKeyOrTrustStoreParametersId(String id) {
        this.keyOrTrustStoreParametersId = id;
    }
    
    public String getKeyOrTrustStoreParametersId() {
        return this.keyOrTrustStoreParametersId;
    }
    
    private void setKeyOrTrustStoreParameters(KeyStoreParameters keyOrTrustStoreParameters) {
        this.keyOrTrustStoreParameters = keyOrTrustStoreParameters;
    }
    
    public String getKeyPassword() {
        return this.keyPassword;
    }

    public void setKeyPassword(String keyPassword) {
        this.keyPassword = keyPassword;
    }

    public String getDigestAlgorithm() {
        return digestAlgorithm;
    }

    public void setDigestAlgorithm(String digestAlgorithm) {
        this.digestAlgorithm = digestAlgorithm;
    }

    public String getMgfAlgorithm() {
        return mgfAlgorithm;
    }

    public void setMgfAlgorithm(String mgfAlgorithm) {
        this.mgfAlgorithm = mgfAlgorithm;
    }

    @Override
    public void setNamespaces(Map<String, String> nspaces) {
        if (this.namespaces == null) {
            this.namespaces = new HashMap<String, String>();
        }
        this.namespaces.putAll(nspaces);
    }
    
}
