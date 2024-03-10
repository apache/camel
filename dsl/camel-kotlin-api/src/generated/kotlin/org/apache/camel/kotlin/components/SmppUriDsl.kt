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
 * Send and receive SMS messages using a SMSC (Short Message Service Center).
 */
public fun UriDsl.smpp(i: SmppUriDsl.() -> Unit) {
  SmppUriDsl(this).apply(i)
}

@CamelDslMarker
public class SmppUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("smpp")
  }

  private var host: String = ""

  private var port: String = ""

  /**
   * Hostname for the SMSC server to use.
   */
  public fun host(host: String) {
    this.host = host
    it.url("$host:$port")
  }

  /**
   * Port number for the SMSC server to use.
   */
  public fun port(port: String) {
    this.port = port
    it.url("$host:$port")
  }

  /**
   * Port number for the SMSC server to use.
   */
  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$host:$port")
  }

  /**
   * Defines the initial delay in milliseconds after the consumer/producer tries to reconnect to the
   * SMSC, after the connection was lost.
   */
  public fun initialReconnectDelay(initialReconnectDelay: String) {
    it.property("initialReconnectDelay", initialReconnectDelay)
  }

  /**
   * Defines the initial delay in milliseconds after the consumer/producer tries to reconnect to the
   * SMSC, after the connection was lost.
   */
  public fun initialReconnectDelay(initialReconnectDelay: Int) {
    it.property("initialReconnectDelay", initialReconnectDelay.toString())
  }

  /**
   * Defines the maximum number of attempts to reconnect to the SMSC, if SMSC returns a negative
   * bind response
   */
  public fun maxReconnect(maxReconnect: String) {
    it.property("maxReconnect", maxReconnect)
  }

  /**
   * Defines the maximum number of attempts to reconnect to the SMSC, if SMSC returns a negative
   * bind response
   */
  public fun maxReconnect(maxReconnect: Int) {
    it.property("maxReconnect", maxReconnect.toString())
  }

  /**
   * Defines the interval in milliseconds between the reconnect attempts, if the connection to the
   * SMSC was lost and the previous was not succeed.
   */
  public fun reconnectDelay(reconnectDelay: String) {
    it.property("reconnectDelay", reconnectDelay)
  }

  /**
   * Defines the interval in milliseconds between the reconnect attempts, if the connection to the
   * SMSC was lost and the previous was not succeed.
   */
  public fun reconnectDelay(reconnectDelay: Int) {
    it.property("reconnectDelay", reconnectDelay.toString())
  }

  /**
   * You can specify a policy for handling long messages: ALLOW - the default, long messages are
   * split to 140 bytes per message TRUNCATE - long messages are split and only the first fragment will
   * be sent to the SMSC. Some carriers drop subsequent fragments so this reduces load on the SMPP
   * connection sending parts of a message that will never be delivered. REJECT - if a message would
   * need to be split, it is rejected with an SMPP NegativeResponseException and the reason code
   * signifying the message is too long.
   */
  public fun splittingPolicy(splittingPolicy: String) {
    it.property("splittingPolicy", splittingPolicy)
  }

  /**
   * This parameter is used to categorize the type of ESME (External Short Message Entity) that is
   * binding to the SMSC (max. 13 characters).
   */
  public fun systemType(systemType: String) {
    it.property("systemType", systemType)
  }

  /**
   * You can specify the address range for the SmppConsumer as defined in section 5.2.7 of the SMPP
   * 3.4 specification. The SmppConsumer will receive messages only from SMSC's which target an address
   * (MSISDN or IP address) within this range.
   */
  public fun addressRange(addressRange: String) {
    it.property("addressRange", addressRange)
  }

  /**
   * Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions
   * (if possible) occurred while the Camel consumer is trying to pickup incoming messages, or the
   * likes, will now be processed as a message and handled by the routing Error Handler. Important:
   * This is only possible if the 3rd party component allows Camel to be alerted if an exception was
   * thrown. Some components handle this internally only, and therefore bridgeErrorHandler is not
   * possible. In other situations we may improve the Camel component to hook into the 3rd party
   * component and make this possible for future releases. By default the consumer will use the
   * org.apache.camel.spi.ExceptionHandler to deal with exceptions, that will be logged at WARN or
   * ERROR level and ignored.
   */
  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  /**
   * Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions
   * (if possible) occurred while the Camel consumer is trying to pickup incoming messages, or the
   * likes, will now be processed as a message and handled by the routing Error Handler. Important:
   * This is only possible if the 3rd party component allows Camel to be alerted if an exception was
   * thrown. Some components handle this internally only, and therefore bridgeErrorHandler is not
   * possible. In other situations we may improve the Camel component to hook into the 3rd party
   * component and make this possible for future releases. By default the consumer will use the
   * org.apache.camel.spi.ExceptionHandler to deal with exceptions, that will be logged at WARN or
   * ERROR level and ignored.
   */
  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
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
   * Defines the destination SME address. For mobile terminated messages, this is the directory
   * number of the recipient MS. Only for SubmitSm, SubmitMulti, CancelSm and DataSm.
   */
  public fun destAddr(destAddr: String) {
    it.property("destAddr", destAddr)
  }

  /**
   * Defines the type of number (TON) to be used in the SME destination address parameters. Only for
   * SubmitSm, SubmitMulti, CancelSm and DataSm. The following NPI values are defined: 0: Unknown 1:
   * ISDN (E163/E164) 2: Data (X.121) 3: Telex (F.69) 6: Land Mobile (E.212) 8: National 9: Private 10:
   * ERMES 13: Internet (IP) 18: WAP Client Id (to be defined by WAP Forum)
   */
  public fun destAddrNpi(destAddrNpi: String) {
    it.property("destAddrNpi", destAddrNpi)
  }

  /**
   * Defines the type of number (TON) to be used in the SME destination address parameters. Only for
   * SubmitSm, SubmitMulti, CancelSm and DataSm. The following NPI values are defined: 0: Unknown 1:
   * ISDN (E163/E164) 2: Data (X.121) 3: Telex (F.69) 6: Land Mobile (E.212) 8: National 9: Private 10:
   * ERMES 13: Internet (IP) 18: WAP Client Id (to be defined by WAP Forum)
   */
  public fun destAddrNpi(destAddrNpi: Int) {
    it.property("destAddrNpi", destAddrNpi.toString())
  }

  /**
   * Defines the type of number (TON) to be used in the SME destination address parameters. Only for
   * SubmitSm, SubmitMulti, CancelSm and DataSm. The following TON values are defined: 0: Unknown 1:
   * International 2: National 3: Network Specific 4: Subscriber Number 5: Alphanumeric 6: Abbreviated
   */
  public fun destAddrTon(destAddrTon: String) {
    it.property("destAddrTon", destAddrTon)
  }

  /**
   * Defines the type of number (TON) to be used in the SME destination address parameters. Only for
   * SubmitSm, SubmitMulti, CancelSm and DataSm. The following TON values are defined: 0: Unknown 1:
   * International 2: National 3: Network Specific 4: Subscriber Number 5: Alphanumeric 6: Abbreviated
   */
  public fun destAddrTon(destAddrTon: Int) {
    it.property("destAddrTon", destAddrTon.toString())
  }

  /**
   * Sessions can be lazily created to avoid exceptions, if the SMSC is not available when the Camel
   * producer is started. Camel will check the in message headers 'CamelSmppSystemId' and
   * 'CamelSmppPassword' of the first exchange. If they are present, Camel will use these data to
   * connect to the SMSC.
   */
  public fun lazySessionCreation(lazySessionCreation: String) {
    it.property("lazySessionCreation", lazySessionCreation)
  }

  /**
   * Sessions can be lazily created to avoid exceptions, if the SMSC is not available when the Camel
   * producer is started. Camel will check the in message headers 'CamelSmppSystemId' and
   * 'CamelSmppPassword' of the first exchange. If they are present, Camel will use these data to
   * connect to the SMSC.
   */
  public fun lazySessionCreation(lazySessionCreation: Boolean) {
    it.property("lazySessionCreation", lazySessionCreation.toString())
  }

  /**
   * Set this on producer in order to benefit from transceiver (TRX) binding type. So once set, you
   * don't need to define an 'SMTPP consumer' endpoint anymore. You would set this to a 'Direct
   * consumer' endpoint instead. DISCALIMER: This feature is only tested with 'Direct consumer'
   * endpoint. The behavior with any other consumer type is unknown and not tested.
   */
  public fun messageReceiverRouteId(messageReceiverRouteId: String) {
    it.property("messageReceiverRouteId", messageReceiverRouteId)
  }

  /**
   * Defines the numeric plan indicator (NPI) to be used in the SME. The following NPI values are
   * defined: 0: Unknown 1: ISDN (E163/E164) 2: Data (X.121) 3: Telex (F.69) 6: Land Mobile (E.212) 8:
   * National 9: Private 10: ERMES 13: Internet (IP) 18: WAP Client Id (to be defined by WAP Forum)
   */
  public fun numberingPlanIndicator(numberingPlanIndicator: String) {
    it.property("numberingPlanIndicator", numberingPlanIndicator)
  }

  /**
   * Defines the numeric plan indicator (NPI) to be used in the SME. The following NPI values are
   * defined: 0: Unknown 1: ISDN (E163/E164) 2: Data (X.121) 3: Telex (F.69) 6: Land Mobile (E.212) 8:
   * National 9: Private 10: ERMES 13: Internet (IP) 18: WAP Client Id (to be defined by WAP Forum)
   */
  public fun numberingPlanIndicator(numberingPlanIndicator: Int) {
    it.property("numberingPlanIndicator", numberingPlanIndicator.toString())
  }

  /**
   * Allows the originating SME to assign a priority level to the short message. Only for SubmitSm
   * and SubmitMulti. Four Priority Levels are supported: 0: Level 0 (lowest) priority 1: Level 1
   * priority 2: Level 2 priority 3: Level 3 (highest) priority
   */
  public fun priorityFlag(priorityFlag: String) {
    it.property("priorityFlag", priorityFlag)
  }

  /**
   * Allows the originating SME to assign a priority level to the short message. Only for SubmitSm
   * and SubmitMulti. Four Priority Levels are supported: 0: Level 0 (lowest) priority 1: Level 1
   * priority 2: Level 2 priority 3: Level 3 (highest) priority
   */
  public fun priorityFlag(priorityFlag: Int) {
    it.property("priorityFlag", priorityFlag.toString())
  }

  /**
   * The protocol id
   */
  public fun protocolId(protocolId: String) {
    it.property("protocolId", protocolId)
  }

  /**
   * The protocol id
   */
  public fun protocolId(protocolId: Int) {
    it.property("protocolId", protocolId.toString())
  }

  /**
   * Is used to request an SMSC delivery receipt and/or SME originated acknowledgements. The
   * following values are defined: 0: No SMSC delivery receipt requested. 1: SMSC delivery receipt
   * requested where final delivery outcome is success or failure. 2: SMSC delivery receipt requested
   * where the final delivery outcome is delivery failure.
   */
  public fun registeredDelivery(registeredDelivery: String) {
    it.property("registeredDelivery", registeredDelivery)
  }

  /**
   * Is used to request an SMSC delivery receipt and/or SME originated acknowledgements. The
   * following values are defined: 0: No SMSC delivery receipt requested. 1: SMSC delivery receipt
   * requested where final delivery outcome is success or failure. 2: SMSC delivery receipt requested
   * where the final delivery outcome is delivery failure.
   */
  public fun registeredDelivery(registeredDelivery: Int) {
    it.property("registeredDelivery", registeredDelivery.toString())
  }

  /**
   * Used to request the SMSC to replace a previously submitted message, that is still pending
   * delivery. The SMSC will replace an existing message provided that the source address, destination
   * address and service type match the same fields in the new message. The following replace if
   * present flag values are defined: 0: Don't replace 1: Replace
   */
  public fun replaceIfPresentFlag(replaceIfPresentFlag: String) {
    it.property("replaceIfPresentFlag", replaceIfPresentFlag)
  }

  /**
   * Used to request the SMSC to replace a previously submitted message, that is still pending
   * delivery. The SMSC will replace an existing message provided that the source address, destination
   * address and service type match the same fields in the new message. The following replace if
   * present flag values are defined: 0: Don't replace 1: Replace
   */
  public fun replaceIfPresentFlag(replaceIfPresentFlag: Int) {
    it.property("replaceIfPresentFlag", replaceIfPresentFlag.toString())
  }

  /**
   * The service type parameter can be used to indicate the SMS Application service associated with
   * the message. The following generic service_types are defined: CMT: Cellular Messaging CPT:
   * Cellular Paging VMN: Voice Mail Notification VMA: Voice Mail Alerting WAP: Wireless Application
   * Protocol USSD: Unstructured Supplementary Services Data
   */
  public fun serviceType(serviceType: String) {
    it.property("serviceType", serviceType)
  }

  /**
   * Defines the address of SME (Short Message Entity) which originated this message.
   */
  public fun sourceAddr(sourceAddr: String) {
    it.property("sourceAddr", sourceAddr)
  }

  /**
   * Defines the numeric plan indicator (NPI) to be used in the SME originator address parameters.
   * The following NPI values are defined: 0: Unknown 1: ISDN (E163/E164) 2: Data (X.121) 3: Telex
   * (F.69) 6: Land Mobile (E.212) 8: National 9: Private 10: ERMES 13: Internet (IP) 18: WAP Client Id
   * (to be defined by WAP Forum)
   */
  public fun sourceAddrNpi(sourceAddrNpi: String) {
    it.property("sourceAddrNpi", sourceAddrNpi)
  }

  /**
   * Defines the numeric plan indicator (NPI) to be used in the SME originator address parameters.
   * The following NPI values are defined: 0: Unknown 1: ISDN (E163/E164) 2: Data (X.121) 3: Telex
   * (F.69) 6: Land Mobile (E.212) 8: National 9: Private 10: ERMES 13: Internet (IP) 18: WAP Client Id
   * (to be defined by WAP Forum)
   */
  public fun sourceAddrNpi(sourceAddrNpi: Int) {
    it.property("sourceAddrNpi", sourceAddrNpi.toString())
  }

  /**
   * Defines the type of number (TON) to be used in the SME originator address parameters. The
   * following TON values are defined: 0: Unknown 1: International 2: National 3: Network Specific 4:
   * Subscriber Number 5: Alphanumeric 6: Abbreviated
   */
  public fun sourceAddrTon(sourceAddrTon: String) {
    it.property("sourceAddrTon", sourceAddrTon)
  }

  /**
   * Defines the type of number (TON) to be used in the SME originator address parameters. The
   * following TON values are defined: 0: Unknown 1: International 2: National 3: Network Specific 4:
   * Subscriber Number 5: Alphanumeric 6: Abbreviated
   */
  public fun sourceAddrTon(sourceAddrTon: Int) {
    it.property("sourceAddrTon", sourceAddrTon.toString())
  }

  /**
   * Defines the type of number (TON) to be used in the SME. The following TON values are defined:
   * 0: Unknown 1: International 2: National 3: Network Specific 4: Subscriber Number 5: Alphanumeric
   * 6: Abbreviated
   */
  public fun typeOfNumber(typeOfNumber: String) {
    it.property("typeOfNumber", typeOfNumber)
  }

  /**
   * Defines the type of number (TON) to be used in the SME. The following TON values are defined:
   * 0: Unknown 1: International 2: National 3: Network Specific 4: Subscriber Number 5: Alphanumeric
   * 6: Abbreviated
   */
  public fun typeOfNumber(typeOfNumber: Int) {
    it.property("typeOfNumber", typeOfNumber.toString())
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
   * Defines the interval in milliseconds between the confidence checks. The confidence check is
   * used to test the communication path between an ESME and an SMSC.
   */
  public fun enquireLinkTimer(enquireLinkTimer: String) {
    it.property("enquireLinkTimer", enquireLinkTimer)
  }

  /**
   * Defines the interval in milliseconds between the confidence checks. The confidence check is
   * used to test the communication path between an ESME and an SMSC.
   */
  public fun enquireLinkTimer(enquireLinkTimer: Int) {
    it.property("enquireLinkTimer", enquireLinkTimer.toString())
  }

  /**
   * Defines the interface version to be used in the binding request with the SMSC. The following
   * values are allowed, as defined in the SMPP protocol (and the underlying implementation using the
   * jSMPP library, respectively): legacy (0x00), 3.3 (0x33), 3.4 (0x34), and 5.0 (0x50). The default
   * (fallback) value is version 3.4.
   */
  public fun interfaceVersion(interfaceVersion: String) {
    it.property("interfaceVersion", interfaceVersion)
  }

  /**
   * Sets the number of threads which can read PDU and process them in parallel.
   */
  public fun pduProcessorDegree(pduProcessorDegree: String) {
    it.property("pduProcessorDegree", pduProcessorDegree)
  }

  /**
   * Sets the number of threads which can read PDU and process them in parallel.
   */
  public fun pduProcessorDegree(pduProcessorDegree: Int) {
    it.property("pduProcessorDegree", pduProcessorDegree.toString())
  }

  /**
   * Sets the capacity of the working queue for PDU processing.
   */
  public fun pduProcessorQueueCapacity(pduProcessorQueueCapacity: String) {
    it.property("pduProcessorQueueCapacity", pduProcessorQueueCapacity)
  }

  /**
   * Sets the capacity of the working queue for PDU processing.
   */
  public fun pduProcessorQueueCapacity(pduProcessorQueueCapacity: Int) {
    it.property("pduProcessorQueueCapacity", pduProcessorQueueCapacity.toString())
  }

  /**
   * You can refer to a org.jsmpp.session.SessionStateListener in the Registry to receive callbacks
   * when the session state changed.
   */
  public fun sessionStateListener(sessionStateListener: String) {
    it.property("sessionStateListener", sessionStateListener)
  }

  /**
   * When true, the SMSC delivery receipt would be requested only for the last segment of a
   * multi-segment (long) message. For short messages, with only 1 segment the behaviour is unchanged.
   */
  public fun singleDLR(singleDLR: String) {
    it.property("singleDLR", singleDLR)
  }

  /**
   * When true, the SMSC delivery receipt would be requested only for the last segment of a
   * multi-segment (long) message. For short messages, with only 1 segment the behaviour is unchanged.
   */
  public fun singleDLR(singleDLR: Boolean) {
    it.property("singleDLR", singleDLR.toString())
  }

  /**
   * Defines the maximum period of inactivity allowed after a transaction, after which an SMPP
   * entity may assume that the session is no longer active. This timer may be active on either
   * communicating SMPP entity (i.e. SMSC or ESME).
   */
  public fun transactionTimer(transactionTimer: String) {
    it.property("transactionTimer", transactionTimer)
  }

  /**
   * Defines the maximum period of inactivity allowed after a transaction, after which an SMPP
   * entity may assume that the session is no longer active. This timer may be active on either
   * communicating SMPP entity (i.e. SMSC or ESME).
   */
  public fun transactionTimer(transactionTimer: Int) {
    it.property("transactionTimer", transactionTimer.toString())
  }

  /**
   * Defines encoding of data according the SMPP 3.4 specification, section 5.2.19. 0: SMSC Default
   * Alphabet 4: 8 bit Alphabet 8: UCS2 Alphabet
   */
  public fun alphabet(alphabet: String) {
    it.property("alphabet", alphabet)
  }

  /**
   * Defines encoding of data according the SMPP 3.4 specification, section 5.2.19. 0: SMSC Default
   * Alphabet 4: 8 bit Alphabet 8: UCS2 Alphabet
   */
  public fun alphabet(alphabet: Int) {
    it.property("alphabet", alphabet.toString())
  }

  /**
   * Defines the data coding according the SMPP 3.4 specification, section 5.2.19. Example data
   * encodings are: 0: SMSC Default Alphabet 3: Latin 1 (ISO-8859-1) 4: Octet unspecified (8-bit
   * binary) 8: UCS2 (ISO/IEC-10646) 13: Extended Kanji JIS(X 0212-1990)
   */
  public fun dataCoding(dataCoding: String) {
    it.property("dataCoding", dataCoding)
  }

  /**
   * Defines the data coding according the SMPP 3.4 specification, section 5.2.19. Example data
   * encodings are: 0: SMSC Default Alphabet 3: Latin 1 (ISO-8859-1) 4: Octet unspecified (8-bit
   * binary) 8: UCS2 (ISO/IEC-10646) 13: Extended Kanji JIS(X 0212-1990)
   */
  public fun dataCoding(dataCoding: Int) {
    it.property("dataCoding", dataCoding.toString())
  }

  /**
   * Defines the encoding scheme of the short message user data. Only for SubmitSm, ReplaceSm and
   * SubmitMulti.
   */
  public fun encoding(encoding: String) {
    it.property("encoding", encoding)
  }

  /**
   * If you need to tunnel SMPP through a HTTP proxy, set this attribute to the hostname or ip
   * address of your HTTP proxy.
   */
  public fun httpProxyHost(httpProxyHost: String) {
    it.property("httpProxyHost", httpProxyHost)
  }

  /**
   * If your HTTP proxy requires basic authentication, set this attribute to the password required
   * for your HTTP proxy.
   */
  public fun httpProxyPassword(httpProxyPassword: String) {
    it.property("httpProxyPassword", httpProxyPassword)
  }

  /**
   * If you need to tunnel SMPP through a HTTP proxy, set this attribute to the port of your HTTP
   * proxy.
   */
  public fun httpProxyPort(httpProxyPort: String) {
    it.property("httpProxyPort", httpProxyPort)
  }

  /**
   * If you need to tunnel SMPP through a HTTP proxy, set this attribute to the port of your HTTP
   * proxy.
   */
  public fun httpProxyPort(httpProxyPort: Int) {
    it.property("httpProxyPort", httpProxyPort.toString())
  }

  /**
   * If your HTTP proxy requires basic authentication, set this attribute to the username required
   * for your HTTP proxy.
   */
  public fun httpProxyUsername(httpProxyUsername: String) {
    it.property("httpProxyUsername", httpProxyUsername)
  }

  /**
   * These headers will be passed to the proxy server while establishing the connection.
   */
  public fun proxyHeaders(proxyHeaders: String) {
    it.property("proxyHeaders", proxyHeaders)
  }

  /**
   * The password for connecting to SMSC server.
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * The system id (username) for connecting to SMSC server.
   */
  public fun systemId(systemId: String) {
    it.property("systemId", systemId)
  }

  /**
   * Whether using SSL with the smpps protocol
   */
  public fun usingSSL(usingSSL: String) {
    it.property("usingSSL", usingSSL)
  }

  /**
   * Whether using SSL with the smpps protocol
   */
  public fun usingSSL(usingSSL: Boolean) {
    it.property("usingSSL", usingSSL.toString())
  }
}
