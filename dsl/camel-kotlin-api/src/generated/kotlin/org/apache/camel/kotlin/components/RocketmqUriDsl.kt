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
 * Send and receive messages from RocketMQ cluster.
 */
public fun UriDsl.rocketmq(i: RocketmqUriDsl.() -> Unit) {
  RocketmqUriDsl(this).apply(i)
}

@CamelDslMarker
public class RocketmqUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("rocketmq")
  }

  private var topicName: String = ""

  /**
   * Topic name of this endpoint.
   */
  public fun topicName(topicName: String) {
    this.topicName = topicName
    it.url("$topicName")
  }

  /**
   * Name server address of RocketMQ cluster.
   */
  public fun namesrvAddr(namesrvAddr: String) {
    it.property("namesrvAddr", namesrvAddr)
  }

  /**
   * Consumer group name.
   */
  public fun consumerGroup(consumerGroup: String) {
    it.property("consumerGroup", consumerGroup)
  }

  /**
   * Subscribe tags of consumer. Multiple tags could be split by , such as TagATagB
   */
  public fun subscribeTags(subscribeTags: String) {
    it.property("subscribeTags", subscribeTags)
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
   * Producer group name.
   */
  public fun producerGroup(producerGroup: String) {
    it.property("producerGroup", producerGroup)
  }

  /**
   * Consumer group name used for receiving response.
   */
  public fun replyToConsumerGroup(replyToConsumerGroup: String) {
    it.property("replyToConsumerGroup", replyToConsumerGroup)
  }

  /**
   * Topic used for receiving response when using in-out pattern.
   */
  public fun replyToTopic(replyToTopic: String) {
    it.property("replyToTopic", replyToTopic)
  }

  /**
   * Each message would be sent with this tag.
   */
  public fun sendTag(sendTag: String) {
    it.property("sendTag", sendTag)
  }

  /**
   * Whether waiting for send result before routing to next endpoint.
   */
  public fun waitForSendResult(waitForSendResult: String) {
    it.property("waitForSendResult", waitForSendResult)
  }

  /**
   * Whether waiting for send result before routing to next endpoint.
   */
  public fun waitForSendResult(waitForSendResult: Boolean) {
    it.property("waitForSendResult", waitForSendResult.toString())
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
   * Check interval milliseconds of request timeout.
   */
  public fun requestTimeoutCheckerIntervalMillis(requestTimeoutCheckerIntervalMillis: String) {
    it.property("requestTimeoutCheckerIntervalMillis", requestTimeoutCheckerIntervalMillis)
  }

  /**
   * Check interval milliseconds of request timeout.
   */
  public fun requestTimeoutCheckerIntervalMillis(requestTimeoutCheckerIntervalMillis: Int) {
    it.property("requestTimeoutCheckerIntervalMillis",
        requestTimeoutCheckerIntervalMillis.toString())
  }

  /**
   * Timeout milliseconds of receiving response when using in-out pattern.
   */
  public fun requestTimeoutMillis(requestTimeoutMillis: String) {
    it.property("requestTimeoutMillis", requestTimeoutMillis)
  }

  /**
   * Timeout milliseconds of receiving response when using in-out pattern.
   */
  public fun requestTimeoutMillis(requestTimeoutMillis: Int) {
    it.property("requestTimeoutMillis", requestTimeoutMillis.toString())
  }

  /**
   * Access key for RocketMQ ACL.
   */
  public fun accessKey(accessKey: String) {
    it.property("accessKey", accessKey)
  }

  /**
   * Secret key for RocketMQ ACL.
   */
  public fun secretKey(secretKey: String) {
    it.property("secretKey", secretKey)
  }
}
