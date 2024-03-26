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

/**
 * Send tweets and receive tweets from user's timeline.
 */
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

  /**
   * The timeline type to produce/consume.
   */
  public fun timelineType(timelineType: String) {
    this.timelineType = timelineType
    it.url("$timelineType")
  }

  /**
   * The list name when using timelineType=list
   */
  public fun list(list: String) {
    it.property("list", list)
  }

  /**
   * The username when using timelineType=user
   */
  public fun user(user: String) {
    it.property("user", user)
  }

  /**
   * If the polling consumer did not poll any files, you can enable this option to send an empty
   * message (no body) instead.
   */
  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: String) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle)
  }

  /**
   * If the polling consumer did not poll any files, you can enable this option to send an empty
   * message (no body) instead.
   */
  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: Boolean) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle.toString())
  }

  /**
   * Endpoint type to use.
   */
  public fun type(type: String) {
    it.property("type", type)
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
   * Used by the geography search, to search by radius using the configured metrics. The unit can
   * either be mi for miles, or km for kilometers. You need to configure all the following options:
   * longitude, latitude, radius, and distanceMetric.
   */
  public fun distanceMetric(distanceMetric: String) {
    it.property("distanceMetric", distanceMetric)
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
   * Used for enabling full text from twitter (eg receive tweets that contains more than 140
   * characters).
   */
  public fun extendedMode(extendedMode: String) {
    it.property("extendedMode", extendedMode)
  }

  /**
   * Used for enabling full text from twitter (eg receive tweets that contains more than 140
   * characters).
   */
  public fun extendedMode(extendedMode: Boolean) {
    it.property("extendedMode", extendedMode.toString())
  }

  /**
   * Used by the geography search to search by latitude. You need to configure all the following
   * options: longitude, latitude, radius, and distanceMetric.
   */
  public fun latitude(latitude: String) {
    it.property("latitude", latitude)
  }

  /**
   * Used by the geography search to search by latitude. You need to configure all the following
   * options: longitude, latitude, radius, and distanceMetric.
   */
  public fun latitude(latitude: Double) {
    it.property("latitude", latitude.toString())
  }

  /**
   * Bounding boxes, created by pairs of lat/lons. Can be used for filter. A pair is defined as
   * lat,lon. And multiple pairs can be separated by semicolon.
   */
  public fun locations(locations: String) {
    it.property("locations", locations)
  }

  /**
   * Used by the geography search to search by longitude. You need to configure all the following
   * options: longitude, latitude, radius, and distanceMetric.
   */
  public fun longitude(longitude: String) {
    it.property("longitude", longitude)
  }

  /**
   * Used by the geography search to search by longitude. You need to configure all the following
   * options: longitude, latitude, radius, and distanceMetric.
   */
  public fun longitude(longitude: Double) {
    it.property("longitude", longitude.toString())
  }

  /**
   * A pluggable org.apache.camel.PollingConsumerPollingStrategy allowing you to provide your custom
   * implementation to control error handling usually occurred during the poll operation before an
   * Exchange have been created and being routed in Camel.
   */
  public fun pollStrategy(pollStrategy: String) {
    it.property("pollStrategy", pollStrategy)
  }

  /**
   * Used by the geography search to search by radius. You need to configure all the following
   * options: longitude, latitude, radius, and distanceMetric.
   */
  public fun radius(radius: String) {
    it.property("radius", radius)
  }

  /**
   * Used by the geography search to search by radius. You need to configure all the following
   * options: longitude, latitude, radius, and distanceMetric.
   */
  public fun radius(radius: Double) {
    it.property("radius", radius.toString())
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
   * Limiting number of results per page.
   */
  public fun count(count: String) {
    it.property("count", count)
  }

  /**
   * Limiting number of results per page.
   */
  public fun count(count: Int) {
    it.property("count", count.toString())
  }

  /**
   * Filter out old tweets, that has previously been polled. This state is stored in memory only,
   * and based on last tweet id.
   */
  public fun filterOld(filterOld: String) {
    it.property("filterOld", filterOld)
  }

  /**
   * Filter out old tweets, that has previously been polled. This state is stored in memory only,
   * and based on last tweet id.
   */
  public fun filterOld(filterOld: Boolean) {
    it.property("filterOld", filterOld.toString())
  }

  /**
   * The lang string ISO_639-1 which will be used for searching
   */
  public fun lang(lang: String) {
    it.property("lang", lang)
  }

  /**
   * The number of pages result which you want camel-twitter to consume.
   */
  public fun numberOfPages(numberOfPages: String) {
    it.property("numberOfPages", numberOfPages)
  }

  /**
   * The number of pages result which you want camel-twitter to consume.
   */
  public fun numberOfPages(numberOfPages: Int) {
    it.property("numberOfPages", numberOfPages.toString())
  }

  /**
   * The last tweet id which will be used for pulling the tweets. It is useful when the camel route
   * is restarted after a long running.
   */
  public fun sinceId(sinceId: String) {
    it.property("sinceId", sinceId)
  }

  /**
   * The last tweet id which will be used for pulling the tweets. It is useful when the camel route
   * is restarted after a long running.
   */
  public fun sinceId(sinceId: Int) {
    it.property("sinceId", sinceId.toString())
  }

  /**
   * To filter by user ids for filter. Multiple values can be separated by comma.
   */
  public fun userIds(userIds: String) {
    it.property("userIds", userIds)
  }

  /**
   * The http proxy host which can be used for the camel-twitter. Can also be configured on the
   * TwitterComponent level instead.
   */
  public fun httpProxyHost(httpProxyHost: String) {
    it.property("httpProxyHost", httpProxyHost)
  }

  /**
   * The http proxy password which can be used for the camel-twitter. Can also be configured on the
   * TwitterComponent level instead.
   */
  public fun httpProxyPassword(httpProxyPassword: String) {
    it.property("httpProxyPassword", httpProxyPassword)
  }

  /**
   * The http proxy port which can be used for the camel-twitter. Can also be configured on the
   * TwitterComponent level instead.
   */
  public fun httpProxyPort(httpProxyPort: String) {
    it.property("httpProxyPort", httpProxyPort)
  }

  /**
   * The http proxy port which can be used for the camel-twitter. Can also be configured on the
   * TwitterComponent level instead.
   */
  public fun httpProxyPort(httpProxyPort: Int) {
    it.property("httpProxyPort", httpProxyPort.toString())
  }

  /**
   * The http proxy user which can be used for the camel-twitter. Can also be configured on the
   * TwitterComponent level instead.
   */
  public fun httpProxyUser(httpProxyUser: String) {
    it.property("httpProxyUser", httpProxyUser)
  }

  /**
   * The number of subsequent error polls (failed due some error) that should happen before the
   * backoffMultipler should kick-in.
   */
  public fun backoffErrorThreshold(backoffErrorThreshold: String) {
    it.property("backoffErrorThreshold", backoffErrorThreshold)
  }

  /**
   * The number of subsequent error polls (failed due some error) that should happen before the
   * backoffMultipler should kick-in.
   */
  public fun backoffErrorThreshold(backoffErrorThreshold: Int) {
    it.property("backoffErrorThreshold", backoffErrorThreshold.toString())
  }

  /**
   * The number of subsequent idle polls that should happen before the backoffMultipler should
   * kick-in.
   */
  public fun backoffIdleThreshold(backoffIdleThreshold: String) {
    it.property("backoffIdleThreshold", backoffIdleThreshold)
  }

  /**
   * The number of subsequent idle polls that should happen before the backoffMultipler should
   * kick-in.
   */
  public fun backoffIdleThreshold(backoffIdleThreshold: Int) {
    it.property("backoffIdleThreshold", backoffIdleThreshold.toString())
  }

  /**
   * To let the scheduled polling consumer backoff if there has been a number of subsequent
   * idles/errors in a row. The multiplier is then the number of polls that will be skipped before the
   * next actual attempt is happening again. When this option is in use then backoffIdleThreshold
   * and/or backoffErrorThreshold must also be configured.
   */
  public fun backoffMultiplier(backoffMultiplier: String) {
    it.property("backoffMultiplier", backoffMultiplier)
  }

  /**
   * To let the scheduled polling consumer backoff if there has been a number of subsequent
   * idles/errors in a row. The multiplier is then the number of polls that will be skipped before the
   * next actual attempt is happening again. When this option is in use then backoffIdleThreshold
   * and/or backoffErrorThreshold must also be configured.
   */
  public fun backoffMultiplier(backoffMultiplier: Int) {
    it.property("backoffMultiplier", backoffMultiplier.toString())
  }

  /**
   * Milliseconds before the next poll.
   */
  public fun delay(delay: String) {
    it.property("delay", delay)
  }

  /**
   * If greedy is enabled, then the ScheduledPollConsumer will run immediately again, if the
   * previous run polled 1 or more messages.
   */
  public fun greedy(greedy: String) {
    it.property("greedy", greedy)
  }

  /**
   * If greedy is enabled, then the ScheduledPollConsumer will run immediately again, if the
   * previous run polled 1 or more messages.
   */
  public fun greedy(greedy: Boolean) {
    it.property("greedy", greedy.toString())
  }

  /**
   * Milliseconds before the first poll starts.
   */
  public fun initialDelay(initialDelay: String) {
    it.property("initialDelay", initialDelay)
  }

  /**
   * Milliseconds before the first poll starts.
   */
  public fun initialDelay(initialDelay: Int) {
    it.property("initialDelay", initialDelay.toString())
  }

  /**
   * Specifies a maximum limit of number of fires. So if you set it to 1, the scheduler will only
   * fire once. If you set it to 5, it will only fire five times. A value of zero or negative means
   * fire forever.
   */
  public fun repeatCount(repeatCount: String) {
    it.property("repeatCount", repeatCount)
  }

  /**
   * Specifies a maximum limit of number of fires. So if you set it to 1, the scheduler will only
   * fire once. If you set it to 5, it will only fire five times. A value of zero or negative means
   * fire forever.
   */
  public fun repeatCount(repeatCount: Int) {
    it.property("repeatCount", repeatCount.toString())
  }

  /**
   * The consumer logs a start/complete log line when it polls. This option allows you to configure
   * the logging level for that.
   */
  public fun runLoggingLevel(runLoggingLevel: String) {
    it.property("runLoggingLevel", runLoggingLevel)
  }

  /**
   * Allows for configuring a custom/shared thread pool to use for the consumer. By default each
   * consumer has its own single threaded thread pool.
   */
  public fun scheduledExecutorService(scheduledExecutorService: String) {
    it.property("scheduledExecutorService", scheduledExecutorService)
  }

  /**
   * To use a cron scheduler from either camel-spring or camel-quartz component. Use value spring or
   * quartz for built in scheduler
   */
  public fun scheduler(scheduler: String) {
    it.property("scheduler", scheduler)
  }

  /**
   * To configure additional properties when using a custom scheduler or any of the Quartz, Spring
   * based scheduler.
   */
  public fun schedulerProperties(schedulerProperties: String) {
    it.property("schedulerProperties", schedulerProperties)
  }

  /**
   * Whether the scheduler should be auto started.
   */
  public fun startScheduler(startScheduler: String) {
    it.property("startScheduler", startScheduler)
  }

  /**
   * Whether the scheduler should be auto started.
   */
  public fun startScheduler(startScheduler: Boolean) {
    it.property("startScheduler", startScheduler.toString())
  }

  /**
   * Time unit for initialDelay and delay options.
   */
  public fun timeUnit(timeUnit: String) {
    it.property("timeUnit", timeUnit)
  }

  /**
   * Controls if fixed delay or fixed rate is used. See ScheduledExecutorService in JDK for details.
   */
  public fun useFixedDelay(useFixedDelay: String) {
    it.property("useFixedDelay", useFixedDelay)
  }

  /**
   * Controls if fixed delay or fixed rate is used. See ScheduledExecutorService in JDK for details.
   */
  public fun useFixedDelay(useFixedDelay: Boolean) {
    it.property("useFixedDelay", useFixedDelay.toString())
  }

  /**
   * The access token. Can also be configured on the TwitterComponent level instead.
   */
  public fun accessToken(accessToken: String) {
    it.property("accessToken", accessToken)
  }

  /**
   * The access secret. Can also be configured on the TwitterComponent level instead.
   */
  public fun accessTokenSecret(accessTokenSecret: String) {
    it.property("accessTokenSecret", accessTokenSecret)
  }

  /**
   * The consumer key. Can also be configured on the TwitterComponent level instead.
   */
  public fun consumerKey(consumerKey: String) {
    it.property("consumerKey", consumerKey)
  }

  /**
   * The consumer secret. Can also be configured on the TwitterComponent level instead.
   */
  public fun consumerSecret(consumerSecret: String) {
    it.property("consumerSecret", consumerSecret)
  }

  /**
   * Sorts by id, so the oldest are first, and newest last.
   */
  public fun sortById(sortById: String) {
    it.property("sortById", sortById)
  }

  /**
   * Sorts by id, so the oldest are first, and newest last.
   */
  public fun sortById(sortById: Boolean) {
    it.property("sortById", sortById.toString())
  }
}
