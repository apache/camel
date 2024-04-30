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
 * Interact with Ethereum nodes using web3j client API.
 */
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

  /**
   * Sets the node address used to communicate
   */
  public fun nodeAddress(nodeAddress: String) {
    this.nodeAddress = nodeAddress
    it.url("$nodeAddress")
  }

  /**
   * Contract address or a list of addresses.
   */
  public fun addresses(addresses: String) {
    it.property("addresses", addresses)
  }

  /**
   * The address the transaction is send from
   */
  public fun fromAddress(fromAddress: String) {
    it.property("fromAddress", fromAddress)
  }

  /**
   * The block number, or the string latest for the last mined block or pending, earliest for not
   * yet mined transactions.
   */
  public fun fromBlock(fromBlock: String) {
    it.property("fromBlock", fromBlock)
  }

  /**
   * If true it returns the full transaction objects, if false only the hashes of the transactions.
   */
  public fun fullTransactionObjects(fullTransactionObjects: String) {
    it.property("fullTransactionObjects", fullTransactionObjects)
  }

  /**
   * If true it returns the full transaction objects, if false only the hashes of the transactions.
   */
  public fun fullTransactionObjects(fullTransactionObjects: Boolean) {
    it.property("fullTransactionObjects", fullTransactionObjects.toString())
  }

  /**
   * The maximum gas allowed in this block.
   */
  public fun gasLimit(gasLimit: String) {
    it.property("gasLimit", gasLimit)
  }

  /**
   * A transaction privateFor nodes with public keys in a Quorum network
   */
  public fun privateFor(privateFor: String) {
    it.property("privateFor", privateFor)
  }

  /**
   * If true, this will support Quorum API.
   */
  public fun quorumAPI(quorumAPI: String) {
    it.property("quorumAPI", quorumAPI)
  }

  /**
   * If true, this will support Quorum API.
   */
  public fun quorumAPI(quorumAPI: Boolean) {
    it.property("quorumAPI", quorumAPI.toString())
  }

  /**
   * The address the transaction is directed to.
   */
  public fun toAddress(toAddress: String) {
    it.property("toAddress", toAddress)
  }

  /**
   * The block number, or the string latest for the last mined block or pending, earliest for not
   * yet mined transactions.
   */
  public fun toBlock(toBlock: String) {
    it.property("toBlock", toBlock)
  }

  /**
   * Topics are order-dependent. Each topic can also be a list of topics. Specify multiple topics
   * separated by comma.
   */
  public fun topics(topics: String) {
    it.property("topics", topics)
  }

  /**
   * The preconfigured Web3j object.
   */
  public fun web3j(web3j: String) {
    it.property("web3j", web3j)
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
   * Contract address.
   */
  public fun address(address: String) {
    it.property("address", address)
  }

  /**
   * The block number, or the string latest for the last mined block or pending, earliest for not
   * yet mined transactions.
   */
  public fun atBlock(atBlock: String) {
    it.property("atBlock", atBlock)
  }

  /**
   * Hash of the block where this transaction was in.
   */
  public fun blockHash(blockHash: String) {
    it.property("blockHash", blockHash)
  }

  /**
   * A random hexadecimal(32 bytes) ID identifying the client.
   */
  public fun clientId(clientId: String) {
    it.property("clientId", clientId)
  }

  /**
   * The compiled code of a contract OR the hash of the invoked method signature and encoded
   * parameters.
   */
  public fun `data`(`data`: String) {
    it.property("data", data)
  }

  /**
   * The local database name.
   */
  public fun databaseName(databaseName: String) {
    it.property("databaseName", databaseName)
  }

  /**
   * The filter id to use.
   */
  public fun filterId(filterId: String) {
    it.property("filterId", filterId)
  }

  /**
   * Gas price used for each paid gas.
   */
  public fun gasPrice(gasPrice: String) {
    it.property("gasPrice", gasPrice)
  }

  /**
   * A hexadecimal string representation (32 bytes) of the hash rate.
   */
  public fun hashrate(hashrate: String) {
    it.property("hashrate", hashrate)
  }

  /**
   * The header's pow-hash (256 bits) used for submitting a proof-of-work solution.
   */
  public fun headerPowHash(headerPowHash: String) {
    it.property("headerPowHash", headerPowHash)
  }

  /**
   * The transactions/uncle index position in the block.
   */
  public fun index(index: String) {
    it.property("index", index)
  }

  /**
   * The key name in the database.
   */
  public fun keyName(keyName: String) {
    it.property("keyName", keyName)
  }

  /**
   * The mix digest (256 bits) used for submitting a proof-of-work solution.
   */
  public fun mixDigest(mixDigest: String) {
    it.property("mixDigest", mixDigest)
  }

  /**
   * The nonce found (64 bits) used for submitting a proof-of-work solution.
   */
  public fun nonce(nonce: String) {
    it.property("nonce", nonce)
  }

  /**
   * Operation to use.
   */
  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  /**
   * The transaction index position withing a block.
   */
  public fun position(position: String) {
    it.property("position", position)
  }

  /**
   * The priority of a whisper message.
   */
  public fun priority(priority: String) {
    it.property("priority", priority)
  }

  /**
   * Message to sign by calculating an Ethereum specific signature.
   */
  public fun sha3HashOfDataToSign(sha3HashOfDataToSign: String) {
    it.property("sha3HashOfDataToSign", sha3HashOfDataToSign)
  }

  /**
   * The signed transaction data for a new message call transaction or a contract creation for
   * signed transactions.
   */
  public fun signedTransactionData(signedTransactionData: String) {
    it.property("signedTransactionData", signedTransactionData)
  }

  /**
   * The source code to compile.
   */
  public fun sourceCode(sourceCode: String) {
    it.property("sourceCode", sourceCode)
  }

  /**
   * The information about a transaction requested by transaction hash.
   */
  public fun transactionHash(transactionHash: String) {
    it.property("transactionHash", transactionHash)
  }

  /**
   * The time to live in seconds of a whisper message.
   */
  public fun ttl(ttl: String) {
    it.property("ttl", ttl)
  }

  /**
   * The value sent within a transaction.
   */
  public fun `value`(`value`: String) {
    it.property("value", value)
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
}
