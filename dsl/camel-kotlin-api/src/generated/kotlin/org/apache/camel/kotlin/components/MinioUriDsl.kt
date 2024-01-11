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

public fun UriDsl.minio(i: MinioUriDsl.() -> Unit) {
  MinioUriDsl(this).apply(i)
}

@CamelDslMarker
public class MinioUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("minio")
  }

  private var bucketName: String = ""

  public fun bucketName(bucketName: String) {
    this.bucketName = bucketName
    it.url("$bucketName")
  }

  public fun autoCreateBucket(autoCreateBucket: String) {
    it.property("autoCreateBucket", autoCreateBucket)
  }

  public fun autoCreateBucket(autoCreateBucket: Boolean) {
    it.property("autoCreateBucket", autoCreateBucket.toString())
  }

  public fun customHttpClient(customHttpClient: String) {
    it.property("customHttpClient", customHttpClient)
  }

  public fun endpoint(endpoint: String) {
    it.property("endpoint", endpoint)
  }

  public fun minioClient(minioClient: String) {
    it.property("minioClient", minioClient)
  }

  public fun objectLock(objectLock: String) {
    it.property("objectLock", objectLock)
  }

  public fun objectLock(objectLock: Boolean) {
    it.property("objectLock", objectLock.toString())
  }

  public fun policy(policy: String) {
    it.property("policy", policy)
  }

  public fun proxyPort(proxyPort: String) {
    it.property("proxyPort", proxyPort)
  }

  public fun proxyPort(proxyPort: Int) {
    it.property("proxyPort", proxyPort.toString())
  }

  public fun region(region: String) {
    it.property("region", region)
  }

  public fun secure(secure: String) {
    it.property("secure", secure)
  }

  public fun secure(secure: Boolean) {
    it.property("secure", secure.toString())
  }

  public fun serverSideEncryption(serverSideEncryption: String) {
    it.property("serverSideEncryption", serverSideEncryption)
  }

  public fun serverSideEncryptionCustomerKey(serverSideEncryptionCustomerKey: String) {
    it.property("serverSideEncryptionCustomerKey", serverSideEncryptionCustomerKey)
  }

  public fun autoCloseBody(autoCloseBody: String) {
    it.property("autoCloseBody", autoCloseBody)
  }

  public fun autoCloseBody(autoCloseBody: Boolean) {
    it.property("autoCloseBody", autoCloseBody.toString())
  }

  public fun bypassGovernanceMode(bypassGovernanceMode: String) {
    it.property("bypassGovernanceMode", bypassGovernanceMode)
  }

  public fun bypassGovernanceMode(bypassGovernanceMode: Boolean) {
    it.property("bypassGovernanceMode", bypassGovernanceMode.toString())
  }

  public fun deleteAfterRead(deleteAfterRead: String) {
    it.property("deleteAfterRead", deleteAfterRead)
  }

  public fun deleteAfterRead(deleteAfterRead: Boolean) {
    it.property("deleteAfterRead", deleteAfterRead.toString())
  }

  public fun delimiter(delimiter: String) {
    it.property("delimiter", delimiter)
  }

  public fun destinationBucketName(destinationBucketName: String) {
    it.property("destinationBucketName", destinationBucketName)
  }

  public fun destinationObjectName(destinationObjectName: String) {
    it.property("destinationObjectName", destinationObjectName)
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

  public fun includeUserMetadata(includeUserMetadata: String) {
    it.property("includeUserMetadata", includeUserMetadata)
  }

  public fun includeUserMetadata(includeUserMetadata: Boolean) {
    it.property("includeUserMetadata", includeUserMetadata.toString())
  }

  public fun includeVersions(includeVersions: String) {
    it.property("includeVersions", includeVersions)
  }

  public fun includeVersions(includeVersions: Boolean) {
    it.property("includeVersions", includeVersions.toString())
  }

  public fun length(length: String) {
    it.property("length", length)
  }

  public fun length(length: Int) {
    it.property("length", length.toString())
  }

  public fun matchETag(matchETag: String) {
    it.property("matchETag", matchETag)
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

  public fun modifiedSince(modifiedSince: String) {
    it.property("modifiedSince", modifiedSince)
  }

  public fun moveAfterRead(moveAfterRead: String) {
    it.property("moveAfterRead", moveAfterRead)
  }

  public fun moveAfterRead(moveAfterRead: Boolean) {
    it.property("moveAfterRead", moveAfterRead.toString())
  }

  public fun notMatchETag(notMatchETag: String) {
    it.property("notMatchETag", notMatchETag)
  }

  public fun objectName(objectName: String) {
    it.property("objectName", objectName)
  }

  public fun offset(offset: String) {
    it.property("offset", offset)
  }

  public fun offset(offset: Int) {
    it.property("offset", offset.toString())
  }

  public fun prefix(prefix: String) {
    it.property("prefix", prefix)
  }

  public fun recursive(recursive: String) {
    it.property("recursive", recursive)
  }

  public fun recursive(recursive: Boolean) {
    it.property("recursive", recursive.toString())
  }

  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: String) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle)
  }

  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: Boolean) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle.toString())
  }

  public fun startAfter(startAfter: String) {
    it.property("startAfter", startAfter)
  }

  public fun unModifiedSince(unModifiedSince: String) {
    it.property("unModifiedSince", unModifiedSince)
  }

  public fun useVersion1(useVersion1: String) {
    it.property("useVersion1", useVersion1)
  }

  public fun useVersion1(useVersion1: Boolean) {
    it.property("useVersion1", useVersion1.toString())
  }

  public fun versionId(versionId: String) {
    it.property("versionId", versionId)
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

  public fun deleteAfterWrite(deleteAfterWrite: String) {
    it.property("deleteAfterWrite", deleteAfterWrite)
  }

  public fun deleteAfterWrite(deleteAfterWrite: Boolean) {
    it.property("deleteAfterWrite", deleteAfterWrite.toString())
  }

  public fun keyName(keyName: String) {
    it.property("keyName", keyName)
  }

  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  public fun pojoRequest(pojoRequest: String) {
    it.property("pojoRequest", pojoRequest)
  }

  public fun pojoRequest(pojoRequest: Boolean) {
    it.property("pojoRequest", pojoRequest.toString())
  }

  public fun storageClass(storageClass: String) {
    it.property("storageClass", storageClass)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
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

  public fun secretKey(secretKey: String) {
    it.property("secretKey", secretKey)
  }
}
