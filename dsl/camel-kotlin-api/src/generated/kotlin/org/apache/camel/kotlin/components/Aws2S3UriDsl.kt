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

public fun UriDsl.`aws2-s3`(i: Aws2S3UriDsl.() -> Unit) {
  Aws2S3UriDsl(this).apply(i)
}

@CamelDslMarker
public class Aws2S3UriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("aws2-s3")
  }

  private var bucketNameOrArn: String = ""

  public fun bucketNameOrArn(bucketNameOrArn: String) {
    this.bucketNameOrArn = bucketNameOrArn
    it.url("//$bucketNameOrArn")
  }

  public fun autoCreateBucket(autoCreateBucket: String) {
    it.property("autoCreateBucket", autoCreateBucket)
  }

  public fun autoCreateBucket(autoCreateBucket: Boolean) {
    it.property("autoCreateBucket", autoCreateBucket.toString())
  }

  public fun delimiter(delimiter: String) {
    it.property("delimiter", delimiter)
  }

  public fun forcePathStyle(forcePathStyle: String) {
    it.property("forcePathStyle", forcePathStyle)
  }

  public fun forcePathStyle(forcePathStyle: Boolean) {
    it.property("forcePathStyle", forcePathStyle.toString())
  }

  public fun overrideEndpoint(overrideEndpoint: String) {
    it.property("overrideEndpoint", overrideEndpoint)
  }

  public fun overrideEndpoint(overrideEndpoint: Boolean) {
    it.property("overrideEndpoint", overrideEndpoint.toString())
  }

  public fun pojoRequest(pojoRequest: String) {
    it.property("pojoRequest", pojoRequest)
  }

  public fun pojoRequest(pojoRequest: Boolean) {
    it.property("pojoRequest", pojoRequest.toString())
  }

  public fun policy(policy: String) {
    it.property("policy", policy)
  }

  public fun prefix(prefix: String) {
    it.property("prefix", prefix)
  }

  public fun region(region: String) {
    it.property("region", region)
  }

  public fun uriEndpointOverride(uriEndpointOverride: String) {
    it.property("uriEndpointOverride", uriEndpointOverride)
  }

  public fun customerAlgorithm(customerAlgorithm: String) {
    it.property("customerAlgorithm", customerAlgorithm)
  }

  public fun customerKeyId(customerKeyId: String) {
    it.property("customerKeyId", customerKeyId)
  }

  public fun customerKeyMD5(customerKeyMD5: String) {
    it.property("customerKeyMD5", customerKeyMD5)
  }

  public fun deleteAfterRead(deleteAfterRead: String) {
    it.property("deleteAfterRead", deleteAfterRead)
  }

  public fun deleteAfterRead(deleteAfterRead: Boolean) {
    it.property("deleteAfterRead", deleteAfterRead.toString())
  }

  public fun destinationBucket(destinationBucket: String) {
    it.property("destinationBucket", destinationBucket)
  }

  public fun destinationBucketPrefix(destinationBucketPrefix: String) {
    it.property("destinationBucketPrefix", destinationBucketPrefix)
  }

  public fun destinationBucketSuffix(destinationBucketSuffix: String) {
    it.property("destinationBucketSuffix", destinationBucketSuffix)
  }

  public fun doneFileName(doneFileName: String) {
    it.property("doneFileName", doneFileName)
  }

  public fun fileName(fileName: String) {
    it.property("fileName", fileName)
  }

  public fun ignoreBody(ignoreBody: String) {
    it.property("ignoreBody", ignoreBody)
  }

  public fun ignoreBody(ignoreBody: Boolean) {
    it.property("ignoreBody", ignoreBody.toString())
  }

  public fun includeBody(includeBody: String) {
    it.property("includeBody", includeBody)
  }

  public fun includeBody(includeBody: Boolean) {
    it.property("includeBody", includeBody.toString())
  }

  public fun includeFolders(includeFolders: String) {
    it.property("includeFolders", includeFolders)
  }

  public fun includeFolders(includeFolders: Boolean) {
    it.property("includeFolders", includeFolders.toString())
  }

  public fun maxConnections(maxConnections: String) {
    it.property("maxConnections", maxConnections)
  }

  public fun maxConnections(maxConnections: Int) {
    it.property("maxConnections", maxConnections.toString())
  }

  public fun maxMessagesPerPoll(maxMessagesPerPoll: String) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll)
  }

  public fun maxMessagesPerPoll(maxMessagesPerPoll: Int) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll.toString())
  }

  public fun moveAfterRead(moveAfterRead: String) {
    it.property("moveAfterRead", moveAfterRead)
  }

  public fun moveAfterRead(moveAfterRead: Boolean) {
    it.property("moveAfterRead", moveAfterRead.toString())
  }

  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: String) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle)
  }

  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: Boolean) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle.toString())
  }

  public fun autocloseBody(autocloseBody: String) {
    it.property("autocloseBody", autocloseBody)
  }

  public fun autocloseBody(autocloseBody: Boolean) {
    it.property("autocloseBody", autocloseBody.toString())
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

  public fun batchMessageNumber(batchMessageNumber: String) {
    it.property("batchMessageNumber", batchMessageNumber)
  }

  public fun batchMessageNumber(batchMessageNumber: Int) {
    it.property("batchMessageNumber", batchMessageNumber.toString())
  }

  public fun batchSize(batchSize: String) {
    it.property("batchSize", batchSize)
  }

  public fun batchSize(batchSize: Int) {
    it.property("batchSize", batchSize.toString())
  }

  public fun bufferSize(bufferSize: String) {
    it.property("bufferSize", bufferSize)
  }

  public fun bufferSize(bufferSize: Int) {
    it.property("bufferSize", bufferSize.toString())
  }

  public fun deleteAfterWrite(deleteAfterWrite: String) {
    it.property("deleteAfterWrite", deleteAfterWrite)
  }

  public fun deleteAfterWrite(deleteAfterWrite: Boolean) {
    it.property("deleteAfterWrite", deleteAfterWrite.toString())
  }

  public fun keyName(keyName: String) {
    it.property("keyName", keyName)
  }

  public fun multiPartUpload(multiPartUpload: String) {
    it.property("multiPartUpload", multiPartUpload)
  }

  public fun multiPartUpload(multiPartUpload: Boolean) {
    it.property("multiPartUpload", multiPartUpload.toString())
  }

  public fun namingStrategy(namingStrategy: String) {
    it.property("namingStrategy", namingStrategy)
  }

  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  public fun partSize(partSize: String) {
    it.property("partSize", partSize)
  }

  public fun partSize(partSize: Int) {
    it.property("partSize", partSize.toString())
  }

  public fun restartingPolicy(restartingPolicy: String) {
    it.property("restartingPolicy", restartingPolicy)
  }

  public fun storageClass(storageClass: String) {
    it.property("storageClass", storageClass)
  }

  public fun streamingUploadMode(streamingUploadMode: String) {
    it.property("streamingUploadMode", streamingUploadMode)
  }

  public fun streamingUploadMode(streamingUploadMode: Boolean) {
    it.property("streamingUploadMode", streamingUploadMode.toString())
  }

  public fun streamingUploadTimeout(streamingUploadTimeout: String) {
    it.property("streamingUploadTimeout", streamingUploadTimeout)
  }

  public fun streamingUploadTimeout(streamingUploadTimeout: Int) {
    it.property("streamingUploadTimeout", streamingUploadTimeout.toString())
  }

  public fun awsKMSKeyId(awsKMSKeyId: String) {
    it.property("awsKMSKeyId", awsKMSKeyId)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun useAwsKMS(useAwsKMS: String) {
    it.property("useAwsKMS", useAwsKMS)
  }

  public fun useAwsKMS(useAwsKMS: Boolean) {
    it.property("useAwsKMS", useAwsKMS.toString())
  }

  public fun useCustomerKey(useCustomerKey: String) {
    it.property("useCustomerKey", useCustomerKey)
  }

  public fun useCustomerKey(useCustomerKey: Boolean) {
    it.property("useCustomerKey", useCustomerKey.toString())
  }

  public fun useSSES3(useSSES3: String) {
    it.property("useSSES3", useSSES3)
  }

  public fun useSSES3(useSSES3: Boolean) {
    it.property("useSSES3", useSSES3.toString())
  }

  public fun amazonS3Client(amazonS3Client: String) {
    it.property("amazonS3Client", amazonS3Client)
  }

  public fun amazonS3Presigner(amazonS3Presigner: String) {
    it.property("amazonS3Presigner", amazonS3Presigner)
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
