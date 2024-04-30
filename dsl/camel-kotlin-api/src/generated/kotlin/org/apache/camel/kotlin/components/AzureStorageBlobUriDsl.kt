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
 * Store and retrieve blobs from Azure Storage Blob Service.
 */
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

  /**
   * Azure account name to be used for authentication with azure blob services
   */
  public fun accountName(accountName: String) {
    this.accountName = accountName
    it.url("$accountName/$containerName")
  }

  /**
   * The blob container name
   */
  public fun containerName(containerName: String) {
    this.containerName = containerName
    it.url("$accountName/$containerName")
  }

  /**
   * The blob name, to consume specific blob from a container. However, on producer it is only
   * required for the operations on the blob level
   */
  public fun blobName(blobName: String) {
    it.property("blobName", blobName)
  }

  /**
   * Set the blob offset for the upload or download operations, default is 0
   */
  public fun blobOffset(blobOffset: String) {
    it.property("blobOffset", blobOffset)
  }

  /**
   * Set the blob offset for the upload or download operations, default is 0
   */
  public fun blobOffset(blobOffset: Int) {
    it.property("blobOffset", blobOffset.toString())
  }

  /**
   * Client to a storage account. This client does not hold any state about a particular storage
   * account but is instead a convenient way of sending off appropriate requests to the resource on the
   * service. It may also be used to construct URLs to blobs and containers. This client contains
   * operations on a service account. Operations on a container are available on BlobContainerClient
   * through getBlobContainerClient(String), and operations on a blob are available on BlobClient
   * through getBlobContainerClient(String).getBlobClient(String).
   */
  public fun blobServiceClient(blobServiceClient: String) {
    it.property("blobServiceClient", blobServiceClient)
  }

  /**
   * The blob type in order to initiate the appropriate settings for each blob type
   */
  public fun blobType(blobType: String) {
    it.property("blobType", blobType)
  }

  /**
   * Close the stream after read or keep it open, default is true
   */
  public fun closeStreamAfterRead(closeStreamAfterRead: String) {
    it.property("closeStreamAfterRead", closeStreamAfterRead)
  }

  /**
   * Close the stream after read or keep it open, default is true
   */
  public fun closeStreamAfterRead(closeStreamAfterRead: Boolean) {
    it.property("closeStreamAfterRead", closeStreamAfterRead.toString())
  }

  /**
   * StorageSharedKeyCredential can be injected to create the azure client, this holds the important
   * authentication information
   */
  public fun credentials(credentials: String) {
    it.property("credentials", credentials)
  }

  /**
   * Determines the credential strategy to adopt
   */
  public fun credentialType(credentialType: String) {
    it.property("credentialType", credentialType)
  }

  /**
   * How many bytes to include in the range. Must be greater than or equal to 0 if specified.
   */
  public fun dataCount(dataCount: String) {
    it.property("dataCount", dataCount)
  }

  /**
   * How many bytes to include in the range. Must be greater than or equal to 0 if specified.
   */
  public fun dataCount(dataCount: Int) {
    it.property("dataCount", dataCount.toString())
  }

  /**
   * The file directory where the downloaded blobs will be saved to, this can be used in both,
   * producer and consumer
   */
  public fun fileDir(fileDir: String) {
    it.property("fileDir", fileDir)
  }

  /**
   * Specifies the maximum number of blobs to return, including all BlobPrefix elements. If the
   * request does not specify maxResultsPerPage or specifies a value greater than 5,000, the server
   * will return up to 5,000 items.
   */
  public fun maxResultsPerPage(maxResultsPerPage: String) {
    it.property("maxResultsPerPage", maxResultsPerPage)
  }

  /**
   * Specifies the maximum number of blobs to return, including all BlobPrefix elements. If the
   * request does not specify maxResultsPerPage or specifies a value greater than 5,000, the server
   * will return up to 5,000 items.
   */
  public fun maxResultsPerPage(maxResultsPerPage: Int) {
    it.property("maxResultsPerPage", maxResultsPerPage.toString())
  }

  /**
   * Specifies the maximum number of additional HTTP Get requests that will be made while reading
   * the data from a response body.
   */
  public fun maxRetryRequests(maxRetryRequests: String) {
    it.property("maxRetryRequests", maxRetryRequests)
  }

  /**
   * Specifies the maximum number of additional HTTP Get requests that will be made while reading
   * the data from a response body.
   */
  public fun maxRetryRequests(maxRetryRequests: Int) {
    it.property("maxRetryRequests", maxRetryRequests.toString())
  }

  /**
   * Filters the results to return only blobs whose names begin with the specified prefix. May be
   * null to return all blobs.
   */
  public fun prefix(prefix: String) {
    it.property("prefix", prefix)
  }

  /**
   * Filters the results to return only blobs whose names match the specified regular expression.
   * May be null to return all if both prefix and regex are set, regex takes the priority and prefix is
   * ignored.
   */
  public fun regex(regex: String) {
    it.property("regex", regex)
  }

  /**
   * In case of usage of Shared Access Signature we'll need to set a SAS Token
   */
  public fun sasToken(sasToken: String) {
    it.property("sasToken", sasToken)
  }

  /**
   * Client to a storage account. This client does not hold any state about a particular storage
   * account but is instead a convenient way of sending off appropriate requests to the resource on the
   * service. It may also be used to construct URLs to blobs and containers. This client contains
   * operations on a service account. Operations on a container are available on BlobContainerClient
   * through BlobServiceClient#getBlobContainerClient(String), and operations on a blob are available
   * on BlobClient through BlobContainerClient#getBlobClient(String).
   */
  public fun serviceClient(serviceClient: String) {
    it.property("serviceClient", serviceClient)
  }

  /**
   * An optional timeout value beyond which a RuntimeException will be raised.
   */
  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
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
   * A user-controlled value that you can use to track requests. The value of the sequence number
   * must be between 0 and 263 - 1.The default value is 0.
   */
  public fun blobSequenceNumber(blobSequenceNumber: String) {
    it.property("blobSequenceNumber", blobSequenceNumber)
  }

  /**
   * A user-controlled value that you can use to track requests. The value of the sequence number
   * must be between 0 and 263 - 1.The default value is 0.
   */
  public fun blobSequenceNumber(blobSequenceNumber: Int) {
    it.property("blobSequenceNumber", blobSequenceNumber.toString())
  }

  /**
   * Specifies which type of blocks to return.
   */
  public fun blockListType(blockListType: String) {
    it.property("blockListType", blockListType)
  }

  /**
   * When using getChangeFeed producer operation, this gives additional context that is passed
   * through the Http pipeline during the service call.
   */
  public fun changeFeedContext(changeFeedContext: String) {
    it.property("changeFeedContext", changeFeedContext)
  }

  /**
   * When using getChangeFeed producer operation, this filters the results to return events
   * approximately before the end time. Note: A few events belonging to the next hour can also be
   * returned. A few events belonging to this hour can be missing; to ensure all events from the hour
   * are returned, round the end time up by an hour.
   */
  public fun changeFeedEndTime(changeFeedEndTime: String) {
    it.property("changeFeedEndTime", changeFeedEndTime)
  }

  /**
   * When using getChangeFeed producer operation, this filters the results to return events
   * approximately after the start time. Note: A few events belonging to the previous hour can also be
   * returned. A few events belonging to this hour can be missing; to ensure all events from the hour
   * are returned, round the start time down by an hour.
   */
  public fun changeFeedStartTime(changeFeedStartTime: String) {
    it.property("changeFeedStartTime", changeFeedStartTime)
  }

  /**
   * Close the stream after write or keep it open, default is true
   */
  public fun closeStreamAfterWrite(closeStreamAfterWrite: String) {
    it.property("closeStreamAfterWrite", closeStreamAfterWrite)
  }

  /**
   * Close the stream after write or keep it open, default is true
   */
  public fun closeStreamAfterWrite(closeStreamAfterWrite: Boolean) {
    it.property("closeStreamAfterWrite", closeStreamAfterWrite.toString())
  }

  /**
   * When is set to true, the staged blocks will not be committed directly.
   */
  public fun commitBlockListLater(commitBlockListLater: String) {
    it.property("commitBlockListLater", commitBlockListLater)
  }

  /**
   * When is set to true, the staged blocks will not be committed directly.
   */
  public fun commitBlockListLater(commitBlockListLater: Boolean) {
    it.property("commitBlockListLater", commitBlockListLater.toString())
  }

  /**
   * When is set to true, the append blocks will be created when committing append blocks.
   */
  public fun createAppendBlob(createAppendBlob: String) {
    it.property("createAppendBlob", createAppendBlob)
  }

  /**
   * When is set to true, the append blocks will be created when committing append blocks.
   */
  public fun createAppendBlob(createAppendBlob: Boolean) {
    it.property("createAppendBlob", createAppendBlob.toString())
  }

  /**
   * When is set to true, the page blob will be created when uploading page blob.
   */
  public fun createPageBlob(createPageBlob: String) {
    it.property("createPageBlob", createPageBlob)
  }

  /**
   * When is set to true, the page blob will be created when uploading page blob.
   */
  public fun createPageBlob(createPageBlob: Boolean) {
    it.property("createPageBlob", createPageBlob.toString())
  }

  /**
   * Override the default expiration (millis) of URL download link.
   */
  public fun downloadLinkExpiration(downloadLinkExpiration: String) {
    it.property("downloadLinkExpiration", downloadLinkExpiration)
  }

  /**
   * Override the default expiration (millis) of URL download link.
   */
  public fun downloadLinkExpiration(downloadLinkExpiration: Int) {
    it.property("downloadLinkExpiration", downloadLinkExpiration.toString())
  }

  /**
   * The blob operation that can be used with this component on the producer
   */
  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  /**
   * Specifies the maximum size for the page blob, up to 8 TB. The page blob size must be aligned to
   * a 512-byte boundary.
   */
  public fun pageBlobSize(pageBlobSize: String) {
    it.property("pageBlobSize", pageBlobSize)
  }

  /**
   * Specifies the maximum size for the page blob, up to 8 TB. The page blob size must be aligned to
   * a 512-byte boundary.
   */
  public fun pageBlobSize(pageBlobSize: Int) {
    it.property("pageBlobSize", pageBlobSize.toString())
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
   * Access key for the associated azure account name to be used for authentication with azure blob
   * services
   */
  public fun accessKey(accessKey: String) {
    it.property("accessKey", accessKey)
  }

  /**
   * Source Blob Access Key: for copyblob operation, sadly, we need to have an accessKey for the
   * source blob we want to copy Passing an accessKey as header, it's unsafe so we could set as key.
   */
  public fun sourceBlobAccessKey(sourceBlobAccessKey: String) {
    it.property("sourceBlobAccessKey", sourceBlobAccessKey)
  }
}
