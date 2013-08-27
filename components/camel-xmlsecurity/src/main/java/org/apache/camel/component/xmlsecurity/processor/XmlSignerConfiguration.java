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
package org.apache.camel.component.xmlsecurity.processor;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.xmlsecurity.api.KeyAccessor;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureConstants;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureHelper;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureProperties;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureTransform;

public class XmlSignerConfiguration extends XmlSignatureConfiguration {

    private KeyAccessor keyAccessor;

    /**
     * Optional canonicalization method for SignerInfo. Default value is
     * {@link CanonicalizationMethod#INCLUSIVE}.
     * 
     */
    private AlgorithmMethod canonicalizationMethod = new XmlSignatureTransform(CanonicalizationMethod.INCLUSIVE);

    /**
     * Optional transform methods. Default value is
     * {@link CanonicalizationMethod#INCLUSIVE}.
     */
    private List<AlgorithmMethod> transformMethods = Collections.singletonList(XmlSignatureHelper
            .getCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE));

    private String signatureAlgorithm = "http://www.w3.org/2000/09/xmldsig#rsa-sha1";

    /**
     * Digest algorithm URI. Optional parameter. This digest algorithm is used
     * for calculating the digest of the input message. If this digest algorithm
     * is not specified then the digest algorithm is calculated from the
     * signature algorithm. Example: "http://www.w3.org/2001/04/xmlenc#sha256"
     */
    private String digestAlgorithm;

    private Boolean addKeyInfoReference = Boolean.TRUE;

    private String prefixForXmlSignatureNamespace = "ds";

    private String contentObjectId;
    
    /**
     * The URI of the content reference. If <code>null</code> then the URI will
     * be set to "" in the enveloped XML signature case or set to "#[object_id]"
     * in the enveloping XML signature case. This value can be overwritten by
     * the header {@link XmlSignatureConstants#HEADER_CONTENT_REFERENCE_URI}.
     */
    private String contentReferenceUri;

    /**
     * Type of the content reference. The default value is <code>null</code>.
     * This value can be overwritten by the header
     * {@link XmlSignatureConstants#HEADER_CONTENT_REFERENCE_TYPE}.
     */
    private String contentReferenceType;

    private String parentLocalName;

    private String parentNamespace;

    /**
     * Indicator whether the message body contains plain text. The default value
     * is <code>false</code>, indicating that the message body contains XML. The
     * value can be overwritten by the header
     * {@link XmlSignatureConstants#HEADER_MESSAGE_IS_PLAIN_TEXT}.
     */
    private Boolean plainText = Boolean.FALSE;

    /**
     * Encoding of the plain text. Only relevant if the message body is plain
     * text (see parameter {@link #plainText}. Default value is "UTF-8".
     * 
     */
    private String plainTextEncoding = "UTF-8";

    private XmlSignatureProperties properties;

    /* references that should be resolved when the context changes */
    private String keyAccessorName;
    private String canonicalizationMethodName;
    private String transformMethodsName;
    private String propertiesName;

    public XmlSignerConfiguration() {
        super();
    }

    public XmlSignerConfiguration copy() {
        try {
            return (XmlSignerConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        super.setCamelContext(camelContext);
        // try to retrieve the references once the context is available.
        setTransformMethods(transformMethodsName);
        setCanonicalizationMethod(canonicalizationMethodName);
        setKeyAccessor(keyAccessorName);
        setProperties(propertiesName);
    }

    public KeyAccessor getKeyAccessor() {
        return keyAccessor;
    }

    public void setKeyAccessor(KeyAccessor keyAccessor) {
        this.keyAccessor = keyAccessor;
    }

    /**
     * Sets the reference name for a KeyAccessor that can be found in the
     * registry.
     */
    public void setKeyAccessor(String keyAccessorName) {
        if (getCamelContext() != null && keyAccessorName != null) {
            KeyAccessor accessor = getCamelContext().getRegistry().lookupByNameAndType(keyAccessorName, KeyAccessor.class);
            if (accessor != null) {
                setKeyAccessor(accessor);
            }
        }
        if (keyAccessorName != null) {
            this.keyAccessorName = keyAccessorName;
        }
    }

    public AlgorithmMethod getCanonicalizationMethod() {
        return canonicalizationMethod;
    }

    public void setCanonicalizationMethod(AlgorithmMethod canonicalizationMethod) {
        this.canonicalizationMethod = canonicalizationMethod;
    }

    /**
     * Sets the reference name for a AlgorithmMethod that can be found in the
     * registry.
     */
    public void setCanonicalizationMethod(String canonicalizationMethodName) {
        if (getCamelContext() != null && canonicalizationMethodName != null) {
            AlgorithmMethod method = getCamelContext().getRegistry().lookupByNameAndType(canonicalizationMethodName, AlgorithmMethod.class);
            if (method != null) {
                setCanonicalizationMethod(method);
            }
        }
        if (canonicalizationMethodName != null) {
            this.canonicalizationMethodName = canonicalizationMethodName;
        }
    }

    public List<AlgorithmMethod> getTransformMethods() {
        return transformMethods;
    }

    public void setTransformMethods(List<AlgorithmMethod> transformMethods) {
        this.transformMethods = transformMethods;
    }

    /**
     * Sets the reference name for a List<AlgorithmMethod> that can be found in
     * the registry.
     */
    public void setTransformMethods(String transformMethodsName) {
        if (getCamelContext() != null && transformMethodsName != null) {
            @SuppressWarnings("unchecked")
            List<AlgorithmMethod> list = getCamelContext().getRegistry().lookupByNameAndType(transformMethodsName, List.class);
            if (list != null) {
                setTransformMethods(list);
            }
        }
        if (transformMethodsName != null) {
            this.transformMethodsName = transformMethodsName;
        }
    }

    public String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    /**
     * Signature algorithm. Default value is
     * "http://www.w3.org/2000/09/xmldsig#rsa-sha1".
     * 
     * @param signatureAlgorithm
     *            signature algorithm
     */
    public void setSignatureAlgorithm(String signatureAlgorithm) {
        this.signatureAlgorithm = signatureAlgorithm;
    }

    public String getDigestAlgorithm() {
        return digestAlgorithm;
    }

    public void setDigestAlgorithm(String digestAlgorithm) {
        this.digestAlgorithm = digestAlgorithm;
    }

    public Boolean getAddKeyInfoReference() {
        return addKeyInfoReference;
    }

    /**
     * In order to protect the KeyInfo element from tampering you can add a
     * reference to the signed info element so that it is protected via the
     * signature value. The default value is <tt>true</tt>.
     * <p>
     * Only relevant when a KeyInfo is returned by {@link KeyAccessor}. and
     * {@link KeyInfo#getId()} is not <code>null</code>.
     * 
     * @param addKeyInfoReference
     *            boolean value
     */
    public void setAddKeyInfoReference(Boolean addKeyInfoReference) {
        this.addKeyInfoReference = addKeyInfoReference;
    }

    public String getPrefixForXmlSignatureNamespace() {
        return prefixForXmlSignatureNamespace;
    }

    /**
     * Namespace prefix for the XML signature namespace
     * "http://www.w3.org/2000/09/xmldsig#". Default value is "ds".
     * 
     * If <code>null</code> or an empty value is set then no prefix is used for
     * the XML signature namespace.
     * <p>
     * See best practice
     * http://www.w3.org/TR/xmldsig-bestpractices/#signing-xml-
     * without-namespaces
     * 
     * @param prefixForXmlSignatureNamespace
     *            prefix
     */
    public void setPrefixForXmlSignatureNamespace(String prefixForXmlSignatureNamespace) {
        this.prefixForXmlSignatureNamespace = prefixForXmlSignatureNamespace;
    }

    public String getParentLocalName() {
        return parentLocalName;
    }
    
    public String getContentObjectId() {
        if (contentObjectId == null) {
            contentObjectId = "_" + UUID.randomUUID().toString();
        }
        return contentObjectId;
    }

    /**
     * Local name of the parent element to which the XML signature element will
     * be added. Only relevant for enveloped XML signature. Default value is
     * <code>null</code>. The value must be <code>null</code> for enveloping XML
     * signature.
     * 
     * @param parentLocalName
     *            local name
     */
    public void setParentLocalName(String parentLocalName) {
        this.parentLocalName = parentLocalName;
    }

    public String getParentNamespace() {
        return parentNamespace;
    }

    /**
     * Namespace of the parent element to which the XML signature element will
     * be added. See {@link #setEnvelopedParentLocalName(String)}.
     * 
     * @param parentNamespace
     */
    public void setParentNamespace(String parentNamespace) {
        this.parentNamespace = parentNamespace;
    }

    public String getContentReferenceUri() {
        return contentReferenceUri;
    }

    public void setContentReferenceUri(String referenceUri) {
        this.contentReferenceUri = referenceUri;
    }

    public String getContentReferenceType() {
        return contentReferenceType;
    }

    public void setContentReferenceType(String referenceType) {
        this.contentReferenceType = referenceType;
    }

    public Boolean getPlainText() {
        return plainText;
    }

    public void setPlainText(Boolean plainText) {
        this.plainText = plainText;
    }

    public String getPlainTextEncoding() {
        return plainTextEncoding;
    }

    public void setPlainTextEncoding(String plainTextEncoding) {
        this.plainTextEncoding = plainTextEncoding;
    }

    public XmlSignatureProperties getProperties() {
        return properties;
    }

    public void setProperties(XmlSignatureProperties properties) {
        this.properties = properties;
    }

    /**
     * Sets the reference name for a XmlSignatureProperties that can be found in
     * the registry.
     */
    public void setProperties(String propertiesName) {
        if (getCamelContext() != null && propertiesName != null) {
            XmlSignatureProperties props = getCamelContext().getRegistry().lookupByNameAndType(propertiesName, XmlSignatureProperties.class);
            if (props != null) {
                setProperties(props);
            }
        }
        if (propertiesName != null) {
            this.propertiesName = propertiesName;
        }
    }

}
