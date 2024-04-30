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
 * Verify XML payloads using the XML signature specification.
 */
public fun UriDsl.`xmlsecurity-verify`(i: XmlsecurityVerifyUriDsl.() -> Unit) {
  XmlsecurityVerifyUriDsl(this).apply(i)
}

@CamelDslMarker
public class XmlsecurityVerifyUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("xmlsecurity-verify")
  }

  private var name: String = ""

  /**
   * The name part in the URI can be chosen by the user to distinguish between different verify
   * endpoints within the camel context.
   */
  public fun name(name: String) {
    this.name = name
    it.url("$name")
  }

  /**
   * You can set a base URI which is used in the URI dereferencing. Relative URIs are then
   * concatenated with the base URI.
   */
  public fun baseUri(baseUri: String) {
    it.property("baseUri", baseUri)
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
   * Provides the key for validating the XML signature.
   */
  public fun keySelector(keySelector: String) {
    it.property("keySelector", keySelector)
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
   * Sets the output node search value for determining the node from the XML signature document
   * which shall be set to the output message body. The class of the value depends on the type of the
   * output node search. The output node search is forwarded to XmlSignature2Message.
   */
  public fun outputNodeSearch(outputNodeSearch: String) {
    it.property("outputNodeSearch", outputNodeSearch)
  }

  /**
   * Determines the search type for determining the output node which is serialized into the output
   * message bodyF. See setOutputNodeSearch(Object). The supported default search types you can find in
   * DefaultXmlSignature2Message.
   */
  public fun outputNodeSearchType(outputNodeSearchType: String) {
    it.property("outputNodeSearchType", outputNodeSearchType)
  }

  /**
   * The character encoding of the resulting signed XML document. If null then the encoding of the
   * original XML document is used.
   */
  public fun outputXmlEncoding(outputXmlEncoding: String) {
    it.property("outputXmlEncoding", outputXmlEncoding)
  }

  /**
   * Indicator whether the XML signature elements (elements with local name Signature and namesapce
   * http://www.w3.org/2000/09/xmldsig#) shall be removed from the document set to the output message.
   * Normally, this is only necessary, if the XML signature is enveloped. The default value is
   * Boolean#FALSE. This parameter is forwarded to XmlSignature2Message. This indicator has no effect
   * if the output node search is of type DefaultXmlSignature2Message#OUTPUT_NODE_SEARCH_TYPE_DEFAULT.F
   */
  public fun removeSignatureElements(removeSignatureElements: String) {
    it.property("removeSignatureElements", removeSignatureElements)
  }

  /**
   * Indicator whether the XML signature elements (elements with local name Signature and namesapce
   * http://www.w3.org/2000/09/xmldsig#) shall be removed from the document set to the output message.
   * Normally, this is only necessary, if the XML signature is enveloped. The default value is
   * Boolean#FALSE. This parameter is forwarded to XmlSignature2Message. This indicator has no effect
   * if the output node search is of type DefaultXmlSignature2Message#OUTPUT_NODE_SEARCH_TYPE_DEFAULT.F
   */
  public fun removeSignatureElements(removeSignatureElements: Boolean) {
    it.property("removeSignatureElements", removeSignatureElements.toString())
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
   * Enables secure validation. If true then secure validation is enabled.
   */
  public fun secureValidation(secureValidation: String) {
    it.property("secureValidation", secureValidation)
  }

  /**
   * Enables secure validation. If true then secure validation is enabled.
   */
  public fun secureValidation(secureValidation: Boolean) {
    it.property("secureValidation", secureValidation.toString())
  }

  /**
   * Handles the different validation failed situations. The default implementation throws specific
   * exceptions for the different situations (All exceptions have the package name
   * org.apache.camel.component.xmlsecurity.api and are a sub-class of XmlSignatureInvalidException. If
   * the signature value validation fails, a XmlSignatureInvalidValueException is thrown. If a
   * reference validation fails, a XmlSignatureInvalidContentHashException is thrown. For more detailed
   * information, see the JavaDoc.
   */
  public fun validationFailedHandler(validationFailedHandler: String) {
    it.property("validationFailedHandler", validationFailedHandler)
  }

  /**
   * Bean which maps the XML signature to the output-message after the validation. How this mapping
   * should be done can be configured by the options outputNodeSearchType, outputNodeSearch, and
   * removeSignatureElements. The default implementation offers three possibilities which are related
   * to the three output node search types Default, ElementName, and XPath. The default implementation
   * determines a node which is then serialized and set to the body of the output message If the search
   * type is ElementName then the output node (which must be in this case an element) is determined by
   * the local name and namespace defined in the search value (see option outputNodeSearch). If the
   * search type is XPath then the output node is determined by the XPath specified in the search value
   * (in this case the output node can be of type Element, TextNode or Document). If the output node
   * search type is Default then the following rules apply: In the enveloped XML signature case (there
   * is a reference with URI= and transform http://www.w3.org/2000/09/xmldsig#enveloped-signature), the
   * incoming XML document without the Signature element is set to the output message body. In the
   * non-enveloped XML signature case, the message body is determined from a referenced Object; this is
   * explained in more detail in chapter Output Node Determination in Enveloping XML Signature Case.
   */
  public fun xmlSignature2Message(xmlSignature2Message: String) {
    it.property("xmlSignature2Message", xmlSignature2Message)
  }

  /**
   * This interface allows the application to check the XML signature before the validation is
   * executed. This step is recommended in
   * http://www.w3.org/TR/xmldsig-bestpractices/#check-what-is-signed
   */
  public fun xmlSignatureChecker(xmlSignatureChecker: String) {
    it.property("xmlSignatureChecker", xmlSignatureChecker)
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
