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
 * Extends the mock component by pulling messages from another endpoint on startup to set the
 * expected message bodies.
 */
public fun UriDsl.`dataset-test`(i: DatasetTestUriDsl.() -> Unit) {
  DatasetTestUriDsl(this).apply(i)
}

@CamelDslMarker
public class DatasetTestUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("dataset-test")
  }

  private var name: String = ""

  /**
   * Name of endpoint to lookup in the registry to use for polling messages used for testing
   */
  public fun name(name: String) {
    this.name = name
    it.url("$name")
  }

  /**
   * Whether the expected messages should arrive in the same order or can be in any order.
   */
  public fun anyOrder(anyOrder: String) {
    it.property("anyOrder", anyOrder)
  }

  /**
   * Whether the expected messages should arrive in the same order or can be in any order.
   */
  public fun anyOrder(anyOrder: Boolean) {
    it.property("anyOrder", anyOrder.toString())
  }

  /**
   * Sets a grace period after which the mock endpoint will re-assert to ensure the preliminary
   * assertion is still valid. This is used, for example, to assert that exactly a number of messages
   * arrive. For example, if the expected count was set to 5, then the assertion is satisfied when five
   * or more messages arrive. To ensure that exactly 5 messages arrive, then you would need to wait a
   * little period to ensure no further message arrives. This is what you can use this method for. By
   * default, this period is disabled.
   */
  public fun assertPeriod(assertPeriod: String) {
    it.property("assertPeriod", assertPeriod)
  }

  /**
   * The split delimiter to use when split is enabled. By default the delimiter is new line based.
   * The delimiter can be a regular expression.
   */
  public fun delimiter(delimiter: String) {
    it.property("delimiter", delimiter)
  }

  /**
   * Specifies the expected number of message exchanges that should be received by this endpoint.
   * Beware: If you want to expect that 0 messages, then take extra care, as 0 matches when the tests
   * starts, so you need to set a assert period time to let the test run for a while to make sure there
   * are still no messages arrived; for that use setAssertPeriod(long). An alternative is to use
   * NotifyBuilder, and use the notifier to know when Camel is done routing some messages, before you
   * call the assertIsSatisfied() method on the mocks. This allows you to not use a fixed assert
   * period, to speedup testing times. If you want to assert that exactly nth message arrives to this
   * mock endpoint, then see also the setAssertPeriod(long) method for further details.
   */
  public fun expectedCount(expectedCount: String) {
    it.property("expectedCount", expectedCount)
  }

  /**
   * Specifies the expected number of message exchanges that should be received by this endpoint.
   * Beware: If you want to expect that 0 messages, then take extra care, as 0 matches when the tests
   * starts, so you need to set a assert period time to let the test run for a while to make sure there
   * are still no messages arrived; for that use setAssertPeriod(long). An alternative is to use
   * NotifyBuilder, and use the notifier to know when Camel is done routing some messages, before you
   * call the assertIsSatisfied() method on the mocks. This allows you to not use a fixed assert
   * period, to speedup testing times. If you want to assert that exactly nth message arrives to this
   * mock endpoint, then see also the setAssertPeriod(long) method for further details.
   */
  public fun expectedCount(expectedCount: Int) {
    it.property("expectedCount", expectedCount.toString())
  }

  /**
   * Sets whether assertIsSatisfied() should fail fast at the first detected failed expectation
   * while it may otherwise wait for all expected messages to arrive before performing expectations
   * verifications. Is by default true. Set to false to use behavior as in Camel 2.x.
   */
  public fun failFast(failFast: String) {
    it.property("failFast", failFast)
  }

  /**
   * Sets whether assertIsSatisfied() should fail fast at the first detected failed expectation
   * while it may otherwise wait for all expected messages to arrive before performing expectations
   * verifications. Is by default true. Set to false to use behavior as in Camel 2.x.
   */
  public fun failFast(failFast: Boolean) {
    it.property("failFast", failFast.toString())
  }

  /**
   * To turn on logging when the mock receives an incoming message. This will log only one time at
   * INFO level for the incoming message. For more detailed logging then set the logger to DEBUG level
   * for the org.apache.camel.component.mock.MockEndpoint class.
   */
  public fun log(log: String) {
    it.property("log", log)
  }

  /**
   * To turn on logging when the mock receives an incoming message. This will log only one time at
   * INFO level for the incoming message. For more detailed logging then set the logger to DEBUG level
   * for the org.apache.camel.component.mock.MockEndpoint class.
   */
  public fun log(log: Boolean) {
    it.property("log", log.toString())
  }

  /**
   * A number that is used to turn on throughput logging based on groups of the size.
   */
  public fun reportGroup(reportGroup: String) {
    it.property("reportGroup", reportGroup)
  }

  /**
   * A number that is used to turn on throughput logging based on groups of the size.
   */
  public fun reportGroup(reportGroup: Int) {
    it.property("reportGroup", reportGroup.toString())
  }

  /**
   * Sets the minimum expected amount of time (in millis) the assertIsSatisfied() will wait on a
   * latch until it is satisfied
   */
  public fun resultMinimumWaitTime(resultMinimumWaitTime: String) {
    it.property("resultMinimumWaitTime", resultMinimumWaitTime)
  }

  /**
   * Sets the maximum amount of time (in millis) the assertIsSatisfied() will wait on a latch until
   * it is satisfied
   */
  public fun resultWaitTime(resultWaitTime: String) {
    it.property("resultWaitTime", resultWaitTime)
  }

  /**
   * Specifies to only retain the first nth number of received Exchanges. This is used when testing
   * with big data, to reduce memory consumption by not storing copies of every Exchange this mock
   * endpoint receives. Important: When using this limitation, then the getReceivedCounter() will still
   * return the actual number of received Exchanges. For example if we have received 5000 Exchanges,
   * and have configured to only retain the first 10 Exchanges, then the getReceivedCounter() will
   * still return 5000 but there is only the first 10 Exchanges in the getExchanges() and
   * getReceivedExchanges() methods. When using this method, then some of the other expectation methods
   * is not supported, for example the expectedBodiesReceived(Object...) sets a expectation on the
   * first number of bodies received. You can configure both setRetainFirst(int) and setRetainLast(int)
   * methods, to limit both the first and last received.
   */
  public fun retainFirst(retainFirst: String) {
    it.property("retainFirst", retainFirst)
  }

  /**
   * Specifies to only retain the first nth number of received Exchanges. This is used when testing
   * with big data, to reduce memory consumption by not storing copies of every Exchange this mock
   * endpoint receives. Important: When using this limitation, then the getReceivedCounter() will still
   * return the actual number of received Exchanges. For example if we have received 5000 Exchanges,
   * and have configured to only retain the first 10 Exchanges, then the getReceivedCounter() will
   * still return 5000 but there is only the first 10 Exchanges in the getExchanges() and
   * getReceivedExchanges() methods. When using this method, then some of the other expectation methods
   * is not supported, for example the expectedBodiesReceived(Object...) sets a expectation on the
   * first number of bodies received. You can configure both setRetainFirst(int) and setRetainLast(int)
   * methods, to limit both the first and last received.
   */
  public fun retainFirst(retainFirst: Int) {
    it.property("retainFirst", retainFirst.toString())
  }

  /**
   * Specifies to only retain the last nth number of received Exchanges. This is used when testing
   * with big data, to reduce memory consumption by not storing copies of every Exchange this mock
   * endpoint receives. Important: When using this limitation, then the getReceivedCounter() will still
   * return the actual number of received Exchanges. For example if we have received 5000 Exchanges,
   * and have configured to only retain the last 20 Exchanges, then the getReceivedCounter() will still
   * return 5000 but there is only the last 20 Exchanges in the getExchanges() and
   * getReceivedExchanges() methods. When using this method, then some of the other expectation methods
   * is not supported, for example the expectedBodiesReceived(Object...) sets a expectation on the
   * first number of bodies received. You can configure both setRetainFirst(int) and setRetainLast(int)
   * methods, to limit both the first and last received.
   */
  public fun retainLast(retainLast: String) {
    it.property("retainLast", retainLast)
  }

  /**
   * Specifies to only retain the last nth number of received Exchanges. This is used when testing
   * with big data, to reduce memory consumption by not storing copies of every Exchange this mock
   * endpoint receives. Important: When using this limitation, then the getReceivedCounter() will still
   * return the actual number of received Exchanges. For example if we have received 5000 Exchanges,
   * and have configured to only retain the last 20 Exchanges, then the getReceivedCounter() will still
   * return 5000 but there is only the last 20 Exchanges in the getExchanges() and
   * getReceivedExchanges() methods. When using this method, then some of the other expectation methods
   * is not supported, for example the expectedBodiesReceived(Object...) sets a expectation on the
   * first number of bodies received. You can configure both setRetainFirst(int) and setRetainLast(int)
   * methods, to limit both the first and last received.
   */
  public fun retainLast(retainLast: Int) {
    it.property("retainLast", retainLast.toString())
  }

  /**
   * Allows a sleep to be specified to wait to check that this endpoint really is empty when
   * expectedMessageCount(int) is called with zero
   */
  public fun sleepForEmptyTest(sleepForEmptyTest: String) {
    it.property("sleepForEmptyTest", sleepForEmptyTest)
  }

  /**
   * If enabled the messages loaded from the test endpoint will be split using new line delimiters
   * so each line is an expected message. For example to use a file endpoint to load a file where each
   * line is an expected message.
   */
  public fun split(split: String) {
    it.property("split", split)
  }

  /**
   * If enabled the messages loaded from the test endpoint will be split using new line delimiters
   * so each line is an expected message. For example to use a file endpoint to load a file where each
   * line is an expected message.
   */
  public fun split(split: Boolean) {
    it.property("split", split.toString())
  }

  /**
   * The timeout to use when polling for message bodies from the URI
   */
  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  /**
   * Sets whether to make a deep copy of the incoming Exchange when received at this mock endpoint.
   * Is by default true.
   */
  public fun copyOnExchange(copyOnExchange: String) {
    it.property("copyOnExchange", copyOnExchange)
  }

  /**
   * Sets whether to make a deep copy of the incoming Exchange when received at this mock endpoint.
   * Is by default true.
   */
  public fun copyOnExchange(copyOnExchange: Boolean) {
    it.property("copyOnExchange", copyOnExchange.toString())
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
   * Maximum number of messages to keep in memory available for browsing. Use 0 for unlimited.
   */
  public fun browseLimit(browseLimit: String) {
    it.property("browseLimit", browseLimit)
  }

  /**
   * Maximum number of messages to keep in memory available for browsing. Use 0 for unlimited.
   */
  public fun browseLimit(browseLimit: Int) {
    it.property("browseLimit", browseLimit.toString())
  }
}
