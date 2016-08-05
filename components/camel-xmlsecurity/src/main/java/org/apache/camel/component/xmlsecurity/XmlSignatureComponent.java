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
package org.apache.camel.component.xmlsecurity;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.URIDereferencer;
import javax.xml.crypto.dsig.spec.XPathFilterParameterSpec;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.xmlsecurity.api.KeyAccessor;
import org.apache.camel.component.xmlsecurity.api.ValidationFailedHandler;
import org.apache.camel.component.xmlsecurity.api.XmlSignature2Message;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureChecker;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureProperties;
import org.apache.camel.component.xmlsecurity.processor.XmlSignerConfiguration;
import org.apache.camel.component.xmlsecurity.processor.XmlVerifierConfiguration;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.ObjectHelper;

public class XmlSignatureComponent extends UriEndpointComponent {

    private XmlSignerConfiguration signerConfiguration;
    private XmlVerifierConfiguration verifierConfiguration;

    public XmlSignatureComponent() {
        super(XmlSignatureEndpoint.class);
    }

    public XmlSignatureComponent(CamelContext context) {
        super(context, XmlSignatureEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining,
                                      Map<String, Object> parameters) throws Exception {
        ObjectHelper.notNull(getCamelContext(), "CamelContext");

        String scheme;
        String name;
        try {
            URI u = new URI(remaining);
            scheme = u.getScheme();
            name = u.getPath();
        } catch (Exception e) {
            throw new MalformedURLException(
                    String.format(
                            "An invalid xmlsecurity uri was provided '%s'."
                                    + " Check the uri matches the format xmlsecurity:sign://<name> or xmlsecurity:verify:<name>",
                            uri
                    )
            );
        }
        XmlSignatureEndpoint endpoint;
        if ("sign".equals(scheme)) {
            XmlSignerConfiguration config = getSignerConfiguration().copy();
            endpoint = new XmlSignerEndpoint(uri, this, config);
        } else if ("verify".equals(scheme)) {
            XmlVerifierConfiguration config = getVerifierConfiguration().copy();
            endpoint = new XmlVerifierEndpoint(uri, this, config);
        } else {
            throw new IllegalStateException(
                    String.format(
                            "Endpoint uri '%s'" + " is wrong configured. Operation '%s'"
                                    + " is not supported. Supported operations are: sign, verify",
                            uri, scheme
                    )
            );
        }
        setProperties(endpoint.getConfiguration(), parameters);
        endpoint.getConfiguration().setCamelContext(getCamelContext());
        endpoint.setCommand(XmlCommand.valueOf(scheme));
        endpoint.setName(name);
        return endpoint;
    }

    public XmlSignerConfiguration getSignerConfiguration() {
        if (signerConfiguration == null) {
            signerConfiguration = new XmlSignerConfiguration();
        }
        return signerConfiguration;
    }

    /**
     * To use a shared XmlSignerConfiguration configuration to use as base for configuring endpoints. Properties of the shared configuration can also be set individually.
     */
    public void setSignerConfiguration(XmlSignerConfiguration signerConfiguration) {
        this.signerConfiguration = signerConfiguration;
    }

    public XmlVerifierConfiguration getVerifierConfiguration() {
        if (verifierConfiguration == null) {
            verifierConfiguration = new XmlVerifierConfiguration();
        }
        return verifierConfiguration;
    }

    /**
     * To use a shared XmlVerifierConfiguration configuration to use as base for configuring endpoints. Properties of the shared configuration can also be set individually.
     */
    public void setVerifierConfiguration(XmlVerifierConfiguration verifierConfiguration) {
        this.verifierConfiguration = verifierConfiguration;
    }

    public URIDereferencer getSignerUriDereferencer() {
        return getSignerConfiguration().getUriDereferencer();
    }

    /**
     * If you want to restrict the remote access via reference URIs, you can setSigner
     * an own dereferencer. Optional parameter. If not setSigner the provider default
     * dereferencer is used which can resolve URI fragments, HTTP, file and
     * XPpointer URIs.
     * <p>
     * Attention: The implementation is provider dependent!
     *
     * @see XMLCryptoContext#setURIDereferencer(URIDereferencer)
     * @param uriDereferencer
     */
    public void setSignerUriDereferencer(URIDereferencer uriDereferencer) {
        getSignerConfiguration().setUriDereferencer(uriDereferencer);
    }

    public String getSignerBaseUri() {
        return getSignerConfiguration().getBaseUri();
    }

    /**
     * You can setSigner a base URI which is used in the URI dereferencing. Relative
     * URIs are then concatenated with the base URI.
     *
     * @see XMLCryptoContext#setBaseURI(String)
     * @param baseUri
     */
    public void setSignerBaseUri(String baseUri) {
        getSignerConfiguration().setBaseUri(baseUri);
    }

    public Boolean getSignerDisallowDoctypeDecl() {
        return getSignerConfiguration().getDisallowDoctypeDecl();
    }

    public KeyAccessor getSignerKeyAccessor() {
        return getSignerConfiguration().getKeyAccessor();
    }

    /**
     * Disallows that the incoming XML document contains DTD DOCTYPE
     * declaration. The default value is {@link Boolean#TRUE}.
     *
     * @param disallowDoctypeDecl if setSigner to {@link Boolean#FALSE} then DOCTYPE declaration is allowed, otherwise not
     */
    public void setSignerDisallowDoctypeDecl(Boolean disallowDoctypeDecl) {
        getSignerConfiguration().setDisallowDoctypeDecl(disallowDoctypeDecl);
    }

    public Boolean getSignerOmitXmlDeclaration() {
        return getSignerConfiguration().getOmitXmlDeclaration();
    }

    /**
     * For the signing process, a private key is necessary. You specify a key accessor bean which provides this private key.
     * The key accessor bean must implement the KeyAccessor interface. The package org.apache.camel.component.xmlsecurity.api
     * contains the default implementation class DefaultKeyAccessor which reads the private key from a Java keystore.
     * @param keyAccessor
     */
    public void setSignerKeyAccessor(KeyAccessor keyAccessor) {
        getSignerConfiguration().setKeyAccessor(keyAccessor);
    }

    /**
     * setSigners the reference name for a KeyAccessor that can be found in the registry.
     * @param keyAccessorName
     */
    public void setSignerKeyAccessor(String keyAccessorName) {
        getSignerConfiguration().setKeyAccessor(keyAccessorName);
    }

    /**
     * Indicator whether the XML declaration in the outgoing message body should
     * be omitted. Default value is <code>false</code>. Can be overwritten by
     * the header {@link XmlSignatureConstants#HEADER_OMIT_XML_DECLARATION}.
     * @param omitXmlDeclaration
     */
    public void setSignerOmitXmlDeclaration(Boolean omitXmlDeclaration) {
        getSignerConfiguration().setOmitXmlDeclaration(omitXmlDeclaration);
    }

    /**
     * Determines if the XML signature specific headers be cleared after signing
     * and verification. Defaults to true.
     *
     * @return true if the Signature headers should be unset, false otherwise
     */
    public Boolean getSignerClearHeaders() {
        return getSignerConfiguration().getClearHeaders();
    }

    public AlgorithmMethod getSignerCanonicalizationMethod() {
        return getSignerConfiguration().getCanonicalizationMethod();
    }

    /**
     * Determines if the XML signature specific headers be cleared after signing
     * and verification. Defaults to true.
     * @param clearHeaders
     */
    public void setSignerClearHeaders(Boolean clearHeaders) {
        getSignerConfiguration().setClearHeaders(clearHeaders);
    }

    public String getSignerSchemaResourceUri() {
        return getSignerConfiguration().getSchemaResourceUri();
    }

    /**
     * Canonicalization method used to canonicalize the SignedInfo element before the digest is calculated.
     * You can use the helper methods XmlSignatureHelper.getCanonicalizationMethod(String algorithm)
     * or getSignerCanonicalizationMethod(String algorithm, List<String> inclusiveNamespacePrefixes) to create a canonicalization method.
     * @param canonicalizationMethod
     */
    public void setSignerCanonicalizationMethod(AlgorithmMethod canonicalizationMethod) {
        getSignerConfiguration().setCanonicalizationMethod(canonicalizationMethod);
    }

    /**
     * setSigners the reference name for a AlgorithmMethod that can be found in the registry.
     * @param canonicalizationMethodName
     */
    public void setSignerCanonicalizationMethod(String canonicalizationMethodName) {
        getSignerConfiguration().setCanonicalizationMethod(canonicalizationMethodName);
    }

    /**
     * Classpath to the XML Schema. Must be specified in the detached XML
     * Signature case for determining the ID attributes, might be setSigner in the
     * enveloped and enveloping case. If setSigner, then the XML document is validated
     * with the specified XML schema. The schema resource URI can be overwritten
     * by the header {@link XmlSignatureConstants#HEADER_SCHEMA_RESOURCE_URI}.
     * @param schemaResourceUri
     */
    public void setSignerSchemaResourceUri(String schemaResourceUri) {
        getSignerConfiguration().setSchemaResourceUri(schemaResourceUri);
    }

    public String getSignerOutputXmlEncoding() {
        return getSignerConfiguration().getOutputXmlEncoding();
    }

    /**
     * The character encoding of the resulting signed XML document. If
     * <code>null</code> then the encoding of the original XML document is used.
     * @param outputXmlEncoding
     */
    public void setSignerOutputXmlEncoding(String outputXmlEncoding) {
        getSignerConfiguration().setOutputXmlEncoding(outputXmlEncoding);
    }

    public List<AlgorithmMethod> getSignerTransformMethods() {
        return getSignerConfiguration().getTransformMethods();
    }

    /**
     * Transforms which are executed on the message body before the digest is calculated.
     * By default, C14n is added and in the case of enveloped signature (see option parentLocalName) also http://www.w3.org/2000/09/xmldsig#enveloped-signature
     * is added at position 0 of the list. Use methods in XmlSignatureHelper to create the transform methods.
     * @param transformMethods
     */
    public void setSignerTransformMethods(List<AlgorithmMethod> transformMethods) {
        getSignerConfiguration().setTransformMethods(transformMethods);
    }

    /**
     * setSigners the reference name for a List<AlgorithmMethod> that can be found in the registry.
     * @param transformMethodsName
     */
    public void setSignerTransformMethods(String transformMethodsName) {
        getSignerConfiguration().setTransformMethods(transformMethodsName);
    }

    public String getSignerSignatureAlgorithm() {
        return getSignerConfiguration().getSignatureAlgorithm();
    }

    /**
     * Signature algorithm. Default value is
     * "http://www.w3.org/2000/09/xmldsig#rsa-sha1".
     * @param signatureAlgorithm
     */
    public void setSignerSignatureAlgorithm(String signatureAlgorithm) {
        getSignerConfiguration().setSignatureAlgorithm(signatureAlgorithm);
    }

    public String getSignerDigestAlgorithm() {
        return getSignerConfiguration().getDigestAlgorithm();
    }

    /**
     * Digest algorithm URI. Optional parameter. This digest algorithm is used
     * for calculating the digest of the input message. If this digest algorithm
     * is not specified then the digest algorithm is calculated from the
     * signature algorithm. Example: "http://www.w3.org/2001/04/xmlenc#sha256"
     * @param digestAlgorithm
     */
    public void setSignerDigestAlgorithm(String digestAlgorithm) {
        getSignerConfiguration().setDigestAlgorithm(digestAlgorithm);
    }

    public Boolean getSignerAddKeyInfoReference() {
        return getSignerConfiguration().getAddKeyInfoReference();
    }

    /**
     * In order to protect the KeyInfo element from tampering you can add a
     * reference to the signed info element so that it is protected via the
     * signature value. The default value is <tt>true</tt>.
     * <p>
     * Only relevant when a KeyInfo is returned by {@link KeyAccessor}. and
     * {@link KeyInfo#getId()} is not <code>null</code>.
     * @param addKeyInfoReference
     */
    public void setSignerAddKeyInfoReference(Boolean addKeyInfoReference) {
        getSignerConfiguration().setAddKeyInfoReference(addKeyInfoReference);
    }

    public String getSignerPrefixForXmlSignatureNamespace() {
        return getSignerConfiguration().getPrefixForXmlSignatureNamespace();
    }

    /**
     * Namespace prefix for the XML signature namespace
     * "http://www.w3.org/2000/09/xmldsig#". Default value is "ds".
     *
     * If <code>null</code> or an empty value is setSigner then no prefix is used for
     * the XML signature namespace.
     * <p>
     * See best practice
     * http://www.w3.org/TR/xmldsig-bestpractices/#signing-xml-
     * without-namespaces
     *
     * @param prefixForXmlSignatureNamespace
     *            prefix
     */
    public void setSignerPrefixForXmlSignatureNamespace(String prefixForXmlSignatureNamespace) {
        getSignerConfiguration().setPrefixForXmlSignatureNamespace(prefixForXmlSignatureNamespace);
    }

    public String getSignerParentLocalName() {
        return getSignerConfiguration().getParentLocalName();
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
     * for detached signature must not be setSigner in the same configuration.
     * <p>
     * If the parameters <tt>parentXpath</tt> and <tt>parentLocalName</tt> are specified
     * in the same configuration then an exception is thrown.
     *
     * @param parentLocalName
     *            local name
     */
    public void setSignerParentLocalName(String parentLocalName) {
        getSignerConfiguration().setParentLocalName(parentLocalName);
    }

    public String getSignerParentNamespace() {
        return getSignerConfiguration().getParentNamespace();
    }

    /**
     * Namespace of the parent element to which the XML signature element will
     * be added.
     * @param parentNamespace
     */
    public void setSignerParentNamespace(String parentNamespace) {
        getSignerConfiguration().setParentNamespace(parentNamespace);
    }

    public String getSignerContentObjectId() {
        return getSignerConfiguration().getContentObjectId();
    }

    /**
     * setSigners the content object Id attribute value. By default a UUID is
     * generated. If you setSigner the <code>null</code> value, then a new UUID will
     * be generated. Only used in the enveloping case.
     * @param contentObjectId
     */
    public void setSignerContentObjectId(String contentObjectId) {
        getSignerConfiguration().setContentObjectId(contentObjectId);
    }

    public String getSignerSignatureId() {
        return getSignerConfiguration().getSignatureId();
    }

    /**
     * setSigners the signature Id. If this parameter is not setSigner (null value) then a
     * unique ID is generated for the signature ID (default). If this parameter
     * is setSigner to "" (empty string) then no Id attribute is created in the
     * signature element.
     * @param signatureId
     */
    public void setSignerSignatureId(String signatureId) {
        getSignerConfiguration().setSignatureId(signatureId);
    }

    public String getSignerContentReferenceUri() {
        return getSignerConfiguration().getContentReferenceUri();
    }

    /**
     * Reference URI for the content to be signed. Only used in the enveloped
     * case. If the reference URI contains an ID attribute value, then the
     * resource schema URI ( {@link #setSchemaResourceUri(String)}) must also be
     * setSigner because the schema validator will then find out which attributes are
     * ID attributes. Will be ignored in the enveloping or detached case.
     * @param referenceUri
     */
    public void setSignerContentReferenceUri(String referenceUri) {
        getSignerConfiguration().setContentReferenceUri(referenceUri);
    }

    public String getSignerContentReferenceType() {
        return getSignerConfiguration().getContentReferenceType();
    }

    /**
     * Type of the content reference. The default value is <code>null</code>.
     * This value can be overwritten by the header
     * {@link XmlSignatureConstants#HEADER_CONTENT_REFERENCE_TYPE}.
     * @param referenceType
     */
    public void setSignerContentReferenceType(String referenceType) {
        getSignerConfiguration().setContentReferenceType(referenceType);
    }

    public Boolean getSignerPlainText() {
        return getSignerConfiguration().getPlainText();
    }

    /**
     * Indicator whether the message body contains plain text. The default value
     * is <code>false</code>, indicating that the message body contains XML. The
     * value can be overwritten by the header
     * {@link XmlSignatureConstants#HEADER_MESSAGE_IS_PLAIN_TEXT}.
     * @param plainText
     */
    public void setSignerPlainText(Boolean plainText) {
        getSignerConfiguration().setPlainText(plainText);
    }

    public String getSignerPlainTextEncoding() {
        return getSignerConfiguration().getPlainTextEncoding();
    }

    /**
     * Encoding of the plain text. Only relevant if the message body is plain
     * text (see parameter {@link #plainText}. Default value is "UTF-8".
     * @param plainTextEncoding
     */
    public void setSignerPlainTextEncoding(String plainTextEncoding) {
        getSignerConfiguration().setPlainTextEncoding(plainTextEncoding);
    }

    public XmlSignatureProperties getSignerProperties() {
        return getSignerConfiguration().getProperties();
    }

    /**
     * For adding additional References and Objects to the XML signature which contain additional properties,
     * you can provide a bean which implements the XmlSignatureProperties interface.
     * @param properties
     */
    public void setSignerProperties(XmlSignatureProperties properties) {
        getSignerConfiguration().setProperties(properties);
    }

    /**
     * setSigners the reference name for a XmlSignatureProperties that can be found in the registry.
     * @param propertiesName
     */
    public void setSignerProperties(String propertiesName) {
        getSignerConfiguration().setProperties(propertiesName);
    }

    public List<XPathFilterParameterSpec> getSignerXpathsToIdAttributes() {
        return getSignerConfiguration().getXpathsToIdAttributes();
    }

    /**
     * Define the elements which are signed in the detached case via XPATH
     * expressions to ID attributes (attributes of type ID). For each element
     * found via the XPATH expression a detached signature is created whose
     * reference URI contains the corresponding attribute value (preceded by
     * '#'). The signature becomes the last sibling of the signed element.
     * Elements with deeper hierarchy level are signed first.
     * <p>
     * You can also setSigner the XPATH list dynamically via the header
     * {@link XmlSignatureConstants#HEADER_XPATHS_TO_ID_ATTRIBUTES}.
     * <p>
     * The parameter {@link #setParentLocalName(String)} or {@link #setParentXpath(XPathFilterParameterSpec)}
     * for enveloped signature and this parameter for detached signature must not
     * be setSigner in the same configuration.
     * @param xpathsToIdAttributes
     */
    public void setSignerXpathsToIdAttributes(List<XPathFilterParameterSpec> xpathsToIdAttributes) {
        getSignerConfiguration().setXpathsToIdAttributes(xpathsToIdAttributes);
    }

    public XPathFilterParameterSpec getSignerParentXpath() {
        return getSignerConfiguration().getParentXpath();
    }

    /**
     * setSigners the XPath to find the parent node in the enveloped case.
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
    public void setSignerParentXpath(XPathFilterParameterSpec parentXpath) {
        getSignerConfiguration().setParentXpath(parentXpath);
    }

    public URIDereferencer getVerifierUriDereferencer() {
        return getVerifierConfiguration().getUriDereferencer();
    }

    /**
     * If you want to restrict the remote access via reference URIs, you can setVerifier
     * an own dereferencer. Optional parameter. If not setVerifier the provider default
     * dereferencer is used which can resolve URI fragments, HTTP, file and
     * XPpointer URIs.
     * <p>
     * Attention: The implementation is provider dependent!
     *
     * @see XMLCryptoContext#setURIDereferencer(URIDereferencer)
     * @param uriDereferencer
     */
    public void setVerifierUriDereferencer(URIDereferencer uriDereferencer) {
        getVerifierConfiguration().setUriDereferencer(uriDereferencer);
    }

    public String getVerifierBaseUri() {
        return getVerifierConfiguration().getBaseUri();
    }

    /**
     * You can setVerifier a base URI which is used in the URI dereferencing. Relative
     * URIs are then concatenated with the base URI.
     *
     * @see XMLCryptoContext#setBaseURI(String)
     * @param baseUri
     */
    public void setVerifierBaseUri(String baseUri) {
        getVerifierConfiguration().setBaseUri(baseUri);
    }

    /**
     * Provides the key for validating the XML signature.
     * @param keySelector
     */
    public void setVerifierKeySelector(KeySelector keySelector) {
        getVerifierConfiguration().setKeySelector(keySelector);
    }

    public KeySelector getVerifierKeySelector() {
        return getVerifierConfiguration().getKeySelector();
    }

    /**
     * setVerifiers the reference name for a KeySelector that can be found in the registry.
     * @param keySelectorName
     */
    public void setVerifierKeySelector(String keySelectorName) {
        getVerifierConfiguration().setKeySelector(keySelectorName);
    }

    public XmlSignatureChecker getVerifierXmlSignatureChecker() {
        return getVerifierConfiguration().getXmlSignatureChecker();
    }

    public Boolean getVerifierDisallowDoctypeDecl() {
        return getVerifierConfiguration().getDisallowDoctypeDecl();
    }

    /**
     * This interface allows the application to check the XML signature before the validation is executed.
     * This step is recommended in http://www.w3.org/TR/xmldsig-bestpractices/#check-what-is-signed
     * @param xmlSignatureChecker
     */
    public void setVerifierXmlSignatureChecker(XmlSignatureChecker xmlSignatureChecker) {
        getVerifierConfiguration().setXmlSignatureChecker(xmlSignatureChecker);
    }

    /**
     * Disallows that the incoming XML document contains DTD DOCTYPE
     * declaration. The default value is {@link Boolean#TRUE}.
     *
     * @param disallowDoctypeDecl if setVerifier to {@link Boolean#FALSE} then DOCTYPE declaration is allowed, otherwise not
     */
    public void setVerifierDisallowDoctypeDecl(Boolean disallowDoctypeDecl) {
        getVerifierConfiguration().setDisallowDoctypeDecl(disallowDoctypeDecl);
    }

    /**
     * setVerifiers the reference name for a application checker that can be found in the registry.
     * @param xmlSignatureCheckerName
     */
    public void setVerifierXmlSignatureChecker(String xmlSignatureCheckerName) {
        getVerifierConfiguration().setXmlSignatureChecker(xmlSignatureCheckerName);
    }

    public Boolean getVerifierOmitXmlDeclaration() {
        return getVerifierConfiguration().getOmitXmlDeclaration();
    }

    /**
     * Indicator whether the XML declaration in the outgoing message body should
     * be omitted. Default value is <code>false</code>. Can be overwritten by
     * the header {@link XmlSignatureConstants#HEADER_OMIT_XML_DECLARATION}.
     * @param omitXmlDeclaration
     */
    public void setVerifierOmitXmlDeclaration(Boolean omitXmlDeclaration) {
        getVerifierConfiguration().setOmitXmlDeclaration(omitXmlDeclaration);
    }

    public XmlSignature2Message getVerifierXmlSignature2Message() {
        return getVerifierConfiguration().getXmlSignature2Message();
    }

    /**
     * Determines if the XML signature specific headers be cleared after signing
     * and verification. Defaults to true.
     *
     * @return true if the Signature headers should be unset, false otherwise
     */
    public Boolean getVerifierClearHeaders() {
        return getVerifierConfiguration().getClearHeaders();
    }

    /**
     * Determines if the XML signature specific headers be cleared after signing
     * and verification. Defaults to true.
     * @param clearHeaders
     */
    public void setVerifierClearHeaders(Boolean clearHeaders) {
        getVerifierConfiguration().setClearHeaders(clearHeaders);
    }

    public String getVerifierSchemaResourceUri() {
        return getVerifierConfiguration().getSchemaResourceUri();
    }

    /**
     * Classpath to the XML Schema. Must be specified in the detached XML
     * Signature case for determining the ID attributes, might be setVerifier in the
     * enveloped and enveloping case. If setVerifier, then the XML document is validated
     * with the specified XML schema. The schema resource URI can be overwritten
     * by the header {@link XmlSignatureConstants#HEADER_SCHEMA_RESOURCE_URI}.
     * @param schemaResourceUri
     */
    public void setVerifierSchemaResourceUri(String schemaResourceUri) {
        getVerifierConfiguration().setSchemaResourceUri(schemaResourceUri);
    }

    public String getVerifierOutputXmlEncoding() {
        return getVerifierConfiguration().getOutputXmlEncoding();
    }

    /**
     * The character encoding of the resulting signed XML document. If
     * <code>null</code> then the encoding of the original XML document is used.
     * @param outputXmlEncoding
     */
    public void setVerifierOutputXmlEncoding(String outputXmlEncoding) {
        getVerifierConfiguration().setOutputXmlEncoding(outputXmlEncoding);
    }

    /**
     * Bean which maps the XML signature to the output-message after the validation.
     * How this mapping should be done can be configured by the options outputNodeSearchType, outputNodeSearch, and removeSignatureElements.
     * The default implementation offers three possibilities which are related to the three output node search types "Default", "ElementName", and "XPath".
     * The default implementation determines a node which is then serialized and setVerifier to the body of the output message
     * If the search type is "ElementName" then the output node (which must be in this case an element) is determined
     * by the local name and namespace defined in the search value (see option outputNodeSearch).
     * If the search type is "XPath" then the output node is determined by the XPath specified in the search value
     * (in this case the output node can be of type "Element", "TextNode" or "Document").
     * If the output node search type is "Default" then the following rules apply:
     * In the enveloped XML signature case (there is a reference with URI="" and transform "http://www.w3.org/2000/09/xmldsig#enveloped-signature"),
     * the incoming XML document without the Signature element is setVerifier to the output message body.
     * In the non-enveloped XML signature case, the message body is determined from a referenced Object;
     * this is explained in more detail in chapter "Output Node Determination in Enveloping XML Signature Case".
     * @param xmlSignature2Message
     */
    public void setVerifierXmlSignature2Message(XmlSignature2Message xmlSignature2Message) {
        getVerifierConfiguration().setXmlSignature2Message(xmlSignature2Message);
    }

    /**
     * setVerifiers the reference name for the to-message instance that can be found in
     * the registry.
     * @param xmlSignature2Message
     */
    public void setVerifierXmlSignature2Message(String xmlSignature2Message) {
        getVerifierConfiguration().setXmlSignature2Message(xmlSignature2Message);
    }

    public ValidationFailedHandler getVerifierValidationFailedHandler() {
        return getVerifierConfiguration().getValidationFailedHandler();
    }

    /**
     * Handles the different validation failed situations.
     * The default implementation throws specific exceptions for the different situations
     * (All exceptions have the package name org.apache.camel.component.xmlsecurity.api and are a sub-class of XmlSignatureInvalidException.
     * If the signature value validation fails, a XmlSignatureInvalidValueException is thrown.
     * If a reference validation fails, a XmlSignatureInvalidContentHashException is thrown. For more detailed information, see the JavaDoc.
     * @param validationFailedHandler
     */
    public void setVerifierValidationFailedHandler(ValidationFailedHandler validationFailedHandler) {
        getVerifierConfiguration().setValidationFailedHandler(validationFailedHandler);
    }

    public void setVerifierValidationFailedHandler(String validationFailedHandlerName) {
        getVerifierConfiguration().setValidationFailedHandler(validationFailedHandlerName);
    }

    public Object getVerifierOutputNodeSearch() {
        return getVerifierConfiguration().getOutputNodeSearch();
    }

    /**
     * setVerifiers the output node search value for determining the node from the XML
     * signature document which shall be setVerifier to the output message body. The
     * class of the value depends on the type of the output node search. The
     * output node search is forwarded to {@link XmlSignature2Message}.
     * @param outputNodeSearch
     */
    public void setVerifierOutputNodeSearch(Object outputNodeSearch) {
        getVerifierConfiguration().setOutputNodeSearch(outputNodeSearch);
    }

    public String getVerifierOutputNodeSearchType() {
        return getVerifierConfiguration().getOutputNodeSearchType();
    }

    /**
     * Determines the search type for determining the output node which is
     * serialized into the output message bodyF. See
     * {@link #setOutputNodeSearch(Object)}. The supported default search types
     * you can find in {@link DefaultXmlSignature2Message}.
     * @param outputNodeSearchType
     */
    public void setVerifierOutputNodeSearchType(String outputNodeSearchType) {
        getVerifierConfiguration().setOutputNodeSearchType(outputNodeSearchType);
    }

    public Boolean getVerifierRemoveSignatureElements() {
        return getVerifierConfiguration().getRemoveSignatureElements();
    }

    /**
     * Indicator whether the XML signature elements (elements with local name
     * "Signature" and namesapce ""http://www.w3.org/2000/09/xmldsig#"") shall
     * be removed from the document setVerifier to the output message. Normally, this is
     * only necessary, if the XML signature is enveloped. The default value is
     * {@link Boolean#FALSE}. This parameter is forwarded to
     * {@link XmlSignature2Message}.
     * <p>
     * This indicator has no effect if the output node search is of type
     * {@link DefaultXmlSignature2Message#OUTPUT_NODE_SEARCH_TYPE_DEFAULT}.F
     * @param removeSignatureElements
     */
    public void setVerifierRemoveSignatureElements(Boolean removeSignatureElements) {
        getVerifierConfiguration().setRemoveSignatureElements(removeSignatureElements);
    }

    public Boolean getVerifierSecureValidation() {
        return getVerifierConfiguration().getSecureValidation();
    }

    /**
     * Enables secure validation. If true then secure validation is enabled.
     * @param secureValidation
     */
    public void setVerifierSecureValidation(Boolean secureValidation) {
        getVerifierConfiguration().setSecureValidation(secureValidation);
    }

    public String getVerifierValidationFailedHandlerName() {
        return getVerifierConfiguration().getValidationFailedHandlerName();
    }

    /**
     * Name of handler to
     * @param validationFailedHandlerName
     */
    public void setVerifierValidationFailedHandlerName(String validationFailedHandlerName) {
        getVerifierConfiguration().setValidationFailedHandlerName(validationFailedHandlerName);
    }
}
