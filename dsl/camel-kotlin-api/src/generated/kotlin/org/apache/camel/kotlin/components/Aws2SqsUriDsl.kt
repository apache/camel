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

  public fun queueNameOrArn(queueNameOrArn: String) {
    this.queueNameOrArn = queueNameOrArn
    it.url("$queueNameOrArn")
  }

  public fun amazonAWSHost(amazonAWSHost: String) {
    it.property("amazonAWSHost", amazonAWSHost)
  }

  public fun autoCreateQueue(autoCreateQueue: String) {
    it.property("autoCreateQueue", autoCreateQueue)
  }

  public fun autoCreateQueue(autoCreateQueue: Boolean) {
    it.property("autoCreateQueue", autoCreateQueue.toString())
  }

  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
  }

  public fun overrideEndpoint(overrideEndpoint: String) {
    it.property("overrideEndpoint", overrideEndpoint)
  }

  public fun overrideEndpoint(overrideEndpoint: Boolean) {
    it.property("overrideEndpoint", overrideEndpoint.toString())
  }

  public fun protocol(protocol: String) {
    it.property("protocol", protocol)
  }

  public fun queueOwnerAWSAccountId(queueOwnerAWSAccountId: String) {
    it.property("queueOwnerAWSAccountId", queueOwnerAWSAccountId)
  }

  public fun region(region: String) {
    it.property("region", region)
  }

  public fun uriEndpointOverride(uriEndpointOverride: String) {
    it.property("uriEndpointOverride", uriEndpointOverride)
  }

  public fun attributeNames(attributeNames: String) {
    it.property("attributeNames", attributeNames)
  }

  public fun concurrentConsumers(concurrentConsumers: String) {
    it.property("concurrentConsumers", concurrentConsumers)
  }

  public fun concurrentConsumers(concurrentConsumers: Int) {
    it.property("concurrentConsumers", concurrentConsumers.toString())
  }

  public fun defaultVisibilityTimeout(defaultVisibilityTimeout: String) {
    it.property("defaultVisibilityTimeout", defaultVisibilityTimeout)
  }

  public fun defaultVisibilityTimeout(defaultVisibilityTimeout: Int) {
    it.property("defaultVisibilityTimeout", defaultVisibilityTimeout.toString())
  }

  public fun deleteAfterRead(deleteAfterRead: String) {
    it.property("deleteAfterRead", deleteAfterRead)
  }

  public fun deleteAfterRead(deleteAfterRead: Boolean) {
    it.property("deleteAfterRead", deleteAfterRead.toString())
  }

  public fun deleteIfFiltered(deleteIfFiltered: String) {
    it.property("deleteIfFiltered", deleteIfFiltered)
  }

  public fun deleteIfFiltered(deleteIfFiltered: Boolean) {
    it.property("deleteIfFiltered", deleteIfFiltered.toString())
  }

  public fun extendMessageVisibility(extendMessageVisibility: String) {
    it.property("extendMessageVisibility", extendMessageVisibility)
  }

  public fun extendMessageVisibility(extendMessageVisibility: Boolean) {
    it.property("extendMessageVisibility", extendMessageVisibility.toString())
  }

  public fun kmsDataKeyReusePeriodSeconds(kmsDataKeyReusePeriodSeconds: String) {
    it.property("kmsDataKeyReusePeriodSeconds", kmsDataKeyReusePeriodSeconds)
  }

  public fun kmsDataKeyReusePeriodSeconds(kmsDataKeyReusePeriodSeconds: Int) {
    it.property("kmsDataKeyReusePeriodSeconds", kmsDataKeyReusePeriodSeconds.toString())
  }

  public fun kmsMasterKeyId(kmsMasterKeyId: String) {
    it.property("kmsMasterKeyId", kmsMasterKeyId)
  }

  public fun maxMessagesPerPoll(maxMessagesPerPoll: String) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll)
  }

  public fun maxMessagesPerPoll(maxMessagesPerPoll: Int) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll.toString())
  }

  public fun messageAttributeNames(messageAttributeNames: String) {
    it.property("messageAttributeNames", messageAttributeNames)
  }

  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: String) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle)
  }

  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: Boolean) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle.toString())
  }

  public fun serverSideEncryptionEnabled(serverSideEncryptionEnabled: String) {
    it.property("serverSideEncryptionEnabled", serverSideEncryptionEnabled)
  }

  public fun serverSideEncryptionEnabled(serverSideEncryptionEnabled: Boolean) {
    it.property("serverSideEncryptionEnabled", serverSideEncryptionEnabled.toString())
  }

  public fun visibilityTimeout(visibilityTimeout: String) {
    it.property("visibilityTimeout", visibilityTimeout)
  }

  public fun visibilityTimeout(visibilityTimeout: Int) {
    it.property("visibilityTimeout", visibilityTimeout.toString())
  }

  public fun waitTimeSeconds(waitTimeSeconds: String) {
    it.property("waitTimeSeconds", waitTimeSeconds)
  }

  public fun waitTimeSeconds(waitTimeSeconds: Int) {
    it.property("waitTimeSeconds", waitTimeSeconds.toString())
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

  public fun pollStrategy(pollStrategy: String) {
    it.property("pollStrategy", pollStrategy)
  }

  public fun batchSeparator(batchSeparator: String) {
    it.property("batchSeparator", batchSeparator)
  }

  public fun delaySeconds(delaySeconds: String) {
    it.property("delaySeconds", delaySeconds)
  }

  public fun delaySeconds(delaySeconds: Int) {
    it.property("delaySeconds", delaySeconds.toString())
  }

  public fun messageDeduplicationIdStrategy(messageDeduplicationIdStrategy: String) {
    it.property("messageDeduplicationIdStrategy", messageDeduplicationIdStrategy)
  }

  public fun messageGroupIdStrategy(messageGroupIdStrategy: String) {
    it.property("messageGroupIdStrategy", messageGroupIdStrategy)
  }

  public fun messageHeaderExceededLimit(messageHeaderExceededLimit: String) {
    it.property("messageHeaderExceededLimit", messageHeaderExceededLimit)
  }

  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun amazonSQSClient(amazonSQSClient: String) {
    it.property("amazonSQSClient", amazonSQSClient)
  }

  public fun delayQueue(delayQueue: String) {
    it.property("delayQueue", delayQueue)
  }

  public fun delayQueue(delayQueue: Boolean) {
    it.property("delayQueue", delayQueue.toString())
  }

  public fun proxyHost(proxyHost: String) {
    it.property("proxyHost", proxyHost)
  }

  public fun proxyPort(proxyPort: String) {
    it.property("proxyPort", proxyPort)
  }

  public fun proxyPort(proxyPort: Int) {
    it.property("proxyPort", proxyPort.toString())
  }

  public fun proxyProtocol(proxyProtocol: String) {
    it.property("proxyProtocol", proxyProtocol)
  }

  public fun maximumMessageSize(maximumMessageSize: String) {
    it.property("maximumMessageSize", maximumMessageSize)
  }

  public fun maximumMessageSize(maximumMessageSize: Int) {
    it.property("maximumMessageSize", maximumMessageSize.toString())
  }

  public fun messageRetentionPeriod(messageRetentionPeriod: String) {
    it.property("messageRetentionPeriod", messageRetentionPeriod)
  }

  public fun messageRetentionPeriod(messageRetentionPeriod: Int) {
    it.property("messageRetentionPeriod", messageRetentionPeriod.toString())
  }

  public fun policy(policy: String) {
    it.property("policy", policy)
  }

  public fun queueUrl(queueUrl: String) {
    it.property("queueUrl", queueUrl)
  }

  public fun receiveMessageWaitTimeSeconds(receiveMessageWaitTimeSeconds: String) {
    it.property("receiveMessageWaitTimeSeconds", receiveMessageWaitTimeSeconds)
  }

  public fun receiveMessageWaitTimeSeconds(receiveMessageWaitTimeSeconds: Int) {
    it.property("receiveMessageWaitTimeSeconds", receiveMessageWaitTimeSeconds.toString())
  }

  public fun redrivePolicy(redrivePolicy: String) {
    it.property("redrivePolicy", redrivePolicy)
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

  public fun delay(delay: Int) {
    it.property("delay", delay.toString())
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

  public fun accessKey(accessKey: String) {
    it.property("accessKey", accessKey)
  }

  public fun profileCredentialsName(profileCredentialsName: String) {
    it.property("profileCredentialsName", profileCredentialsName)
  }

  public fun secretKey(secretKey: String) {
    it.property("secretKey", secretKey)
  }

  public fun sessionToken(sessionToken: String) {
    it.property("sessionToken", sessionToken)
  }

  public fun trustAllCertificates(trustAllCertificates: String) {
    it.property("trustAllCertificates", trustAllCertificates)
  }

  public fun trustAllCertificates(trustAllCertificates: Boolean) {
    it.property("trustAllCertificates", trustAllCertificates.toString())
  }

  public fun useDefaultCredentialsProvider(useDefaultCredentialsProvider: String) {
    it.property("useDefaultCredentialsProvider", useDefaultCredentialsProvider)
  }

  public fun useDefaultCredentialsProvider(useDefaultCredentialsProvider: Boolean) {
    it.property("useDefaultCredentialsProvider", useDefaultCredentialsProvider.toString())
  }

  public fun useProfileCredentialsProvider(useProfileCredentialsProvider: String) {
    it.property("useProfileCredentialsProvider", useProfileCredentialsProvider)
  }

  public fun useProfileCredentialsProvider(useProfileCredentialsProvider: Boolean) {
    it.property("useProfileCredentialsProvider", useProfileCredentialsProvider.toString())
  }

  public fun useSessionCredentials(useSessionCredentials: String) {
    it.property("useSessionCredentials", useSessionCredentials)
  }

  public fun useSessionCredentials(useSessionCredentials: Boolean) {
    it.property("useSessionCredentials", useSessionCredentials.toString())
  }
}
