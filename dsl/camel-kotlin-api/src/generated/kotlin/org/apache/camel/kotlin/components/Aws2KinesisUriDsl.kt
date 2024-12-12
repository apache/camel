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
 * Consume and produce records from and to AWS Kinesis Streams.
 */
public fun UriDsl.`aws2-kinesis`(i: Aws2KinesisUriDsl.() -> Unit) {
  Aws2KinesisUriDsl(this).apply(i)
}

@CamelDslMarker
public class Aws2KinesisUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("aws2-kinesis")
  }

  private var streamName: String = ""

  /**
   * Name of the stream
   */
  public fun streamName(streamName: String) {
    this.streamName = streamName
    it.url("$streamName")
  }

  /**
   * This option will set the CBOR_ENABLED property during the execution
   */
  public fun cborEnabled(cborEnabled: String) {
    it.property("cborEnabled", cborEnabled)
  }

  /**
   * This option will set the CBOR_ENABLED property during the execution
   */
  public fun cborEnabled(cborEnabled: Boolean) {
    it.property("cborEnabled", cborEnabled.toString())
  }

  /**
   * Set the need for overriding the endpoint. This option needs to be used in combination with
   * uriEndpointOverride option
   */
  public fun overrideEndpoint(overrideEndpoint: String) {
    it.property("overrideEndpoint", overrideEndpoint)
  }

  /**
   * Set the need for overriding the endpoint. This option needs to be used in combination with
   * uriEndpointOverride option
   */
  public fun overrideEndpoint(overrideEndpoint: Boolean) {
    it.property("overrideEndpoint", overrideEndpoint.toString())
  }

  /**
   * The region in which Kinesis Firehose client needs to work. When using this parameter, the
   * configuration will expect the lowercase name of the region (for example ap-east-1) You'll need to
   * use the name Region.EU_WEST_1.id()
   */
  public fun region(region: String) {
    it.property("region", region)
  }

  /**
   * Set the overriding uri endpoint. This option needs to be used in combination with
   * overrideEndpoint option
   */
  public fun uriEndpointOverride(uriEndpointOverride: String) {
    it.property("uriEndpointOverride", uriEndpointOverride)
  }

  /**
   * Defines where in the Kinesis stream to start getting records
   */
  public fun iteratorType(iteratorType: String) {
    it.property("iteratorType", iteratorType)
  }

  /**
   * Maximum number of records that will be fetched in each poll
   */
  public fun maxResultsPerRequest(maxResultsPerRequest: String) {
    it.property("maxResultsPerRequest", maxResultsPerRequest)
  }

  /**
   * Maximum number of records that will be fetched in each poll
   */
  public fun maxResultsPerRequest(maxResultsPerRequest: Int) {
    it.property("maxResultsPerRequest", maxResultsPerRequest.toString())
  }

  /**
   * The message timestamp to start polling from. Required if iteratorType is set to AT_TIMESTAMP
   */
  public fun messageTimestamp(messageTimestamp: String) {
    it.property("messageTimestamp", messageTimestamp)
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
   * The sequence number to start polling from. Required if iteratorType is set to
   * AFTER_SEQUENCE_NUMBER or AT_SEQUENCE_NUMBER
   */
  public fun sequenceNumber(sequenceNumber: String) {
    it.property("sequenceNumber", sequenceNumber)
  }

  /**
   * Define what will be the behavior in case of shard closed. Possible value are ignore, silent and
   * fail. In case of ignore a WARN message will be logged once and the consumer will not process new
   * messages until restarted,in case of silent there will be no logging and the consumer will not
   * process new messages until restarted,in case of fail a ReachedClosedStateException will be thrown
   */
  public fun shardClosed(shardClosed: String) {
    it.property("shardClosed", shardClosed)
  }

  /**
   * Defines which shardId in the Kinesis stream to get records from
   */
  public fun shardId(shardId: String) {
    it.property("shardId", shardId)
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
   * A pluggable org.apache.camel.PollingConsumerPollingStrategy allowing you to provide your custom
   * implementation to control error handling usually occurred during the poll operation before an
   * Exchange have been created and being routed in Camel.
   */
  public fun pollStrategy(pollStrategy: String) {
    it.property("pollStrategy", pollStrategy)
  }

  /**
   * The interval in milliseconds to wait between shard polling
   */
  public fun shardMonitorInterval(shardMonitorInterval: String) {
    it.property("shardMonitorInterval", shardMonitorInterval)
  }

  /**
   * The interval in milliseconds to wait between shard polling
   */
  public fun shardMonitorInterval(shardMonitorInterval: Int) {
    it.property("shardMonitorInterval", shardMonitorInterval.toString())
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
   * Amazon Kinesis client to use for all requests for this endpoint
   */
  public fun amazonKinesisClient(amazonKinesisClient: String) {
    it.property("amazonKinesisClient", amazonKinesisClient)
  }

  /**
   * If we want to a KinesisAsyncClient instance set it to true
   */
  public fun asyncClient(asyncClient: String) {
    it.property("asyncClient", asyncClient)
  }

  /**
   * If we want to a KinesisAsyncClient instance set it to true
   */
  public fun asyncClient(asyncClient: Boolean) {
    it.property("asyncClient", asyncClient.toString())
  }

  /**
   * If we want to a KCL Consumer, we can pass an instance of CloudWatchAsyncClient
   */
  public fun cloudWatchAsyncClient(cloudWatchAsyncClient: String) {
    it.property("cloudWatchAsyncClient", cloudWatchAsyncClient)
  }

  /**
   * If we want to a KCL Consumer, we can pass an instance of DynamoDbAsyncClient
   */
  public fun dynamoDbAsyncClient(dynamoDbAsyncClient: String) {
    it.property("dynamoDbAsyncClient", dynamoDbAsyncClient)
  }

  /**
   * If we want to use a KCL Consumer and disable the CloudWatch Metrics Export
   */
  public fun kclDisableCloudwatchMetricsExport(kclDisableCloudwatchMetricsExport: String) {
    it.property("kclDisableCloudwatchMetricsExport", kclDisableCloudwatchMetricsExport)
  }

  /**
   * If we want to use a KCL Consumer and disable the CloudWatch Metrics Export
   */
  public fun kclDisableCloudwatchMetricsExport(kclDisableCloudwatchMetricsExport: Boolean) {
    it.property("kclDisableCloudwatchMetricsExport", kclDisableCloudwatchMetricsExport.toString())
  }

  /**
   * If we want to a KCL Consumer set it to true
   */
  public fun useKclConsumers(useKclConsumers: String) {
    it.property("useKclConsumers", useKclConsumers)
  }

  /**
   * If we want to a KCL Consumer set it to true
   */
  public fun useKclConsumers(useKclConsumers: Boolean) {
    it.property("useKclConsumers", useKclConsumers.toString())
  }

  /**
   * To define a proxy host when instantiating the Kinesis client
   */
  public fun proxyHost(proxyHost: String) {
    it.property("proxyHost", proxyHost)
  }

  /**
   * To define a proxy port when instantiating the Kinesis client
   */
  public fun proxyPort(proxyPort: String) {
    it.property("proxyPort", proxyPort)
  }

  /**
   * To define a proxy port when instantiating the Kinesis client
   */
  public fun proxyPort(proxyPort: Int) {
    it.property("proxyPort", proxyPort.toString())
  }

  /**
   * To define a proxy protocol when instantiating the Kinesis client
   */
  public fun proxyProtocol(proxyProtocol: String) {
    it.property("proxyProtocol", proxyProtocol)
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
   * Milliseconds before the next poll.
   */
  public fun delay(delay: Int) {
    it.property("delay", delay.toString())
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
   * Amazon AWS Access Key
   */
  public fun accessKey(accessKey: String) {
    it.property("accessKey", accessKey)
  }

  /**
   * If using a profile credentials provider this parameter will set the profile name.
   */
  public fun profileCredentialsName(profileCredentialsName: String) {
    it.property("profileCredentialsName", profileCredentialsName)
  }

  /**
   * Amazon AWS Secret Key
   */
  public fun secretKey(secretKey: String) {
    it.property("secretKey", secretKey)
  }

  /**
   * Amazon AWS Session Token used when the user needs to assume a IAM role
   */
  public fun sessionToken(sessionToken: String) {
    it.property("sessionToken", sessionToken)
  }

  /**
   * If we want to trust all certificates in case of overriding the endpoint
   */
  public fun trustAllCertificates(trustAllCertificates: String) {
    it.property("trustAllCertificates", trustAllCertificates)
  }

  /**
   * If we want to trust all certificates in case of overriding the endpoint
   */
  public fun trustAllCertificates(trustAllCertificates: Boolean) {
    it.property("trustAllCertificates", trustAllCertificates.toString())
  }

  /**
   * Set whether the Kinesis client should expect to load credentials through a default credentials
   * provider or to expect static credentials to be passed in.
   */
  public fun useDefaultCredentialsProvider(useDefaultCredentialsProvider: String) {
    it.property("useDefaultCredentialsProvider", useDefaultCredentialsProvider)
  }

  /**
   * Set whether the Kinesis client should expect to load credentials through a default credentials
   * provider or to expect static credentials to be passed in.
   */
  public fun useDefaultCredentialsProvider(useDefaultCredentialsProvider: Boolean) {
    it.property("useDefaultCredentialsProvider", useDefaultCredentialsProvider.toString())
  }

  /**
   * Set whether the Kinesis client should expect to load credentials through a profile credentials
   * provider.
   */
  public fun useProfileCredentialsProvider(useProfileCredentialsProvider: String) {
    it.property("useProfileCredentialsProvider", useProfileCredentialsProvider)
  }

  /**
   * Set whether the Kinesis client should expect to load credentials through a profile credentials
   * provider.
   */
  public fun useProfileCredentialsProvider(useProfileCredentialsProvider: Boolean) {
    it.property("useProfileCredentialsProvider", useProfileCredentialsProvider.toString())
  }

  /**
   * Set whether the Kinesis client should expect to use Session Credentials. This is useful in
   * situation in which the user needs to assume a IAM role for doing operations in Kinesis.
   */
  public fun useSessionCredentials(useSessionCredentials: String) {
    it.property("useSessionCredentials", useSessionCredentials)
  }

  /**
   * Set whether the Kinesis client should expect to use Session Credentials. This is useful in
   * situation in which the user needs to assume a IAM role for doing operations in Kinesis.
   */
  public fun useSessionCredentials(useSessionCredentials: Boolean) {
    it.property("useSessionCredentials", useSessionCredentials.toString())
  }
}
