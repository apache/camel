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
import javax.xml.crypto.dsig.spec.XPathFilterParameterSpec;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.xmlsecurity.api.KeyAccessor;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureConstants;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureHelper;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureProperties;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureTransform;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class XmlSignerConfiguration extends XmlSignatureConfiguration {

    @UriParam
    private KeyAccessor keyAccessor;

    /**
     * Optional canonicalization method for SignerInfo. Default value is
     * {@link CanonicalizationMethod#INCLUSIVE}.
     * 
     */
    @UriParam
    private AlgorithmMethod canonicalizationMethod = new XmlSignatureTransform(CanonicalizationMethod.INCLUSIVE);

    /**
     * Optional transform methods. Default value is
     * {@link CanonicalizationMethod#INCLUSIVE}.
     */
    private List<AlgorithmMethod> transformMethods = Collections.singletonList(XmlSignatureHelper
            .getCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE));

    @UriParam
    private String signatureAlgorithm = "http://www.w3.org/2000/09/xmldsig#rsa-sha1";

    /**
     * Digest algorithm URI. Optional parameter. This digest algorithm is used
     * for calculating the digest of the input message. If this digest algorithm
     * is not specified then the digest algorithm is calculated from the
     * signature algorithm. Example: "http://www.w3.org/2001/04/xmlenc#sha256"
     */
    @UriParam
    private String digestAlgorithm;

    @UriParam(defaultValue = "true")
    private Boolean addKeyInfoReference = Boolean.TRUE;

    @UriParam(defaultValue = "ds")
    private String prefixForXmlSignatureNamespace = "ds";

    @UriParam
    private String contentObjectId;

    // default value is null so that a unique ID is generated.
    @UriParam
    private String signatureId;

    /**
     * The URI of the content reference. This value can be overwritten by the
     * header {@link XmlSignatureConstants#HEADER_CONTENT_REFERENCE_URI}. Can
     * only be used in connection with the enveloped case when you specify a
     * schema (see {@link #setSchemaResourceUri(String)}. Will be ignored in the
     * enveloping and detached case.
     */
    @UriParam
    private String contentReferenceUri;

    /**
     * Type of the content reference. The default value is <code>null</code>.
     * This value can be overwritten by the header
     * {@link XmlSignatureConstants#HEADER_CONTENT_REFERENCE_TYPE}.
     */
    @UriParam
    private String contentReferenceType;

    @UriParam
    private String parentLocalName;

    @UriParam
    private String parentNamespace;

    /**
     * Indicator whether the message body contains plain text. The default value
     * is <code>false</code>, indicating that the message body contains XML. The
     * value can be overwritten by the header
     * {@link XmlSignatureConstants#HEADER_MESSAGE_IS_PLAIN_TEXT}.
     */
    @UriParam(defaultValue = "Boolean.FALSE")
    private Boolean plainText = Boolean.FALSE;

    /**
     * Encoding of the plain text. Only relevant if the message body is plain
     * text (see parameter {@link #plainText}. Default value is "UTF-8".
     * 
     */
    @UriParam
    private String plainTextEncoding = "UTF-8";

    private XmlSignatureProperties properties;

    private List<XPathFilterParameterSpec> xpathsToIdAttributes = Collections.emptyList();
    
    private XPathFilterParameterSpec parentXpath;

    /* references that should be resolved when the context changes */
    @UriParam
    private String keyAccessorName;
    @UriParam
    private String canonicalizationMethodName;
    @UriParam
    private String transformMethodsName;
    @UriParam
    private String propertiesName;

    public XmlSignerConfiguration() {
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

    /**
     * Local name of the parent element to which the XML signature element will
     * be added. Only relevant for enveloped XML signature. Alternatively you can 
     * also use {@link #setParentXpath(XPathFilterParameterSpec)}.
     * 
     * <p> Default value is
     * <code>null</code>. The value must be <code>null</code> for enveloping and
     * detached XML signature.
     * <p>
     * This parameter or the parameter {@link #setParentXpath(XPathFilterParameterSpec)}
     * for enveloped signature and the parameter {@link #setXpathsToIdAttributes(List)} 
     * for detached signature must not be set in the same configuration.
     * <p>
     * If the parameters <tt>parentXpath</tt> and <tt>parentLocalName</tt> are specified
     * in the same configuration then an exception is thrown.
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
     * be added.
     */
    public void setParentNamespace(String parentNamespace) {
        this.parentNamespace = parentNamespace;
    }

    public String getContentObjectId() {
        if (contentObjectId == null) {
            // content object ID must always be set, because it is only used in enveloping case.
            contentObjectId = "_" + UUID.randomUUID().toString();
        }
        return contentObjectId;
    }

    /**
     * Sets the content object Id attribute value. By default a UUID is
     * generated. If you set the <code>null</code> value, then a new UUID will
     * be generated. Only used in the enveloping case.
     * 
     * @param contentObjectId
     */
    public void setContentObjectId(String contentObjectId) {
        this.contentObjectId = contentObjectId;
    }

    public String getSignatureId() {
        return signatureId;
    }

    /**
     * Sets the signature Id. If this parameter is not set (null value) then a
     * unique ID is generated for the signature ID (default). If this parameter
     * is set to "" (empty string) then no Id attribute is created in the
     * signature element.
     * 
     * @param signatureId
     */
    public void setSignatureId(String signatureId) {
        this.signatureId = signatureId;
    }

    public String getContentReferenceUri() {
        return contentReferenceUri;
    }

    /**
     * Reference URI for the content to be signed. Only used in the enveloped
     * case. If the reference URI contains an ID attribute value, then the
     * resource schema URI ( {@link #setSchemaResourceUri(String)}) must also be
     * set because the schema validator will then find out which attributes are
     * ID attributes. Will be ignored in the enveloping or detached case.
     * 
     * @param referenceUri
     */
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
            XmlSignatureProperties props = getCamelContext().getRegistry()
                    .lookupByNameAndType(propertiesName, XmlSignatureProperties.class);
            if (props != null) {
                setProperties(props);
            }
        }
        if (propertiesName != null) {
            this.propertiesName = propertiesName;
        }
    }

    public String getKeyAccessorName() {
        return keyAccessorName;
    }

    public void setKeyAccessorName(String keyAccessorName) {
        this.keyAccessorName = keyAccessorName;
    }

    public String getCanonicalizationMethodName() {
        return canonicalizationMethodName;
    }

    public void setCanonicalizationMethodName(String canonicalizationMethodName) {
        this.canonicalizationMethodName = canonicalizationMethodName;
    }

    public String getTransformMethodsName() {
        return transformMethodsName;
    }

    public void setTransformMethodsName(String transformMethodsName) {
        this.transformMethodsName = transformMethodsName;
    }

    public String getPropertiesName() {
        return propertiesName;
    }

    public void setPropertiesName(String propertiesName) {
        this.propertiesName = propertiesName;
    }

    public List<XPathFilterParameterSpec> getXpathsToIdAttributes() {
        return xpathsToIdAttributes;
    }

    /**
     * Define the elements which are signed in the detached case via XPATH
     * expressions to ID attributes (attributes of type ID). For each element
     * found via the XPATH expression a detached signature is created whose
     * reference URI contains the corresponding attribute value (preceded by
     * '#'). The signature becomes the last sibling of the signed element.
     * Elements with deeper hierarchy level are signed first.
     * <p>
     * You can also set the XPATH list dynamically via the header
     * {@link XmlSignatureConstants#HEADER_XPATHS_TO_ID_ATTRIBUTES}.
     * <p>
     * The parameter {@link #setParentLocalName(String)} or {@link #setParentXpath(XPathFilterParameterSpec)}
     * for enveloped signature and this parameter for detached signature must not
     * be set in the same configuration.
     * 
     * @param xpathsToIdAttributes
     */
    public void setXpathsToIdAttributes(List<XPathFilterParameterSpec> xpathsToIdAttributes) {
        if (xpathsToIdAttributes == null) {
            this.xpathsToIdAttributes = Collections.emptyList();
        } else {
            this.xpathsToIdAttributes = Collections.unmodifiableList(xpathsToIdAttributes);
        }
    }

    public XPathFilterParameterSpec getParentXpath() {
        return parentXpath;
    }

    /** Sets the XPath to find the parent node in the enveloped case. 
     * Either you specify the parent node via this method or the local name and namespace of the parent 
     * with the methods {@link #setParentLocalName(String)} and {@link #setParentNamespace(String)}. 
     * <p>
     * Default value is <code>null</code>. The value must be <code>null</code> for enveloping and
     * detached XML signature.
     * <p>
     * If the parameters <tt>parentXpath</tt> and <tt>parentLocalName</tt> are specified
     * in the same configuration then an exception is thrown.
     * 
     * @param parentXpath xpath to the parent node, if the xpath returns several values then the first Element node is used
     */
    public void setParentXpath(XPathFilterParameterSpec parentXpath) {
        this.parentXpath = parentXpath;
    }
    
}
