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

  public fun host(host: String) {
    this.host = host
    it.url("$host:$port")
  }

  public fun port(port: String) {
    this.port = port
    it.url("$host:$port")
  }

  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$host:$port")
  }

  public fun initialReconnectDelay(initialReconnectDelay: String) {
    it.property("initialReconnectDelay", initialReconnectDelay)
  }

  public fun initialReconnectDelay(initialReconnectDelay: Int) {
    it.property("initialReconnectDelay", initialReconnectDelay.toString())
  }

  public fun maxReconnect(maxReconnect: String) {
    it.property("maxReconnect", maxReconnect)
  }

  public fun maxReconnect(maxReconnect: Int) {
    it.property("maxReconnect", maxReconnect.toString())
  }

  public fun reconnectDelay(reconnectDelay: String) {
    it.property("reconnectDelay", reconnectDelay)
  }

  public fun reconnectDelay(reconnectDelay: Int) {
    it.property("reconnectDelay", reconnectDelay.toString())
  }

  public fun splittingPolicy(splittingPolicy: String) {
    it.property("splittingPolicy", splittingPolicy)
  }

  public fun systemType(systemType: String) {
    it.property("systemType", systemType)
  }

  public fun addressRange(addressRange: String) {
    it.property("addressRange", addressRange)
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
  }

  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
  }

  public fun destAddr(destAddr: String) {
    it.property("destAddr", destAddr)
  }

  public fun destAddrNpi(destAddrNpi: String) {
    it.property("destAddrNpi", destAddrNpi)
  }

  public fun destAddrNpi(destAddrNpi: Int) {
    it.property("destAddrNpi", destAddrNpi.toString())
  }

  public fun destAddrTon(destAddrTon: String) {
    it.property("destAddrTon", destAddrTon)
  }

  public fun destAddrTon(destAddrTon: Int) {
    it.property("destAddrTon", destAddrTon.toString())
  }

  public fun lazySessionCreation(lazySessionCreation: String) {
    it.property("lazySessionCreation", lazySessionCreation)
  }

  public fun lazySessionCreation(lazySessionCreation: Boolean) {
    it.property("lazySessionCreation", lazySessionCreation.toString())
  }

  public fun messageReceiverRouteId(messageReceiverRouteId: String) {
    it.property("messageReceiverRouteId", messageReceiverRouteId)
  }

  public fun numberingPlanIndicator(numberingPlanIndicator: String) {
    it.property("numberingPlanIndicator", numberingPlanIndicator)
  }

  public fun numberingPlanIndicator(numberingPlanIndicator: Int) {
    it.property("numberingPlanIndicator", numberingPlanIndicator.toString())
  }

  public fun priorityFlag(priorityFlag: String) {
    it.property("priorityFlag", priorityFlag)
  }

  public fun priorityFlag(priorityFlag: Int) {
    it.property("priorityFlag", priorityFlag.toString())
  }

  public fun protocolId(protocolId: String) {
    it.property("protocolId", protocolId)
  }

  public fun protocolId(protocolId: Int) {
    it.property("protocolId", protocolId.toString())
  }

  public fun registeredDelivery(registeredDelivery: String) {
    it.property("registeredDelivery", registeredDelivery)
  }

  public fun registeredDelivery(registeredDelivery: Int) {
    it.property("registeredDelivery", registeredDelivery.toString())
  }

  public fun replaceIfPresentFlag(replaceIfPresentFlag: String) {
    it.property("replaceIfPresentFlag", replaceIfPresentFlag)
  }

  public fun replaceIfPresentFlag(replaceIfPresentFlag: Int) {
    it.property("replaceIfPresentFlag", replaceIfPresentFlag.toString())
  }

  public fun serviceType(serviceType: String) {
    it.property("serviceType", serviceType)
  }

  public fun sourceAddr(sourceAddr: String) {
    it.property("sourceAddr", sourceAddr)
  }

  public fun sourceAddrNpi(sourceAddrNpi: String) {
    it.property("sourceAddrNpi", sourceAddrNpi)
  }

  public fun sourceAddrNpi(sourceAddrNpi: Int) {
    it.property("sourceAddrNpi", sourceAddrNpi.toString())
  }

  public fun sourceAddrTon(sourceAddrTon: String) {
    it.property("sourceAddrTon", sourceAddrTon)
  }

  public fun sourceAddrTon(sourceAddrTon: Int) {
    it.property("sourceAddrTon", sourceAddrTon.toString())
  }

  public fun typeOfNumber(typeOfNumber: String) {
    it.property("typeOfNumber", typeOfNumber)
  }

  public fun typeOfNumber(typeOfNumber: Int) {
    it.property("typeOfNumber", typeOfNumber.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun enquireLinkTimer(enquireLinkTimer: String) {
    it.property("enquireLinkTimer", enquireLinkTimer)
  }

  public fun enquireLinkTimer(enquireLinkTimer: Int) {
    it.property("enquireLinkTimer", enquireLinkTimer.toString())
  }

  public fun interfaceVersion(interfaceVersion: String) {
    it.property("interfaceVersion", interfaceVersion)
  }

  public fun pduProcessorDegree(pduProcessorDegree: String) {
    it.property("pduProcessorDegree", pduProcessorDegree)
  }

  public fun pduProcessorDegree(pduProcessorDegree: Int) {
    it.property("pduProcessorDegree", pduProcessorDegree.toString())
  }

  public fun pduProcessorQueueCapacity(pduProcessorQueueCapacity: String) {
    it.property("pduProcessorQueueCapacity", pduProcessorQueueCapacity)
  }

  public fun pduProcessorQueueCapacity(pduProcessorQueueCapacity: Int) {
    it.property("pduProcessorQueueCapacity", pduProcessorQueueCapacity.toString())
  }

  public fun sessionStateListener(sessionStateListener: String) {
    it.property("sessionStateListener", sessionStateListener)
  }

  public fun singleDLR(singleDLR: String) {
    it.property("singleDLR", singleDLR)
  }

  public fun singleDLR(singleDLR: Boolean) {
    it.property("singleDLR", singleDLR.toString())
  }

  public fun transactionTimer(transactionTimer: String) {
    it.property("transactionTimer", transactionTimer)
  }

  public fun transactionTimer(transactionTimer: Int) {
    it.property("transactionTimer", transactionTimer.toString())
  }

  public fun alphabet(alphabet: String) {
    it.property("alphabet", alphabet)
  }

  public fun alphabet(alphabet: Int) {
    it.property("alphabet", alphabet.toString())
  }

  public fun dataCoding(dataCoding: String) {
    it.property("dataCoding", dataCoding)
  }

  public fun dataCoding(dataCoding: Int) {
    it.property("dataCoding", dataCoding.toString())
  }

  public fun encoding(encoding: String) {
    it.property("encoding", encoding)
  }

  public fun httpProxyHost(httpProxyHost: String) {
    it.property("httpProxyHost", httpProxyHost)
  }

  public fun httpProxyPassword(httpProxyPassword: String) {
    it.property("httpProxyPassword", httpProxyPassword)
  }

  public fun httpProxyPort(httpProxyPort: String) {
    it.property("httpProxyPort", httpProxyPort)
  }

  public fun httpProxyPort(httpProxyPort: Int) {
    it.property("httpProxyPort", httpProxyPort.toString())
  }

  public fun httpProxyUsername(httpProxyUsername: String) {
    it.property("httpProxyUsername", httpProxyUsername)
  }

  public fun proxyHeaders(proxyHeaders: String) {
    it.property("proxyHeaders", proxyHeaders)
  }

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun systemId(systemId: String) {
    it.property("systemId", systemId)
  }

  public fun usingSSL(usingSSL: String) {
    it.property("usingSSL", usingSSL)
  }

  public fun usingSSL(usingSSL: Boolean) {
    it.property("usingSSL", usingSSL.toString())
  }
}
