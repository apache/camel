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
import javax.xml.crypto.dsig.spec.XPathFilterParameterSpec;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.xmlsecurity.api.KeyAccessor;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureHelper;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureProperties;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureTransform;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class XmlSignerConfiguration extends XmlSignatureConfiguration {

    @UriParam(description = "An XPath, as a javax.xml.crypto.dsig.spec.XPathFilterParameterSpec created with"
                            + " XmlSignatureHelper.getXpathFilter, that selects the element to which an enveloped signature is added"
                            + " as the last child; the first element of the result is used. Setting it makes the signature"
                            + " enveloped. Cannot be combined with parentLocalName or xpathsToIdAttributes: the signer then fails.")
    private XPathFilterParameterSpec parentXpath;
    @UriParam(description = "XPaths, as javax.xml.crypto.dsig.spec.XPathFilterParameterSpec created with"
                            + " XmlSignatureHelper.getXpathFilter, that select ID attributes; setting them makes the signatures"
                            + " detached. For each element with a selected ID attribute, the signer creates a signature whose"
                            + " reference URI is # followed by the attribute value, adds it as the last child of the parent of that"
                            + " element, and signs deeper elements first. Requires schemaResourceUri and cannot be combined with"
                            + " parentLocalName or parentXpath. The CamelXmlSignatureXpathsToIdAttributes header replaces this"
                            + " option.")
    private List<XPathFilterParameterSpec> xpathsToIdAttributes = Collections.emptyList();
    @UriParam(description = "The transforms of the reference to the signed content, which are applied to the content before its"
                            + " digest is calculated. Create them with the methods of XmlSignatureHelper. The default is Canonical"
                            + " XML 1.0 (http://www.w3.org/TR/2001/REC-xml-c14n-20010315), and for an enveloped signature the signer"
                            + " puts the enveloped-signature transform first when the list does not contain it. The"
                            + " CamelXmlSignatureTransformMethods header replaces this option.")
    private List<AlgorithmMethod> transformMethods = Collections.singletonList(XmlSignatureHelper
            .getCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE));
    @UriParam(description = "Provides the key that the signer signs with and the optional KeyInfo element that is added to the"
                            + " signature. Required: the signer fails without it. DefaultKeyAccessor of the package"
                            + " org.apache.camel.component.xmlsecurity.api reads the private key and the certificate chain of an"
                            + " alias from a java.security.KeyStore.")
    private KeyAccessor keyAccessor;
    @UriParam(defaultValue = "http://www.w3.org/TR/2001/REC-xml-c14n-20010315",
              description = "The canonicalization method of the SignedInfo element, which is canonicalized before the signature"
                            + " value is calculated. Create it with XmlSignatureHelper.getCanonicalizationMethod(algorithm) or"
                            + " getCanonicalizationMethod(algorithm, inclusiveNamespacePrefixes). The default is Canonical XML 1.0"
                            + " (http://www.w3.org/TR/2001/REC-xml-c14n-20010315).")
    private AlgorithmMethod canonicalizationMethod = new XmlSignatureTransform(CanonicalizationMethod.INCLUSIVE);
    @UriParam(defaultValue = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256",
              description = "The URI of the signature algorithm, which must fit the key of keyAccessor, for example"
                            + " http://www.w3.org/2001/04/xmldsig-more#rsa-sha512 or"
                            + " http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256. The default is RSA with SHA-256.")
    private String signatureAlgorithm = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";
    @UriParam(description = "The URI of the digest algorithm of the references, for example"
                            + " http://www.w3.org/2001/04/xmlenc#sha512. If not set, the signer takes the digest algorithm that the"
                            + " URI of signatureAlgorithm names (SHA-1, SHA-224, SHA-256, SHA-384, SHA-512 or RIPEMD-160), and fails"
                            + " if it names none.")
    private String digestAlgorithm;
    @UriParam(defaultValue = "true",
              description = "Whether to add a reference to the KeyInfo element to the SignedInfo element, so that the signature"
                            + " value also protects the KeyInfo element against changes. Only used when keyAccessor returns a"
                            + " KeyInfo with an Id; DefaultKeyAccessor does so when the keystore has a certificate chain for the"
                            + " alias.")
    private Boolean addKeyInfoReference = Boolean.TRUE;
    @UriParam(defaultValue = "ds",
              description = "The namespace prefix of the elements of the XML signature namespace"
                            + " http://www.w3.org/2000/09/xmldsig#. If empty, the signature elements have no prefix.")
    private String prefixForXmlSignatureNamespace = "ds";
    @UriParam(description = "The Id attribute of the Object element that holds the signed content of an enveloping signature,"
                            + " which the reference to the content points to. If not set, an Id made of an underscore and a random"
                            + " UUID is generated once and then used for all messages of the endpoint. Only used for an enveloping"
                            + " signature.")
    private String contentObjectId;
    @UriParam(description = "The Id attribute of the Signature element. If not set, a new Id made of an underscore and a random"
                            + " UUID is generated for each signature; if empty, the Signature element has no Id attribute. The"
                            + " XmlSignatureProperties of the properties option can replace it, and XAdESSignatureProperties"
                            + " generates one when it is empty.")
    private String signatureId;
    @UriParam(description = "The URI of the reference to the signed content of an enveloped signature. If not set, the URI is"
                            + " empty, which signs the whole document. A URI that points to an ID, such as #id, also requires"
                            + " schemaResourceUri, which declares the ID attributes. The CamelXmlSignatureContentReferenceUri header"
                            + " replaces this option. Not used for an enveloping or detached signature.")
    private String contentReferenceUri;
    @UriParam(description = "The Type attribute of the reference to the signed content, for example"
                            + " http://www.w3.org/2000/09/xmldsig#Object. If not set, the reference has no Type attribute. The"
                            + " CamelXmlSignatureContentReferenceType header replaces this option.")
    private String contentReferenceType;
    @UriParam(description = "The local name of the element to which an enveloped signature is added as the last child; setting it"
                            + " makes the signature enveloped. The first element of the document with this local name and the"
                            + " namespace of parentNamespace is used. Cannot be combined with parentXpath or xpathsToIdAttributes:"
                            + " the signer then fails.")
    private String parentLocalName;
    @UriParam(description = "The namespace of the parentLocalName element. If not set, that element must have no namespace.")
    private String parentNamespace;
    @UriParam(defaultValue = "false",
              description = "Whether the message body is plain text instead of an XML document. The signer then puts the text,"
                            + " decoded with plainTextEncoding, into the Object element of an enveloping signature; plain text"
                            + " cannot get an enveloped or detached signature. The CamelXmlSignatureMessageIsPlainText header"
                            + " replaces this option.")
    private Boolean plainText = Boolean.FALSE;
    @UriParam(defaultValue = "UTF-8",
              description = "The character encoding that the signer decodes a plain text message body with, see plainText. The"
                            + " CamelXmlSignaturePlainTextEncoding header replaces this option.")
    private String plainTextEncoding = "UTF-8";
    @UriParam(description = "A bean that implements org.apache.camel.component.xmlsecurity.api.XmlSignatureProperties, which adds"
                            + " further references and Object elements to each signature and can set the Id of the Signature"
                            + " element. For XAdES-BES and XAdES-EPES signatures, use XAdESSignatureProperties or"
                            + " DefaultXAdESSignatureProperties.")
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

    public void setKeyAccessor(KeyAccessor keyAccessor) {
        this.keyAccessor = keyAccessor;
    }

    public AlgorithmMethod getCanonicalizationMethod() {
        return canonicalizationMethod;
    }

    public void setCanonicalizationMethod(AlgorithmMethod canonicalizationMethod) {
        this.canonicalizationMethod = canonicalizationMethod;
    }

    public List<AlgorithmMethod> getTransformMethods() {
        return transformMethods;
    }

    public void setTransformMethods(List<AlgorithmMethod> transformMethods) {
        this.transformMethods = transformMethods;
    }

    public String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

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

    public void setAddKeyInfoReference(Boolean addKeyInfoReference) {
        this.addKeyInfoReference = addKeyInfoReference;
    }

    public String getPrefixForXmlSignatureNamespace() {
        return prefixForXmlSignatureNamespace;
    }

    public void setPrefixForXmlSignatureNamespace(String prefixForXmlSignatureNamespace) {
        this.prefixForXmlSignatureNamespace = prefixForXmlSignatureNamespace;
    }

    public String getParentLocalName() {
        return parentLocalName;
    }

    public void setParentLocalName(String parentLocalName) {
        this.parentLocalName = parentLocalName;
    }

    public String getParentNamespace() {
        return parentNamespace;
    }

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

    public void setContentObjectId(String contentObjectId) {
        this.contentObjectId = contentObjectId;
    }

    public String getSignatureId() {
        return signatureId;
    }

    public void setSignatureId(String signatureId) {
        this.signatureId = signatureId;
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

    public List<XPathFilterParameterSpec> getXpathsToIdAttributes() {
        return xpathsToIdAttributes;
    }

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

    public void setParentXpath(XPathFilterParameterSpec parentXpath) {
        this.parentXpath = parentXpath;
    }

}
