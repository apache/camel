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

/**
 * Transfer data securely and reliably using the AS2 protocol (RFC4130).
 */
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

  /**
   * What kind of operation to perform
   */
  public fun apiName(apiName: String) {
    this.apiName = apiName
    it.url("$apiName/$methodName")
  }

  /**
   * What sub operation to use for the selected operation
   */
  public fun methodName(methodName: String) {
    this.methodName = methodName
    it.url("$apiName/$methodName")
  }

  /**
   * The value of the AS2From header of AS2 message.
   */
  public fun as2From(as2From: String) {
    it.property("as2From", as2From)
  }

  /**
   * The structure of AS2 Message. One of: PLAIN - No encryption, no signature, SIGNED - No
   * encryption, signature, ENCRYPTED - Encryption, no signature, ENCRYPTED_SIGNED - Encryption,
   * signature
   */
  public fun as2MessageStructure(as2MessageStructure: String) {
    it.property("as2MessageStructure", as2MessageStructure)
  }

  /**
   * The value of the AS2To header of AS2 message.
   */
  public fun as2To(as2To: String) {
    it.property("as2To", as2To)
  }

  /**
   * The version of the AS2 protocol.
   */
  public fun as2Version(as2Version: String) {
    it.property("as2Version", as2Version)
  }

  /**
   * The port number of asynchronous MDN server.
   */
  public fun asyncMdnPortNumber(asyncMdnPortNumber: String) {
    it.property("asyncMdnPortNumber", asyncMdnPortNumber)
  }

  /**
   * The port number of asynchronous MDN server.
   */
  public fun asyncMdnPortNumber(asyncMdnPortNumber: Int) {
    it.property("asyncMdnPortNumber", asyncMdnPortNumber.toString())
  }

  /**
   * The name of the attached file
   */
  public fun attachedFileName(attachedFileName: String) {
    it.property("attachedFileName", attachedFileName)
  }

  /**
   * The Client Fully Qualified Domain Name (FQDN). Used in message ids sent by endpoint.
   */
  public fun clientFqdn(clientFqdn: String) {
    it.property("clientFqdn", clientFqdn)
  }

  /**
   * The algorithm used to compress EDI message.
   */
  public fun compressionAlgorithm(compressionAlgorithm: String) {
    it.property("compressionAlgorithm", compressionAlgorithm)
  }

  /**
   * The value of the Disposition-Notification-To header. Assigning a value to this parameter
   * requests a message disposition notification (MDN) for the AS2 message.
   */
  public fun dispositionNotificationTo(dispositionNotificationTo: String) {
    it.property("dispositionNotificationTo", dispositionNotificationTo)
  }

  /**
   * The transfer encoding of EDI message.
   */
  public fun ediMessageTransferEncoding(ediMessageTransferEncoding: String) {
    it.property("ediMessageTransferEncoding", ediMessageTransferEncoding)
  }

  /**
   * The content type of EDI message. One of application/edifact, application/edi-x12,
   * application/edi-consent, application/xml
   */
  public fun ediMessageType(ediMessageType: String) {
    it.property("ediMessageType", ediMessageType)
  }

  /**
   * The value of the From header of AS2 message.
   */
  public fun from(from: String) {
    it.property("from", from)
  }

  /**
   * Set hostname verifier for SSL session.
   */
  public fun hostnameVerifier(hostnameVerifier: String) {
    it.property("hostnameVerifier", hostnameVerifier)
  }

  /**
   * The maximum size of the connection pool for http connections (client only)
   */
  public fun httpConnectionPoolSize(httpConnectionPoolSize: String) {
    it.property("httpConnectionPoolSize", httpConnectionPoolSize)
  }

  /**
   * The maximum size of the connection pool for http connections (client only)
   */
  public fun httpConnectionPoolSize(httpConnectionPoolSize: Int) {
    it.property("httpConnectionPoolSize", httpConnectionPoolSize.toString())
  }

  /**
   * The time to live for connections in the connection pool (client only)
   */
  public fun httpConnectionPoolTtl(httpConnectionPoolTtl: String) {
    it.property("httpConnectionPoolTtl", httpConnectionPoolTtl)
  }

  /**
   * The timeout of the http connection (client only)
   */
  public fun httpConnectionTimeout(httpConnectionTimeout: String) {
    it.property("httpConnectionTimeout", httpConnectionTimeout)
  }

  /**
   * The timeout of the underlying http socket (client only)
   */
  public fun httpSocketTimeout(httpSocketTimeout: String) {
    it.property("httpSocketTimeout", httpSocketTimeout)
  }

  /**
   * Sets the name of a parameter to be passed in the exchange In Body
   */
  public fun inBody(inBody: String) {
    it.property("inBody", inBody)
  }

  /**
   * The template used to format MDN message
   */
  public fun mdnMessageTemplate(mdnMessageTemplate: String) {
    it.property("mdnMessageTemplate", mdnMessageTemplate)
  }

  /**
   * The return URL that the message receiver should send an asynchronous MDN to. If not present the
   * receipt is synchronous. (Client only)
   */
  public fun receiptDeliveryOption(receiptDeliveryOption: String) {
    it.property("receiptDeliveryOption", receiptDeliveryOption)
  }

  /**
   * The request URI of EDI message.
   */
  public fun requestUri(requestUri: String) {
    it.property("requestUri", requestUri)
  }

  /**
   * The value included in the Server message header identifying the AS2 Server.
   */
  public fun server(server: String) {
    it.property("server", server)
  }

  /**
   * The Server Fully Qualified Domain Name (FQDN). Used in message ids sent by endpoint.
   */
  public fun serverFqdn(serverFqdn: String) {
    it.property("serverFqdn", serverFqdn)
  }

  /**
   * The port number of server.
   */
  public fun serverPortNumber(serverPortNumber: String) {
    it.property("serverPortNumber", serverPortNumber)
  }

  /**
   * The port number of server.
   */
  public fun serverPortNumber(serverPortNumber: Int) {
    it.property("serverPortNumber", serverPortNumber.toString())
  }

  /**
   * Set SSL context for connection to remote server.
   */
  public fun sslContext(sslContext: String) {
    it.property("sslContext", sslContext)
  }

  /**
   * The value of Subject header of AS2 message.
   */
  public fun subject(subject: String) {
    it.property("subject", subject)
  }

  /**
   * The host name (IP or DNS name) of target host.
   */
  public fun targetHostname(targetHostname: String) {
    it.property("targetHostname", targetHostname)
  }

  /**
   * The port number of target host. -1 indicates the scheme default port.
   */
  public fun targetPortNumber(targetPortNumber: String) {
    it.property("targetPortNumber", targetPortNumber)
  }

  /**
   * The port number of target host. -1 indicates the scheme default port.
   */
  public fun targetPortNumber(targetPortNumber: Int) {
    it.property("targetPortNumber", targetPortNumber.toString())
  }

  /**
   * The value included in the User-Agent message header identifying the AS2 user agent.
   */
  public fun userAgent(userAgent: String) {
    it.property("userAgent", userAgent)
  }

  /**
   * To let the consumer use a custom ExceptionHandler. Notice if the option bridgeErrorHandler is
   * enabled then this option is not in use. By default the consumer will deal with exceptions, that
   * will be logged at WARN or ERROR level and ignored.
   */
  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  /**
   * Sets the exchange pattern when the consumer creates an exchange.
   */
  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
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
   * The key used to encrypt the EDI message.
   */
  public fun decryptingPrivateKey(decryptingPrivateKey: String) {
    it.property("decryptingPrivateKey", decryptingPrivateKey)
  }

  /**
   * The algorithm used to encrypt EDI message.
   */
  public fun encryptingAlgorithm(encryptingAlgorithm: String) {
    it.property("encryptingAlgorithm", encryptingAlgorithm)
  }

  /**
   * The chain of certificates used to encrypt EDI message.
   */
  public fun encryptingCertificateChain(encryptingCertificateChain: String) {
    it.property("encryptingCertificateChain", encryptingCertificateChain)
  }

  /**
   * The list of algorithms, in order of preference, requested to generate a message integrity check
   * (MIC) returned in message dispostion notification (MDN)
   */
  public fun signedReceiptMicAlgorithms(signedReceiptMicAlgorithms: String) {
    it.property("signedReceiptMicAlgorithms", signedReceiptMicAlgorithms)
  }

  /**
   * The algorithm used to sign EDI message.
   */
  public fun signingAlgorithm(signingAlgorithm: String) {
    it.property("signingAlgorithm", signingAlgorithm)
  }

  /**
   * The chain of certificates used to sign EDI message.
   */
  public fun signingCertificateChain(signingCertificateChain: String) {
    it.property("signingCertificateChain", signingCertificateChain)
  }

  /**
   * The key used to sign the EDI message.
   */
  public fun signingPrivateKey(signingPrivateKey: String) {
    it.property("signingPrivateKey", signingPrivateKey)
  }

  /**
   * Certificates to validate the message's signature against. If not supplied, validation will not
   * take place. Server: validates the received message. Client: not yet implemented, should validate
   * the MDN
   */
  public fun validateSigningCertificateChain(validateSigningCertificateChain: String) {
    it.property("validateSigningCertificateChain", validateSigningCertificateChain)
  }
}
