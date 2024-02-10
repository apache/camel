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
import kotlin.Int
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

public fun UriDsl.as2(i: As2UriDsl.() -> Unit) {
  As2UriDsl(this).apply(i)
}

@CamelDslMarker
public class As2UriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("as2")
  }

  private var apiName: String = ""

  private var methodName: String = ""

  public fun apiName(apiName: String) {
    this.apiName = apiName
    it.url("$apiName/$methodName")
  }

  public fun methodName(methodName: String) {
    this.methodName = methodName
    it.url("$apiName/$methodName")
  }

  public fun as2From(as2From: String) {
    it.property("as2From", as2From)
  }

  public fun as2MessageStructure(as2MessageStructure: String) {
    it.property("as2MessageStructure", as2MessageStructure)
  }

  public fun as2To(as2To: String) {
    it.property("as2To", as2To)
  }

  public fun as2Version(as2Version: String) {
    it.property("as2Version", as2Version)
  }

  public fun attachedFileName(attachedFileName: String) {
    it.property("attachedFileName", attachedFileName)
  }

  public fun clientFqdn(clientFqdn: String) {
    it.property("clientFqdn", clientFqdn)
  }

  public fun compressionAlgorithm(compressionAlgorithm: String) {
    it.property("compressionAlgorithm", compressionAlgorithm)
  }

  public fun dispositionNotificationTo(dispositionNotificationTo: String) {
    it.property("dispositionNotificationTo", dispositionNotificationTo)
  }

  public fun ediMessageTransferEncoding(ediMessageTransferEncoding: String) {
    it.property("ediMessageTransferEncoding", ediMessageTransferEncoding)
  }

  public fun ediMessageType(ediMessageType: String) {
    it.property("ediMessageType", ediMessageType)
  }

  public fun from(from: String) {
    it.property("from", from)
  }

  public fun hostnameVerifier(hostnameVerifier: String) {
    it.property("hostnameVerifier", hostnameVerifier)
  }

  public fun httpConnectionPoolSize(httpConnectionPoolSize: String) {
    it.property("httpConnectionPoolSize", httpConnectionPoolSize)
  }

  public fun httpConnectionPoolSize(httpConnectionPoolSize: Int) {
    it.property("httpConnectionPoolSize", httpConnectionPoolSize.toString())
  }

  public fun httpConnectionPoolTtl(httpConnectionPoolTtl: String) {
    it.property("httpConnectionPoolTtl", httpConnectionPoolTtl)
  }

  public fun httpConnectionTimeout(httpConnectionTimeout: String) {
    it.property("httpConnectionTimeout", httpConnectionTimeout)
  }

  public fun httpSocketTimeout(httpSocketTimeout: String) {
    it.property("httpSocketTimeout", httpSocketTimeout)
  }

  public fun inBody(inBody: String) {
    it.property("inBody", inBody)
  }

  public fun mdnMessageTemplate(mdnMessageTemplate: String) {
    it.property("mdnMessageTemplate", mdnMessageTemplate)
  }

  public fun requestUri(requestUri: String) {
    it.property("requestUri", requestUri)
  }

  public fun server(server: String) {
    it.property("server", server)
  }

  public fun serverFqdn(serverFqdn: String) {
    it.property("serverFqdn", serverFqdn)
  }

  public fun serverPortNumber(serverPortNumber: String) {
    it.property("serverPortNumber", serverPortNumber)
  }

  public fun serverPortNumber(serverPortNumber: Int) {
    it.property("serverPortNumber", serverPortNumber.toString())
  }

  public fun sslContext(sslContext: String) {
    it.property("sslContext", sslContext)
  }

  public fun subject(subject: String) {
    it.property("subject", subject)
  }

  public fun targetHostname(targetHostname: String) {
    it.property("targetHostname", targetHostname)
  }

  public fun targetPortNumber(targetPortNumber: String) {
    it.property("targetPortNumber", targetPortNumber)
  }

  public fun targetPortNumber(targetPortNumber: Int) {
    it.property("targetPortNumber", targetPortNumber.toString())
  }

  public fun userAgent(userAgent: String) {
    it.property("userAgent", userAgent)
  }

  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun decryptingPrivateKey(decryptingPrivateKey: String) {
    it.property("decryptingPrivateKey", decryptingPrivateKey)
  }

  public fun encryptingAlgorithm(encryptingAlgorithm: String) {
    it.property("encryptingAlgorithm", encryptingAlgorithm)
  }

  public fun encryptingCertificateChain(encryptingCertificateChain: String) {
    it.property("encryptingCertificateChain", encryptingCertificateChain)
  }

  public fun signedReceiptMicAlgorithms(signedReceiptMicAlgorithms: String) {
    it.property("signedReceiptMicAlgorithms", signedReceiptMicAlgorithms)
  }

  public fun signingAlgorithm(signingAlgorithm: String) {
    it.property("signingAlgorithm", signingAlgorithm)
  }

  public fun signingCertificateChain(signingCertificateChain: String) {
    it.property("signingCertificateChain", signingCertificateChain)
  }

  public fun signingPrivateKey(signingPrivateKey: String) {
    it.property("signingPrivateKey", signingPrivateKey)
  }

  public fun validateSigningCertificateChain(validateSigningCertificateChain: String) {
    it.property("validateSigningCertificateChain", validateSigningCertificateChain)
  }
}
