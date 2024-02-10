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

  public fun name(name: String) {
    this.name = name
    it.url("$name")
  }

  public fun baseUri(baseUri: String) {
    it.property("baseUri", baseUri)
  }

  public fun clearHeaders(clearHeaders: String) {
    it.property("clearHeaders", clearHeaders)
  }

  public fun clearHeaders(clearHeaders: Boolean) {
    it.property("clearHeaders", clearHeaders.toString())
  }

  public fun cryptoContextProperties(cryptoContextProperties: String) {
    it.property("cryptoContextProperties", cryptoContextProperties)
  }

  public fun disallowDoctypeDecl(disallowDoctypeDecl: String) {
    it.property("disallowDoctypeDecl", disallowDoctypeDecl)
  }

  public fun disallowDoctypeDecl(disallowDoctypeDecl: Boolean) {
    it.property("disallowDoctypeDecl", disallowDoctypeDecl.toString())
  }

  public fun keySelector(keySelector: String) {
    it.property("keySelector", keySelector)
  }

  public fun omitXmlDeclaration(omitXmlDeclaration: String) {
    it.property("omitXmlDeclaration", omitXmlDeclaration)
  }

  public fun omitXmlDeclaration(omitXmlDeclaration: Boolean) {
    it.property("omitXmlDeclaration", omitXmlDeclaration.toString())
  }

  public fun outputNodeSearch(outputNodeSearch: String) {
    it.property("outputNodeSearch", outputNodeSearch)
  }

  public fun outputNodeSearchType(outputNodeSearchType: String) {
    it.property("outputNodeSearchType", outputNodeSearchType)
  }

  public fun outputXmlEncoding(outputXmlEncoding: String) {
    it.property("outputXmlEncoding", outputXmlEncoding)
  }

  public fun removeSignatureElements(removeSignatureElements: String) {
    it.property("removeSignatureElements", removeSignatureElements)
  }

  public fun removeSignatureElements(removeSignatureElements: Boolean) {
    it.property("removeSignatureElements", removeSignatureElements.toString())
  }

  public fun schemaResourceUri(schemaResourceUri: String) {
    it.property("schemaResourceUri", schemaResourceUri)
  }

  public fun secureValidation(secureValidation: String) {
    it.property("secureValidation", secureValidation)
  }

  public fun secureValidation(secureValidation: Boolean) {
    it.property("secureValidation", secureValidation.toString())
  }

  public fun validationFailedHandler(validationFailedHandler: String) {
    it.property("validationFailedHandler", validationFailedHandler)
  }

  public fun xmlSignature2Message(xmlSignature2Message: String) {
    it.property("xmlSignature2Message", xmlSignature2Message)
  }

  public fun xmlSignatureChecker(xmlSignatureChecker: String) {
    it.property("xmlSignatureChecker", xmlSignatureChecker)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun uriDereferencer(uriDereferencer: String) {
    it.property("uriDereferencer", uriDereferencer)
  }
}
