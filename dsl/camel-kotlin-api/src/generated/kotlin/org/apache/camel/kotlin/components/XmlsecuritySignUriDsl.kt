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
package org.apache.camel.kotlin.components

import kotlin.Boolean
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

/**
 * Sign XML payloads using the XML signature specification.
 */
public fun UriDsl.`xmlsecurity-sign`(i: XmlsecuritySignUriDsl.() -> Unit) {
  XmlsecuritySignUriDsl(this).apply(i)
}

@CamelDslMarker
public class XmlsecuritySignUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("xmlsecurity-sign")
  }

  private var name: String = ""

  /**
   * The name part in the URI can be chosen by the user to distinguish between different signer
   * endpoints within the camel context.
   */
  public fun name(name: String) {
    this.name = name
    it.url("$name")
  }

  /**
   * In order to protect the KeyInfo element from tampering you can add a reference to the signed
   * info element so that it is protected via the signature value. The default value is true. Only
   * relevant when a KeyInfo is returned by KeyAccessor. and KeyInfo#getId() is not null.
   */
  public fun addKeyInfoReference(addKeyInfoReference: String) {
    it.property("addKeyInfoReference", addKeyInfoReference)
  }

  /**
   * In order to protect the KeyInfo element from tampering you can add a reference to the signed
   * info element so that it is protected via the signature value. The default value is true. Only
   * relevant when a KeyInfo is returned by KeyAccessor. and KeyInfo#getId() is not null.
   */
  public fun addKeyInfoReference(addKeyInfoReference: Boolean) {
    it.property("addKeyInfoReference", addKeyInfoReference.toString())
  }

  /**
   * You can set a base URI which is used in the URI dereferencing. Relative URIs are then
   * concatenated with the base URI.
   */
  public fun baseUri(baseUri: String) {
    it.property("baseUri", baseUri)
  }

  /**
   * Canonicalization method used to canonicalize the SignedInfo element before the digest is
   * calculated. You can use the helper methods XmlSignatureHelper.getCanonicalizationMethod(String
   * algorithm) or getCanonicalizationMethod(String algorithm, List inclusiveNamespacePrefixes) to
   * create a canonicalization method.
   */
  public fun canonicalizationMethod(canonicalizationMethod: String) {
    it.property("canonicalizationMethod", canonicalizationMethod)
  }

  /**
   * Determines if the XML signature specific headers be cleared after signing and verification.
   * Defaults to true.
   */
  public fun clearHeaders(clearHeaders: String) {
    it.property("clearHeaders", clearHeaders)
  }

  /**
   * Determines if the XML signature specific headers be cleared after signing and verification.
   * Defaults to true.
   */
  public fun clearHeaders(clearHeaders: Boolean) {
    it.property("clearHeaders", clearHeaders.toString())
  }

  /**
   * Sets the content object Id attribute value. By default a UUID is generated. If you set the null
   * value, then a new UUID will be generated. Only used in the enveloping case.
   */
  public fun contentObjectId(contentObjectId: String) {
    it.property("contentObjectId", contentObjectId)
  }

  /**
   * Type of the content reference. The default value is null. This value can be overwritten by the
   * header XmlSignatureConstants#HEADER_CONTENT_REFERENCE_TYPE.
   */
  public fun contentReferenceType(contentReferenceType: String) {
    it.property("contentReferenceType", contentReferenceType)
  }

  /**
   * Reference URI for the content to be signed. Only used in the enveloped case. If the reference
   * URI contains an ID attribute value, then the resource schema URI ( setSchemaResourceUri(String))
   * must also be set because the schema validator will then find out which attributes are ID
   * attributes. Will be ignored in the enveloping or detached case.
   */
  public fun contentReferenceUri(contentReferenceUri: String) {
    it.property("contentReferenceUri", contentReferenceUri)
  }

  /**
   * Sets the crypto context properties. See {link XMLCryptoContext#setProperty(String, Object)}.
   * Possible properties are defined in XMLSignContext an XMLValidateContext (see Supported
   * Properties). The following properties are set by default to the value Boolean#TRUE for the XML
   * validation. If you want to switch these features off you must set the property value to
   * Boolean#FALSE. org.jcp.xml.dsig.validateManifests javax.xml.crypto.dsig.cacheReference
   */
  public fun cryptoContextProperties(cryptoContextProperties: String) {
    it.property("cryptoContextProperties", cryptoContextProperties)
  }

  /**
   * Digest algorithm URI. Optional parameter. This digest algorithm is used for calculating the
   * digest of the input message. If this digest algorithm is not specified then the digest algorithm
   * is calculated from the signature algorithm. Example: http://www.w3.org/2001/04/xmlenc#sha256
   */
  public fun digestAlgorithm(digestAlgorithm: String) {
    it.property("digestAlgorithm", digestAlgorithm)
  }

  /**
   * Disallows that the incoming XML document contains DTD DOCTYPE declaration. The default value is
   * Boolean#TRUE.
   */
  public fun disallowDoctypeDecl(disallowDoctypeDecl: String) {
    it.property("disallowDoctypeDecl", disallowDoctypeDecl)
  }

  /**
   * Disallows that the incoming XML document contains DTD DOCTYPE declaration. The default value is
   * Boolean#TRUE.
   */
  public fun disallowDoctypeDecl(disallowDoctypeDecl: Boolean) {
    it.property("disallowDoctypeDecl", disallowDoctypeDecl.toString())
  }

  /**
   * For the signing process, a private key is necessary. You specify a key accessor bean which
   * provides this private key. The key accessor bean must implement the KeyAccessor interface. The
   * package org.apache.camel.component.xmlsecurity.api contains the default implementation class
   * DefaultKeyAccessor which reads the private key from a Java keystore.
   */
  public fun keyAccessor(keyAccessor: String) {
    it.property("keyAccessor", keyAccessor)
  }

  /**
   * Indicator whether the XML declaration in the outgoing message body should be omitted. Default
   * value is false. Can be overwritten by the header
   * XmlSignatureConstants#HEADER_OMIT_XML_DECLARATION.
   */
  public fun omitXmlDeclaration(omitXmlDeclaration: String) {
    it.property("omitXmlDeclaration", omitXmlDeclaration)
  }

  /**
   * Indicator whether the XML declaration in the outgoing message body should be omitted. Default
   * value is false. Can be overwritten by the header
   * XmlSignatureConstants#HEADER_OMIT_XML_DECLARATION.
   */
  public fun omitXmlDeclaration(omitXmlDeclaration: Boolean) {
    it.property("omitXmlDeclaration", omitXmlDeclaration.toString())
  }

  /**
   * The character encoding of the resulting signed XML document. If null then the encoding of the
   * original XML document is used.
   */
  public fun outputXmlEncoding(outputXmlEncoding: String) {
    it.property("outputXmlEncoding", outputXmlEncoding)
  }

  /**
   * Local name of the parent element to which the XML signature element will be added. Only
   * relevant for enveloped XML signature. Alternatively you can also use
   * setParentXpath(XPathFilterParameterSpec). Default value is null. The value must be null for
   * enveloping and detached XML signature. This parameter or the parameter
   * setParentXpath(XPathFilterParameterSpec) for enveloped signature and the parameter
   * setXpathsToIdAttributes(List) for detached signature must not be set in the same configuration. If
   * the parameters parentXpath and parentLocalName are specified in the same configuration then an
   * exception is thrown.
   */
  public fun parentLocalName(parentLocalName: String) {
    it.property("parentLocalName", parentLocalName)
  }

  /**
   * Namespace of the parent element to which the XML signature element will be added.
   */
  public fun parentNamespace(parentNamespace: String) {
    it.property("parentNamespace", parentNamespace)
  }

  /**
   * Sets the XPath to find the parent node in the enveloped case. Either you specify the parent
   * node via this method or the local name and namespace of the parent with the methods
   * setParentLocalName(String) and setParentNamespace(String). Default value is null. The value must
   * be null for enveloping and detached XML signature. If the parameters parentXpath and
   * parentLocalName are specified in the same configuration then an exception is thrown.
   */
  public fun parentXpath(parentXpath: String) {
    it.property("parentXpath", parentXpath)
  }

  /**
   * Indicator whether the message body contains plain text. The default value is false, indicating
   * that the message body contains XML. The value can be overwritten by the header
   * XmlSignatureConstants#HEADER_MESSAGE_IS_PLAIN_TEXT.
   */
  public fun plainText(plainText: String) {
    it.property("plainText", plainText)
  }

  /**
   * Indicator whether the message body contains plain text. The default value is false, indicating
   * that the message body contains XML. The value can be overwritten by the header
   * XmlSignatureConstants#HEADER_MESSAGE_IS_PLAIN_TEXT.
   */
  public fun plainText(plainText: Boolean) {
    it.property("plainText", plainText.toString())
  }

  /**
   * Encoding of the plain text. Only relevant if the message body is plain text (see parameter
   * plainText. Default value is UTF-8.
   */
  public fun plainTextEncoding(plainTextEncoding: String) {
    it.property("plainTextEncoding", plainTextEncoding)
  }

  /**
   * Namespace prefix for the XML signature namespace http://www.w3.org/2000/09/xmldsig#. Default
   * value is ds. If null or an empty value is set then no prefix is used for the XML signature
   * namespace. See best practice http://www.w3.org/TR/xmldsig-bestpractices/#signing-xml-
   * without-namespaces
   */
  public fun prefixForXmlSignatureNamespace(prefixForXmlSignatureNamespace: String) {
    it.property("prefixForXmlSignatureNamespace", prefixForXmlSignatureNamespace)
  }

  /**
   * For adding additional References and Objects to the XML signature which contain additional
   * properties, you can provide a bean which implements the XmlSignatureProperties interface.
   */
  public fun properties(properties: String) {
    it.property("properties", properties)
  }

  /**
   * Classpath to the XML Schema. Must be specified in the detached XML Signature case for
   * determining the ID attributes, might be set in the enveloped and enveloping case. If set, then the
   * XML document is validated with the specified XML schema. The schema resource URI can be
   * overwritten by the header XmlSignatureConstants#HEADER_SCHEMA_RESOURCE_URI.
   */
  public fun schemaResourceUri(schemaResourceUri: String) {
    it.property("schemaResourceUri", schemaResourceUri)
  }

  /**
   * Signature algorithm. Default value is http://www.w3.org/2000/09/xmldsig#rsa-sha1.
   */
  public fun signatureAlgorithm(signatureAlgorithm: String) {
    it.property("signatureAlgorithm", signatureAlgorithm)
  }

  /**
   * Sets the signature Id. If this parameter is not set (null value) then a unique ID is generated
   * for the signature ID (default). If this parameter is set to (empty string) then no Id attribute is
   * created in the signature element.
   */
  public fun signatureId(signatureId: String) {
    it.property("signatureId", signatureId)
  }

  /**
   * Transforms which are executed on the message body before the digest is calculated. By default,
   * C14n is added and in the case of enveloped signature (see option parentLocalName) also
   * http://www.w3.org/2000/09/xmldsig#enveloped-signature is added at position 0 of the list. Use
   * methods in XmlSignatureHelper to create the transform methods.
   */
  public fun transformMethods(transformMethods: String) {
    it.property("transformMethods", transformMethods)
  }

  /**
   * Define the elements which are signed in the detached case via XPATH expressions to ID
   * attributes (attributes of type ID). For each element found via the XPATH expression a detached
   * signature is created whose reference URI contains the corresponding attribute value (preceded by
   * '#'). The signature becomes the last sibling of the signed element. Elements with deeper hierarchy
   * level are signed first. You can also set the XPATH list dynamically via the header
   * XmlSignatureConstants#HEADER_XPATHS_TO_ID_ATTRIBUTES. The parameter setParentLocalName(String) or
   * setParentXpath(XPathFilterParameterSpec) for enveloped signature and this parameter for detached
   * signature must not be set in the same configuration.
   */
  public fun xpathsToIdAttributes(xpathsToIdAttributes: String) {
    it.property("xpathsToIdAttributes", xpathsToIdAttributes)
  }

  /**
   * Whether the producer should be started lazy (on the first message). By starting lazy you can
   * use this to allow CamelContext and routes to startup in situations where a producer may otherwise
   * fail during starting and cause the route to fail being started. By deferring this startup to be
   * lazy then the startup failure can be handled during routing messages via Camel's routing error
   * handlers. Beware that when the first message is processed then creating and starting the producer
   * may take a little time and prolong the total processing time of the processing.
   */
  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  /**
   * Whether the producer should be started lazy (on the first message). By starting lazy you can
   * use this to allow CamelContext and routes to startup in situations where a producer may otherwise
   * fail during starting and cause the route to fail being started. By deferring this startup to be
   * lazy then the startup failure can be handled during routing messages via Camel's routing error
   * handlers. Beware that when the first message is processed then creating and starting the producer
   * may take a little time and prolong the total processing time of the processing.
   */
  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  /**
   * If you want to restrict the remote access via reference URIs, you can set an own dereferencer.
   * Optional parameter. If not set the provider default dereferencer is used which can resolve URI
   * fragments, HTTP, file and XPpointer URIs. Attention: The implementation is provider dependent!
   */
  public fun uriDereferencer(uriDereferencer: String) {
    it.property("uriDereferencer", uriDereferencer)
  }
}
