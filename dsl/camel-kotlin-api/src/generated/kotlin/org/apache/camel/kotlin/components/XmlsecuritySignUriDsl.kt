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

  public fun name(name: String) {
    this.name = name
    it.url("$name")
  }

  public fun addKeyInfoReference(addKeyInfoReference: String) {
    it.property("addKeyInfoReference", addKeyInfoReference)
  }

  public fun addKeyInfoReference(addKeyInfoReference: Boolean) {
    it.property("addKeyInfoReference", addKeyInfoReference.toString())
  }

  public fun baseUri(baseUri: String) {
    it.property("baseUri", baseUri)
  }

  public fun canonicalizationMethod(canonicalizationMethod: String) {
    it.property("canonicalizationMethod", canonicalizationMethod)
  }

  public fun clearHeaders(clearHeaders: String) {
    it.property("clearHeaders", clearHeaders)
  }

  public fun clearHeaders(clearHeaders: Boolean) {
    it.property("clearHeaders", clearHeaders.toString())
  }

  public fun contentObjectId(contentObjectId: String) {
    it.property("contentObjectId", contentObjectId)
  }

  public fun contentReferenceType(contentReferenceType: String) {
    it.property("contentReferenceType", contentReferenceType)
  }

  public fun contentReferenceUri(contentReferenceUri: String) {
    it.property("contentReferenceUri", contentReferenceUri)
  }

  public fun cryptoContextProperties(cryptoContextProperties: String) {
    it.property("cryptoContextProperties", cryptoContextProperties)
  }

  public fun digestAlgorithm(digestAlgorithm: String) {
    it.property("digestAlgorithm", digestAlgorithm)
  }

  public fun disallowDoctypeDecl(disallowDoctypeDecl: String) {
    it.property("disallowDoctypeDecl", disallowDoctypeDecl)
  }

  public fun disallowDoctypeDecl(disallowDoctypeDecl: Boolean) {
    it.property("disallowDoctypeDecl", disallowDoctypeDecl.toString())
  }

  public fun keyAccessor(keyAccessor: String) {
    it.property("keyAccessor", keyAccessor)
  }

  public fun omitXmlDeclaration(omitXmlDeclaration: String) {
    it.property("omitXmlDeclaration", omitXmlDeclaration)
  }

  public fun omitXmlDeclaration(omitXmlDeclaration: Boolean) {
    it.property("omitXmlDeclaration", omitXmlDeclaration.toString())
  }

  public fun outputXmlEncoding(outputXmlEncoding: String) {
    it.property("outputXmlEncoding", outputXmlEncoding)
  }

  public fun parentLocalName(parentLocalName: String) {
    it.property("parentLocalName", parentLocalName)
  }

  public fun parentNamespace(parentNamespace: String) {
    it.property("parentNamespace", parentNamespace)
  }

  public fun parentXpath(parentXpath: String) {
    it.property("parentXpath", parentXpath)
  }

  public fun plainText(plainText: String) {
    it.property("plainText", plainText)
  }

  public fun plainText(plainText: Boolean) {
    it.property("plainText", plainText.toString())
  }

  public fun plainTextEncoding(plainTextEncoding: String) {
    it.property("plainTextEncoding", plainTextEncoding)
  }

  public fun prefixForXmlSignatureNamespace(prefixForXmlSignatureNamespace: String) {
    it.property("prefixForXmlSignatureNamespace", prefixForXmlSignatureNamespace)
  }

  public fun properties(properties: String) {
    it.property("properties", properties)
  }

  public fun schemaResourceUri(schemaResourceUri: String) {
    it.property("schemaResourceUri", schemaResourceUri)
  }

  public fun signatureAlgorithm(signatureAlgorithm: String) {
    it.property("signatureAlgorithm", signatureAlgorithm)
  }

  public fun signatureId(signatureId: String) {
    it.property("signatureId", signatureId)
  }

  public fun transformMethods(transformMethods: String) {
    it.property("transformMethods", transformMethods)
  }

  public fun xpathsToIdAttributes(xpathsToIdAttributes: String) {
    it.property("xpathsToIdAttributes", xpathsToIdAttributes)
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
