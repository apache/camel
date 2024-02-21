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

public fun UriDsl.`azure-storage-blob`(i: AzureStorageBlobUriDsl.() -> Unit) {
  AzureStorageBlobUriDsl(this).apply(i)
}

@CamelDslMarker
public class AzureStorageBlobUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("azure-storage-blob")
  }

  private var accountName: String = ""

  private var containerName: String = ""

  public fun accountName(accountName: String) {
    this.accountName = accountName
    it.url("$accountName/$containerName")
  }

  public fun containerName(containerName: String) {
    this.containerName = containerName
    it.url("$accountName/$containerName")
  }

  public fun blobName(blobName: String) {
    it.property("blobName", blobName)
  }

  public fun blobOffset(blobOffset: String) {
    it.property("blobOffset", blobOffset)
  }

  public fun blobOffset(blobOffset: Int) {
    it.property("blobOffset", blobOffset.toString())
  }

  public fun blobServiceClient(blobServiceClient: String) {
    it.property("blobServiceClient", blobServiceClient)
  }

  public fun blobType(blobType: String) {
    it.property("blobType", blobType)
  }

  public fun closeStreamAfterRead(closeStreamAfterRead: String) {
    it.property("closeStreamAfterRead", closeStreamAfterRead)
  }

  public fun closeStreamAfterRead(closeStreamAfterRead: Boolean) {
    it.property("closeStreamAfterRead", closeStreamAfterRead.toString())
  }

  public fun credentials(credentials: String) {
    it.property("credentials", credentials)
  }

  public fun credentialType(credentialType: String) {
    it.property("credentialType", credentialType)
  }

  public fun dataCount(dataCount: String) {
    it.property("dataCount", dataCount)
  }

  public fun dataCount(dataCount: Int) {
    it.property("dataCount", dataCount.toString())
  }

  public fun fileDir(fileDir: String) {
    it.property("fileDir", fileDir)
  }

  public fun maxResultsPerPage(maxResultsPerPage: String) {
    it.property("maxResultsPerPage", maxResultsPerPage)
  }

  public fun maxResultsPerPage(maxResultsPerPage: Int) {
    it.property("maxResultsPerPage", maxResultsPerPage.toString())
  }

  public fun maxRetryRequests(maxRetryRequests: String) {
    it.property("maxRetryRequests", maxRetryRequests)
  }

  public fun maxRetryRequests(maxRetryRequests: Int) {
    it.property("maxRetryRequests", maxRetryRequests.toString())
  }

  public fun prefix(prefix: String) {
    it.property("prefix", prefix)
  }

  public fun regex(regex: String) {
    it.property("regex", regex)
  }

  public fun sasToken(sasToken: String) {
    it.property("sasToken", sasToken)
  }

  public fun serviceClient(serviceClient: String) {
    it.property("serviceClient", serviceClient)
  }

  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: String) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle)
  }

  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: Boolean) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle.toString())
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

  public fun blobSequenceNumber(blobSequenceNumber: String) {
    it.property("blobSequenceNumber", blobSequenceNumber)
  }

  public fun blobSequenceNumber(blobSequenceNumber: Int) {
    it.property("blobSequenceNumber", blobSequenceNumber.toString())
  }

  public fun blockListType(blockListType: String) {
    it.property("blockListType", blockListType)
  }

  public fun changeFeedContext(changeFeedContext: String) {
    it.property("changeFeedContext", changeFeedContext)
  }

  public fun changeFeedEndTime(changeFeedEndTime: String) {
    it.property("changeFeedEndTime", changeFeedEndTime)
  }

  public fun changeFeedStartTime(changeFeedStartTime: String) {
    it.property("changeFeedStartTime", changeFeedStartTime)
  }

  public fun closeStreamAfterWrite(closeStreamAfterWrite: String) {
    it.property("closeStreamAfterWrite", closeStreamAfterWrite)
  }

  public fun closeStreamAfterWrite(closeStreamAfterWrite: Boolean) {
    it.property("closeStreamAfterWrite", closeStreamAfterWrite.toString())
  }

  public fun commitBlockListLater(commitBlockListLater: String) {
    it.property("commitBlockListLater", commitBlockListLater)
  }

  public fun commitBlockListLater(commitBlockListLater: Boolean) {
    it.property("commitBlockListLater", commitBlockListLater.toString())
  }

  public fun createAppendBlob(createAppendBlob: String) {
    it.property("createAppendBlob", createAppendBlob)
  }

  public fun createAppendBlob(createAppendBlob: Boolean) {
    it.property("createAppendBlob", createAppendBlob.toString())
  }

  public fun createPageBlob(createPageBlob: String) {
    it.property("createPageBlob", createPageBlob)
  }

  public fun createPageBlob(createPageBlob: Boolean) {
    it.property("createPageBlob", createPageBlob.toString())
  }

  public fun downloadLinkExpiration(downloadLinkExpiration: String) {
    it.property("downloadLinkExpiration", downloadLinkExpiration)
  }

  public fun downloadLinkExpiration(downloadLinkExpiration: Int) {
    it.property("downloadLinkExpiration", downloadLinkExpiration.toString())
  }

  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  public fun pageBlobSize(pageBlobSize: String) {
    it.property("pageBlobSize", pageBlobSize)
  }

  public fun pageBlobSize(pageBlobSize: Int) {
    it.property("pageBlobSize", pageBlobSize.toString())
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

  public fun sourceBlobAccessKey(sourceBlobAccessKey: String) {
    it.property("sourceBlobAccessKey", sourceBlobAccessKey)
  }
}
