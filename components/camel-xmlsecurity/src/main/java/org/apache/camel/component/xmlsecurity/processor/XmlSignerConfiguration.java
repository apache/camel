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
package org.apache.camel.component.xmlsecurity.processor;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.spec.XPathFilterParameterSpec;

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
    private XPathFilterParameterSpec parentXpath;
    @UriParam
    private List<XPathFilterParameterSpec> xpathsToIdAttributes = Collections.emptyList();
    @UriParam
    private List<AlgorithmMethod> transformMethods = Collections.singletonList(XmlSignatureHelper
            .getCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE));
    @UriParam
    private KeyAccessor keyAccessor;
    @UriParam(defaultValue = "http://www.w3.org/TR/2001/REC-xml-c14n-20010315")
    private AlgorithmMethod canonicalizationMethod = new XmlSignatureTransform(CanonicalizationMethod.INCLUSIVE);
    @UriParam(defaultValue = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256")
    private String signatureAlgorithm = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";
    @UriParam
    private String digestAlgorithm;
    @UriParam(defaultValue = "true")
    private Boolean addKeyInfoReference = Boolean.TRUE;
    @UriParam(defaultValue = "ds")
    private String prefixForXmlSignatureNamespace = "ds";
    @UriParam
    private String contentObjectId;
    @UriParam
    private String signatureId;
    @UriParam
    private String contentReferenceUri;
    @UriParam
    private String contentReferenceType;
    @UriParam
    private String parentLocalName;
    @UriParam
    private String parentNamespace;
    @UriParam(defaultValue = "false")
    private Boolean plainText = Boolean.FALSE;
    @UriParam(defaultValue = "UTF-8")
    private String plainTextEncoding = "UTF-8";
    @UriParam
    private XmlSignatureProperties properties;

    public XmlSignerConfiguration() {
    }

    public XmlSignerConfiguration copy() {
        try {
            return (XmlSignerConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public KeyAccessor getKeyAccessor() {
        return keyAccessor;
    }

    /**
     * For the signing process, a private key is necessary. You specify a key accessor bean which provides this private key.
     * The key accessor bean must implement the KeyAccessor interface. The package org.apache.camel.component.xmlsecurity.api
     * contains the default implementation class DefaultKeyAccessor which reads the private key from a Java keystore.
     */
    public void setKeyAccessor(KeyAccessor keyAccessor) {
        this.keyAccessor = keyAccessor;
    }

    public AlgorithmMethod getCanonicalizationMethod() {
        return canonicalizationMethod;
    }

    /**
     * Canonicalization method used to canonicalize the SignedInfo element before the digest is calculated.
     * You can use the helper methods XmlSignatureHelper.getCanonicalizationMethod(String algorithm)
     * or getCanonicalizationMethod(String algorithm, List<String> inclusiveNamespacePrefixes) to create a canonicalization method.
     */
    public void setCanonicalizationMethod(AlgorithmMethod canonicalizationMethod) {
        this.canonicalizationMethod = canonicalizationMethod;
    }

    public List<AlgorithmMethod> getTransformMethods() {
        return transformMethods;
    }

    /**
     * Transforms which are executed on the message body before the digest is calculated.
     * By default, C14n is added and in the case of enveloped signature (see option parentLocalName) also http://www.w3.org/2000/09/xmldsig#enveloped-signature
     * is added at position 0 of the list. Use methods in XmlSignatureHelper to create the transform methods.
     */
    public void setTransformMethods(List<AlgorithmMethod> transformMethods) {
        this.transformMethods = transformMethods;
    }

    public String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    /**
     * Signature algorithm. Default value is
     * "http://www.w3.org/2000/09/xmldsig#rsa-sha1".
     */
    public void setSignatureAlgorithm(String signatureAlgorithm) {
        this.signatureAlgorithm = signatureAlgorithm;
    }

    public String getDigestAlgorithm() {
        return digestAlgorithm;
    }

    /**
     * Digest algorithm URI. Optional parameter. This digest algorithm is used
     * for calculating the digest of the input message. If this digest algorithm
     * is not specified then the digest algorithm is calculated from the
     * signature algorithm. Example: "http://www.w3.org/2001/04/xmlenc#sha256"
     */
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
     */
    public void setContentReferenceUri(String referenceUri) {
        this.contentReferenceUri = referenceUri;
    }

    public String getContentReferenceType() {
        return contentReferenceType;
    }

    /**
     * Type of the content reference. The default value is <code>null</code>.
     * This value can be overwritten by the header
     * {@link XmlSignatureConstants#HEADER_CONTENT_REFERENCE_TYPE}.
     */
    public void setContentReferenceType(String referenceType) {
        this.contentReferenceType = referenceType;
    }

    public Boolean getPlainText() {
        return plainText;
    }

    /**
     * Indicator whether the message body contains plain text. The default value
     * is <code>false</code>, indicating that the message body contains XML. The
     * value can be overwritten by the header
     * {@link XmlSignatureConstants#HEADER_MESSAGE_IS_PLAIN_TEXT}.
     */
    public void setPlainText(Boolean plainText) {
        this.plainText = plainText;
    }

    public String getPlainTextEncoding() {
        return plainTextEncoding;
    }

    /**
     * Encoding of the plain text. Only relevant if the message body is plain
     * text (see parameter {@link #plainText}. Default value is "UTF-8".
     */
    public void setPlainTextEncoding(String plainTextEncoding) {
        this.plainTextEncoding = plainTextEncoding;
    }

    public XmlSignatureProperties getProperties() {
        return properties;
    }

    /**
     * For adding additional References and Objects to the XML signature which contain additional properties,
     * you can provide a bean which implements the XmlSignatureProperties interface.
     */
    public void setProperties(XmlSignatureProperties properties) {
        this.properties = properties;
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

    /**
     * Sets the XPath to find the parent node in the enveloped case.
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
