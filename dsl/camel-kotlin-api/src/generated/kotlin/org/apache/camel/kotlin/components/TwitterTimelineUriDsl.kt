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
import kotlin.Double
import kotlin.Int
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

public fun UriDsl.`twitter-timeline`(i: TwitterTimelineUriDsl.() -> Unit) {
  TwitterTimelineUriDsl(this).apply(i)
}

@CamelDslMarker
public class TwitterTimelineUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("twitter-timeline")
  }

  private var timelineType: String = ""

  public fun timelineType(timelineType: String) {
    this.timelineType = timelineType
    it.url("$timelineType")
  }

  public fun list(list: String) {
    it.property("list", list)
  }

  public fun user(user: String) {
    it.property("user", user)
  }

  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: String) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle)
  }

  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: Boolean) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle.toString())
  }

  public fun type(type: String) {
    it.property("type", type)
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
  }

  public fun distanceMetric(distanceMetric: String) {
    it.property("distanceMetric", distanceMetric)
  }

  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
  }

  public fun extendedMode(extendedMode: String) {
    it.property("extendedMode", extendedMode)
  }

  public fun extendedMode(extendedMode: Boolean) {
    it.property("extendedMode", extendedMode.toString())
  }

  public fun latitude(latitude: String) {
    it.property("latitude", latitude)
  }

  public fun latitude(latitude: Double) {
    it.property("latitude", latitude.toString())
  }

  public fun locations(locations: String) {
    it.property("locations", locations)
  }

  public fun longitude(longitude: String) {
    it.property("longitude", longitude)
  }

  public fun longitude(longitude: Double) {
    it.property("longitude", longitude.toString())
  }

  public fun pollStrategy(pollStrategy: String) {
    it.property("pollStrategy", pollStrategy)
  }

  public fun radius(radius: String) {
    it.property("radius", radius)
  }

  public fun radius(radius: Double) {
    it.property("radius", radius.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun count(count: String) {
    it.property("count", count)
  }

  public fun count(count: Int) {
    it.property("count", count.toString())
  }

  public fun filterOld(filterOld: String) {
    it.property("filterOld", filterOld)
  }

  public fun filterOld(filterOld: Boolean) {
    it.property("filterOld", filterOld.toString())
  }

  public fun lang(lang: String) {
    it.property("lang", lang)
  }

  public fun numberOfPages(numberOfPages: String) {
    it.property("numberOfPages", numberOfPages)
  }

  public fun numberOfPages(numberOfPages: Int) {
    it.property("numberOfPages", numberOfPages.toString())
  }

  public fun sinceId(sinceId: String) {
    it.property("sinceId", sinceId)
  }

  public fun sinceId(sinceId: Int) {
    it.property("sinceId", sinceId.toString())
  }

  public fun userIds(userIds: String) {
    it.property("userIds", userIds)
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

  public fun httpProxyUser(httpProxyUser: String) {
    it.property("httpProxyUser", httpProxyUser)
  }

  public fun backoffErrorThreshold(backoffErrorThreshold: String) {
    it.property("backoffErrorThreshold", backoffErrorThreshold)
  }

  public fun backoffErrorThreshold(backoffErrorThreshold: Int) {
    it.property("backoffErrorThreshold", backoffErrorThreshold.toString())
  }

  public fun backoffIdleThreshold(backoffIdleThreshold: String) {
    it.property("backoffIdleThreshold", backoffIdleThreshold)
  }

  public fun backoffIdleThreshold(backoffIdleThreshold: Int) {
    it.property("backoffIdleThreshold", backoffIdleThreshold.toString())
  }

  public fun backoffMultiplier(backoffMultiplier: String) {
    it.property("backoffMultiplier", backoffMultiplier)
  }

  public fun backoffMultiplier(backoffMultiplier: Int) {
    it.property("backoffMultiplier", backoffMultiplier.toString())
  }

  public fun delay(delay: String) {
    it.property("delay", delay)
  }

  public fun greedy(greedy: String) {
    it.property("greedy", greedy)
  }

  public fun greedy(greedy: Boolean) {
    it.property("greedy", greedy.toString())
  }

  public fun initialDelay(initialDelay: String) {
    it.property("initialDelay", initialDelay)
  }

  public fun initialDelay(initialDelay: Int) {
    it.property("initialDelay", initialDelay.toString())
  }

  public fun repeatCount(repeatCount: String) {
    it.property("repeatCount", repeatCount)
  }

  public fun repeatCount(repeatCount: Int) {
    it.property("repeatCount", repeatCount.toString())
  }

  public fun runLoggingLevel(runLoggingLevel: String) {
    it.property("runLoggingLevel", runLoggingLevel)
  }

  public fun scheduledExecutorService(scheduledExecutorService: String) {
    it.property("scheduledExecutorService", scheduledExecutorService)
  }

  public fun scheduler(scheduler: String) {
    it.property("scheduler", scheduler)
  }

  public fun schedulerProperties(schedulerProperties: String) {
    it.property("schedulerProperties", schedulerProperties)
  }

  public fun startScheduler(startScheduler: String) {
    it.property("startScheduler", startScheduler)
  }

  public fun startScheduler(startScheduler: Boolean) {
    it.property("startScheduler", startScheduler.toString())
  }

  public fun timeUnit(timeUnit: String) {
    it.property("timeUnit", timeUnit)
  }

  public fun useFixedDelay(useFixedDelay: String) {
    it.property("useFixedDelay", useFixedDelay)
  }

  public fun useFixedDelay(useFixedDelay: Boolean) {
    it.property("useFixedDelay", useFixedDelay.toString())
  }

  public fun accessToken(accessToken: String) {
    it.property("accessToken", accessToken)
  }

  public fun accessTokenSecret(accessTokenSecret: String) {
    it.property("accessTokenSecret", accessTokenSecret)
  }

  public fun consumerKey(consumerKey: String) {
    it.property("consumerKey", consumerKey)
  }

  public fun consumerSecret(consumerSecret: String) {
    it.property("consumerSecret", consumerSecret)
  }

  public fun sortById(sortById: String) {
    it.property("sortById", sortById)
  }

  public fun sortById(sortById: Boolean) {
    it.property("sortById", sortById.toString())
  }
}
