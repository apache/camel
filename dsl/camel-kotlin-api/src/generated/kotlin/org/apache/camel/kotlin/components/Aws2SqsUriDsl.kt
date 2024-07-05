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
 * Send and receive messages to/from AWS SQS.
 */
public fun UriDsl.`aws2-sqs`(i: Aws2SqsUriDsl.() -> Unit) {
  Aws2SqsUriDsl(this).apply(i)
}

@CamelDslMarker
public class Aws2SqsUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("aws2-sqs")
  }

  private var queueNameOrArn: String = ""

  /**
   * Queue name or ARN
   */
  public fun queueNameOrArn(queueNameOrArn: String) {
    this.queueNameOrArn = queueNameOrArn
    it.url("$queueNameOrArn")
  }

  /**
   * The hostname of the Amazon AWS cloud.
   */
  public fun amazonAWSHost(amazonAWSHost: String) {
    it.property("amazonAWSHost", amazonAWSHost)
  }

  /**
   * Setting the auto-creation of the queue
   */
  public fun autoCreateQueue(autoCreateQueue: String) {
    it.property("autoCreateQueue", autoCreateQueue)
  }

  /**
   * Setting the auto-creation of the queue
   */
  public fun autoCreateQueue(autoCreateQueue: Boolean) {
    it.property("autoCreateQueue", autoCreateQueue.toString())
  }

  /**
   * To use a custom HeaderFilterStrategy to map headers to/from Camel.
   */
  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
  }

  /**
   * Set the need for overriding the endpoint. This option needs to be used in combination with the
   * uriEndpointOverride option
   */
  public fun overrideEndpoint(overrideEndpoint: String) {
    it.property("overrideEndpoint", overrideEndpoint)
  }

  /**
   * Set the need for overriding the endpoint. This option needs to be used in combination with the
   * uriEndpointOverride option
   */
  public fun overrideEndpoint(overrideEndpoint: Boolean) {
    it.property("overrideEndpoint", overrideEndpoint.toString())
  }

  /**
   * The underlying protocol used to communicate with SQS
   */
  public fun protocol(protocol: String) {
    it.property("protocol", protocol)
  }

  /**
   * Specify the queue owner aws account id when you need to connect the queue with a different
   * account owner.
   */
  public fun queueOwnerAWSAccountId(queueOwnerAWSAccountId: String) {
    it.property("queueOwnerAWSAccountId", queueOwnerAWSAccountId)
  }

  /**
   * The region in which SQS client needs to work. When using this parameter, the configuration will
   * expect the lowercase name of the region (for example, ap-east-1) You'll need to use the name
   * Region.EU_WEST_1.id()
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
   * A list of attribute names to receive when consuming. Multiple names can be separated by comma.
   */
  public fun attributeNames(attributeNames: String) {
    it.property("attributeNames", attributeNames)
  }

  /**
   * Allows you to use multiple threads to poll the sqs queue to increase throughput
   */
  public fun concurrentConsumers(concurrentConsumers: String) {
    it.property("concurrentConsumers", concurrentConsumers)
  }

  /**
   * Allows you to use multiple threads to poll the sqs queue to increase throughput
   */
  public fun concurrentConsumers(concurrentConsumers: Int) {
    it.property("concurrentConsumers", concurrentConsumers.toString())
  }

  /**
   * The default visibility timeout (in seconds)
   */
  public fun defaultVisibilityTimeout(defaultVisibilityTimeout: String) {
    it.property("defaultVisibilityTimeout", defaultVisibilityTimeout)
  }

  /**
   * The default visibility timeout (in seconds)
   */
  public fun defaultVisibilityTimeout(defaultVisibilityTimeout: Int) {
    it.property("defaultVisibilityTimeout", defaultVisibilityTimeout.toString())
  }

  /**
   * Delete message from SQS after it has been read
   */
  public fun deleteAfterRead(deleteAfterRead: String) {
    it.property("deleteAfterRead", deleteAfterRead)
  }

  /**
   * Delete message from SQS after it has been read
   */
  public fun deleteAfterRead(deleteAfterRead: Boolean) {
    it.property("deleteAfterRead", deleteAfterRead.toString())
  }

  /**
   * Whether to send the DeleteMessage to the SQS queue if the exchange has property with key
   * Sqs2Constants#SQS_DELETE_FILTERED (CamelAwsSqsDeleteFiltered) set to true.
   */
  public fun deleteIfFiltered(deleteIfFiltered: String) {
    it.property("deleteIfFiltered", deleteIfFiltered)
  }

  /**
   * Whether to send the DeleteMessage to the SQS queue if the exchange has property with key
   * Sqs2Constants#SQS_DELETE_FILTERED (CamelAwsSqsDeleteFiltered) set to true.
   */
  public fun deleteIfFiltered(deleteIfFiltered: Boolean) {
    it.property("deleteIfFiltered", deleteIfFiltered.toString())
  }

  /**
   * If enabled, then a scheduled background task will keep extending the message visibility on SQS.
   * This is needed if it takes a long time to process the message. If set to true
   * defaultVisibilityTimeout must be set. See details at Amazon docs.
   */
  public fun extendMessageVisibility(extendMessageVisibility: String) {
    it.property("extendMessageVisibility", extendMessageVisibility)
  }

  /**
   * If enabled, then a scheduled background task will keep extending the message visibility on SQS.
   * This is needed if it takes a long time to process the message. If set to true
   * defaultVisibilityTimeout must be set. See details at Amazon docs.
   */
  public fun extendMessageVisibility(extendMessageVisibility: Boolean) {
    it.property("extendMessageVisibility", extendMessageVisibility.toString())
  }

  /**
   * The length of time, in seconds, for which Amazon SQS can reuse a data key to encrypt or decrypt
   * messages before calling AWS KMS again. An integer representing seconds, between 60 seconds (1
   * minute) and 86,400 seconds (24 hours). Default: 300 (5 minutes).
   */
  public fun kmsDataKeyReusePeriodSeconds(kmsDataKeyReusePeriodSeconds: String) {
    it.property("kmsDataKeyReusePeriodSeconds", kmsDataKeyReusePeriodSeconds)
  }

  /**
   * The length of time, in seconds, for which Amazon SQS can reuse a data key to encrypt or decrypt
   * messages before calling AWS KMS again. An integer representing seconds, between 60 seconds (1
   * minute) and 86,400 seconds (24 hours). Default: 300 (5 minutes).
   */
  public fun kmsDataKeyReusePeriodSeconds(kmsDataKeyReusePeriodSeconds: Int) {
    it.property("kmsDataKeyReusePeriodSeconds", kmsDataKeyReusePeriodSeconds.toString())
  }

  /**
   * The ID of an AWS-managed customer master key (CMK) for Amazon SQS or a custom CMK.
   */
  public fun kmsMasterKeyId(kmsMasterKeyId: String) {
    it.property("kmsMasterKeyId", kmsMasterKeyId)
  }

  /**
   * Gets the maximum number of messages as a limit to poll at each polling. Is default unlimited,
   * but use 0 or negative number to disable it as unlimited.
   */
  public fun maxMessagesPerPoll(maxMessagesPerPoll: String) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll)
  }

  /**
   * Gets the maximum number of messages as a limit to poll at each polling. Is default unlimited,
   * but use 0 or negative number to disable it as unlimited.
   */
  public fun maxMessagesPerPoll(maxMessagesPerPoll: Int) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll.toString())
  }

  /**
   * A list of message attribute names to receive when consuming. Multiple names can be separated by
   * comma.
   */
  public fun messageAttributeNames(messageAttributeNames: String) {
    it.property("messageAttributeNames", messageAttributeNames)
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
   * Define if Server Side Encryption is enabled or not on the queue
   */
  public fun serverSideEncryptionEnabled(serverSideEncryptionEnabled: String) {
    it.property("serverSideEncryptionEnabled", serverSideEncryptionEnabled)
  }

  /**
   * Define if Server Side Encryption is enabled or not on the queue
   */
  public fun serverSideEncryptionEnabled(serverSideEncryptionEnabled: Boolean) {
    it.property("serverSideEncryptionEnabled", serverSideEncryptionEnabled.toString())
  }

  /**
   * The duration (in seconds) that the received messages are hidden from subsequent retrieve
   * requests after being retrieved by a ReceiveMessage request to set in the
   * com.amazonaws.services.sqs.model.SetQueueAttributesRequest. This only makes sense if it's
   * different from defaultVisibilityTimeout. It changes the queue visibility timeout attribute
   * permanently.
   */
  public fun visibilityTimeout(visibilityTimeout: String) {
    it.property("visibilityTimeout", visibilityTimeout)
  }

  /**
   * The duration (in seconds) that the received messages are hidden from subsequent retrieve
   * requests after being retrieved by a ReceiveMessage request to set in the
   * com.amazonaws.services.sqs.model.SetQueueAttributesRequest. This only makes sense if it's
   * different from defaultVisibilityTimeout. It changes the queue visibility timeout attribute
   * permanently.
   */
  public fun visibilityTimeout(visibilityTimeout: Int) {
    it.property("visibilityTimeout", visibilityTimeout.toString())
  }

  /**
   * Duration in seconds (0 to 20) that the ReceiveMessage action call will wait until a message is
   * in the queue to include in the response.
   */
  public fun waitTimeSeconds(waitTimeSeconds: String) {
    it.property("waitTimeSeconds", waitTimeSeconds)
  }

  /**
   * Duration in seconds (0 to 20) that the ReceiveMessage action call will wait until a message is
   * in the queue to include in the response.
   */
  public fun waitTimeSeconds(waitTimeSeconds: Int) {
    it.property("waitTimeSeconds", waitTimeSeconds.toString())
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
   * A pluggable in-progress repository org.apache.camel.spi.IdempotentRepository. The in-progress
   * repository is used to account the current in progress files being consumed. By default a memory
   * based repository is used.
   */
  public fun inProgressRepository(inProgressRepository: String) {
    it.property("inProgressRepository", inProgressRepository)
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
   * Set the separator when passing a String to send batch message operation
   */
  public fun batchSeparator(batchSeparator: String) {
    it.property("batchSeparator", batchSeparator)
  }

  /**
   * Delay sending messages for a number of seconds.
   */
  public fun delaySeconds(delaySeconds: String) {
    it.property("delaySeconds", delaySeconds)
  }

  /**
   * Delay sending messages for a number of seconds.
   */
  public fun delaySeconds(delaySeconds: Int) {
    it.property("delaySeconds", delaySeconds.toString())
  }

  /**
   * Only for FIFO queues. Strategy for setting the messageDeduplicationId on the message. It can be
   * one of the following options: useExchangeId, useContentBasedDeduplication. For the
   * useContentBasedDeduplication option, no messageDeduplicationId will be set on the message.
   */
  public fun messageDeduplicationIdStrategy(messageDeduplicationIdStrategy: String) {
    it.property("messageDeduplicationIdStrategy", messageDeduplicationIdStrategy)
  }

  /**
   * Only for FIFO queues. Strategy for setting the messageGroupId on the message. It can be one of
   * the following options: useConstant, useExchangeId, usePropertyValue. For the usePropertyValue
   * option, the value of property CamelAwsMessageGroupId will be used.
   */
  public fun messageGroupIdStrategy(messageGroupIdStrategy: String) {
    it.property("messageGroupIdStrategy", messageGroupIdStrategy)
  }

  /**
   * What to do if sending to AWS SQS has more messages than AWS allows (currently only maximum 10
   * message headers are allowed). WARN will log a WARN about the limit is for each additional header,
   * so the message can be sent to AWS. WARN_ONCE will only log one time a WARN about the limit is hit,
   * and drop additional headers, so the message can be sent to AWS. IGNORE will ignore (no logging)
   * and drop additional headers, so the message can be sent to AWS. FAIL will cause an exception to be
   * thrown and the message is not sent to AWS.
   */
  public fun messageHeaderExceededLimit(messageHeaderExceededLimit: String) {
    it.property("messageHeaderExceededLimit", messageHeaderExceededLimit)
  }

  /**
   * The operation to do in case the user don't want to send only a message
   */
  public fun operation(operation: String) {
    it.property("operation", operation)
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
   * To use the AmazonSQS client
   */
  public fun amazonSQSClient(amazonSQSClient: String) {
    it.property("amazonSQSClient", amazonSQSClient)
  }

  /**
   * Define if you want to apply delaySeconds option to the queue or on single messages
   */
  public fun delayQueue(delayQueue: String) {
    it.property("delayQueue", delayQueue)
  }

  /**
   * Define if you want to apply delaySeconds option to the queue or on single messages
   */
  public fun delayQueue(delayQueue: Boolean) {
    it.property("delayQueue", delayQueue.toString())
  }

  /**
   * To define a proxy host when instantiating the SQS client
   */
  public fun proxyHost(proxyHost: String) {
    it.property("proxyHost", proxyHost)
  }

  /**
   * To define a proxy port when instantiating the SQS client
   */
  public fun proxyPort(proxyPort: String) {
    it.property("proxyPort", proxyPort)
  }

  /**
   * To define a proxy port when instantiating the SQS client
   */
  public fun proxyPort(proxyPort: Int) {
    it.property("proxyPort", proxyPort.toString())
  }

  /**
   * To define a proxy protocol when instantiating the SQS client
   */
  public fun proxyProtocol(proxyProtocol: String) {
    it.property("proxyProtocol", proxyProtocol)
  }

  /**
   * The maximumMessageSize (in bytes) an SQS message can contain for this queue.
   */
  public fun maximumMessageSize(maximumMessageSize: String) {
    it.property("maximumMessageSize", maximumMessageSize)
  }

  /**
   * The maximumMessageSize (in bytes) an SQS message can contain for this queue.
   */
  public fun maximumMessageSize(maximumMessageSize: Int) {
    it.property("maximumMessageSize", maximumMessageSize.toString())
  }

  /**
   * The messageRetentionPeriod (in seconds) a message will be retained by SQS for this queue.
   */
  public fun messageRetentionPeriod(messageRetentionPeriod: String) {
    it.property("messageRetentionPeriod", messageRetentionPeriod)
  }

  /**
   * The messageRetentionPeriod (in seconds) a message will be retained by SQS for this queue.
   */
  public fun messageRetentionPeriod(messageRetentionPeriod: Int) {
    it.property("messageRetentionPeriod", messageRetentionPeriod.toString())
  }

  /**
   * The policy for this queue. It can be loaded by default from classpath, but you can prefix with
   * classpath:, file:, or http: to load the resource from different systems.
   */
  public fun policy(policy: String) {
    it.property("policy", policy)
  }

  /**
   * To define the queueUrl explicitly. All other parameters, which would influence the queueUrl,
   * are ignored. This parameter is intended to be used to connect to a mock implementation of SQS, for
   * testing purposes.
   */
  public fun queueUrl(queueUrl: String) {
    it.property("queueUrl", queueUrl)
  }

  /**
   * If you do not specify WaitTimeSeconds in the request, the queue attribute
   * ReceiveMessageWaitTimeSeconds is used to determine how long to wait.
   */
  public fun receiveMessageWaitTimeSeconds(receiveMessageWaitTimeSeconds: String) {
    it.property("receiveMessageWaitTimeSeconds", receiveMessageWaitTimeSeconds)
  }

  /**
   * If you do not specify WaitTimeSeconds in the request, the queue attribute
   * ReceiveMessageWaitTimeSeconds is used to determine how long to wait.
   */
  public fun receiveMessageWaitTimeSeconds(receiveMessageWaitTimeSeconds: Int) {
    it.property("receiveMessageWaitTimeSeconds", receiveMessageWaitTimeSeconds.toString())
  }

  /**
   * Specify the policy that send message to DeadLetter queue. See detail at Amazon docs.
   */
  public fun redrivePolicy(redrivePolicy: String) {
    it.property("redrivePolicy", redrivePolicy)
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
   * If using a profile credentials provider, this parameter will set the profile name
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
   * Amazon AWS Session Token used when the user needs to assume an IAM role
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
   * Set whether the SQS client should expect to load credentials on an AWS infra instance or to
   * expect static credentials to be passed in.
   */
  public fun useDefaultCredentialsProvider(useDefaultCredentialsProvider: String) {
    it.property("useDefaultCredentialsProvider", useDefaultCredentialsProvider)
  }

  /**
   * Set whether the SQS client should expect to load credentials on an AWS infra instance or to
   * expect static credentials to be passed in.
   */
  public fun useDefaultCredentialsProvider(useDefaultCredentialsProvider: Boolean) {
    it.property("useDefaultCredentialsProvider", useDefaultCredentialsProvider.toString())
  }

  /**
   * Set whether the SQS client should expect to load credentials through a profile credentials
   * provider.
   */
  public fun useProfileCredentialsProvider(useProfileCredentialsProvider: String) {
    it.property("useProfileCredentialsProvider", useProfileCredentialsProvider)
  }

  /**
   * Set whether the SQS client should expect to load credentials through a profile credentials
   * provider.
   */
  public fun useProfileCredentialsProvider(useProfileCredentialsProvider: Boolean) {
    it.property("useProfileCredentialsProvider", useProfileCredentialsProvider.toString())
  }

  /**
   * Set whether the SQS client should expect to use Session Credentials. This is useful in a
   * situation in which the user needs to assume an IAM role for doing operations in SQS.
   */
  public fun useSessionCredentials(useSessionCredentials: String) {
    it.property("useSessionCredentials", useSessionCredentials)
  }

  /**
   * Set whether the SQS client should expect to use Session Credentials. This is useful in a
   * situation in which the user needs to assume an IAM role for doing operations in SQS.
   */
  public fun useSessionCredentials(useSessionCredentials: Boolean) {
    it.property("useSessionCredentials", useSessionCredentials.toString())
  }
}
