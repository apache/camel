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

public fun UriDsl.web3j(i: Web3jUriDsl.() -> Unit) {
  Web3jUriDsl(this).apply(i)
}

@CamelDslMarker
public class Web3jUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("web3j")
  }

  private var nodeAddress: String = ""

  public fun nodeAddress(nodeAddress: String) {
    this.nodeAddress = nodeAddress
    it.url("$nodeAddress")
  }

  public fun addresses(addresses: String) {
    it.property("addresses", addresses)
  }

  public fun fromAddress(fromAddress: String) {
    it.property("fromAddress", fromAddress)
  }

  public fun fromBlock(fromBlock: String) {
    it.property("fromBlock", fromBlock)
  }

  public fun fullTransactionObjects(fullTransactionObjects: String) {
    it.property("fullTransactionObjects", fullTransactionObjects)
  }

  public fun fullTransactionObjects(fullTransactionObjects: Boolean) {
    it.property("fullTransactionObjects", fullTransactionObjects.toString())
  }

  public fun gasLimit(gasLimit: String) {
    it.property("gasLimit", gasLimit)
  }

  public fun privateFor(privateFor: String) {
    it.property("privateFor", privateFor)
  }

  public fun quorumAPI(quorumAPI: String) {
    it.property("quorumAPI", quorumAPI)
  }

  public fun quorumAPI(quorumAPI: Boolean) {
    it.property("quorumAPI", quorumAPI.toString())
  }

  public fun toAddress(toAddress: String) {
    it.property("toAddress", toAddress)
  }

  public fun toBlock(toBlock: String) {
    it.property("toBlock", toBlock)
  }

  public fun topics(topics: String) {
    it.property("topics", topics)
  }

  public fun web3j(web3j: String) {
    it.property("web3j", web3j)
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

  public fun address(address: String) {
    it.property("address", address)
  }

  public fun atBlock(atBlock: String) {
    it.property("atBlock", atBlock)
  }

  public fun blockHash(blockHash: String) {
    it.property("blockHash", blockHash)
  }

  public fun clientId(clientId: String) {
    it.property("clientId", clientId)
  }

  public fun `data`(`data`: String) {
    it.property("data", data)
  }

  public fun databaseName(databaseName: String) {
    it.property("databaseName", databaseName)
  }

  public fun filterId(filterId: String) {
    it.property("filterId", filterId)
  }

  public fun gasPrice(gasPrice: String) {
    it.property("gasPrice", gasPrice)
  }

  public fun hashrate(hashrate: String) {
    it.property("hashrate", hashrate)
  }

  public fun headerPowHash(headerPowHash: String) {
    it.property("headerPowHash", headerPowHash)
  }

  public fun index(index: String) {
    it.property("index", index)
  }

  public fun keyName(keyName: String) {
    it.property("keyName", keyName)
  }

  public fun mixDigest(mixDigest: String) {
    it.property("mixDigest", mixDigest)
  }

  public fun nonce(nonce: String) {
    it.property("nonce", nonce)
  }

  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  public fun position(position: String) {
    it.property("position", position)
  }

  public fun priority(priority: String) {
    it.property("priority", priority)
  }

  public fun sha3HashOfDataToSign(sha3HashOfDataToSign: String) {
    it.property("sha3HashOfDataToSign", sha3HashOfDataToSign)
  }

  public fun signedTransactionData(signedTransactionData: String) {
    it.property("signedTransactionData", signedTransactionData)
  }

  public fun sourceCode(sourceCode: String) {
    it.property("sourceCode", sourceCode)
  }

  public fun transactionHash(transactionHash: String) {
    it.property("transactionHash", transactionHash)
  }

  public fun ttl(ttl: String) {
    it.property("ttl", ttl)
  }

  public fun `value`(`value`: String) {
    it.property("value", value)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
