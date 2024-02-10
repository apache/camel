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

  public fun topicName(topicName: String) {
    this.topicName = topicName
    it.url("$topicName")
  }

  public fun namesrvAddr(namesrvAddr: String) {
    it.property("namesrvAddr", namesrvAddr)
  }

  public fun consumerGroup(consumerGroup: String) {
    it.property("consumerGroup", consumerGroup)
  }

  public fun subscribeTags(subscribeTags: String) {
    it.property("subscribeTags", subscribeTags)
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

  public fun producerGroup(producerGroup: String) {
    it.property("producerGroup", producerGroup)
  }

  public fun replyToConsumerGroup(replyToConsumerGroup: String) {
    it.property("replyToConsumerGroup", replyToConsumerGroup)
  }

  public fun replyToTopic(replyToTopic: String) {
    it.property("replyToTopic", replyToTopic)
  }

  public fun sendTag(sendTag: String) {
    it.property("sendTag", sendTag)
  }

  public fun waitForSendResult(waitForSendResult: String) {
    it.property("waitForSendResult", waitForSendResult)
  }

  public fun waitForSendResult(waitForSendResult: Boolean) {
    it.property("waitForSendResult", waitForSendResult.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun requestTimeoutCheckerIntervalMillis(requestTimeoutCheckerIntervalMillis: String) {
    it.property("requestTimeoutCheckerIntervalMillis", requestTimeoutCheckerIntervalMillis)
  }

  public fun requestTimeoutCheckerIntervalMillis(requestTimeoutCheckerIntervalMillis: Int) {
    it.property("requestTimeoutCheckerIntervalMillis",
        requestTimeoutCheckerIntervalMillis.toString())
  }

  public fun requestTimeoutMillis(requestTimeoutMillis: String) {
    it.property("requestTimeoutMillis", requestTimeoutMillis)
  }

  public fun requestTimeoutMillis(requestTimeoutMillis: Int) {
    it.property("requestTimeoutMillis", requestTimeoutMillis.toString())
  }

  public fun accessKey(accessKey: String) {
    it.property("accessKey", accessKey)
  }

  public fun secretKey(secretKey: String) {
    it.property("secretKey", secretKey)
  }
}
