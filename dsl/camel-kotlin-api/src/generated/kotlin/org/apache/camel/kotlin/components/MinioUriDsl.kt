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
 * Store and retrieve objects from Minio Storage Service using Minio SDK.
 */
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

  /**
   * Bucket name
   */
  public fun bucketName(bucketName: String) {
    this.bucketName = bucketName
    it.url("$bucketName")
  }

  /**
   * Setting the autocreation of the bucket if bucket name not exist.
   */
  public fun autoCreateBucket(autoCreateBucket: String) {
    it.property("autoCreateBucket", autoCreateBucket)
  }

  /**
   * Setting the autocreation of the bucket if bucket name not exist.
   */
  public fun autoCreateBucket(autoCreateBucket: Boolean) {
    it.property("autoCreateBucket", autoCreateBucket.toString())
  }

  /**
   * Endpoint can be an URL, domain name, IPv4 address or IPv6 address.
   */
  public fun endpoint(endpoint: String) {
    it.property("endpoint", endpoint)
  }

  /**
   * Reference to a Minio Client object in the registry.
   */
  public fun minioClient(minioClient: String) {
    it.property("minioClient", minioClient)
  }

  /**
   * Set when creating new bucket.
   */
  public fun objectLock(objectLock: String) {
    it.property("objectLock", objectLock)
  }

  /**
   * Set when creating new bucket.
   */
  public fun objectLock(objectLock: Boolean) {
    it.property("objectLock", objectLock.toString())
  }

  /**
   * The policy for this queue to set in the method.
   */
  public fun policy(policy: String) {
    it.property("policy", policy)
  }

  /**
   * TCP/IP port number. 80 and 443 are used as defaults for HTTP and HTTPS.
   */
  public fun proxyPort(proxyPort: String) {
    it.property("proxyPort", proxyPort)
  }

  /**
   * TCP/IP port number. 80 and 443 are used as defaults for HTTP and HTTPS.
   */
  public fun proxyPort(proxyPort: Int) {
    it.property("proxyPort", proxyPort.toString())
  }

  /**
   * The region in which Minio client needs to work. When using this parameter, the configuration
   * will expect the lowercase name of the region (for example ap-east-1). You'll need to use the name
   * Region.EU_WEST_1.id()
   */
  public fun region(region: String) {
    it.property("region", region)
  }

  /**
   * Flag to indicate to use secure connection to minio service or not.
   */
  public fun secure(secure: String) {
    it.property("secure", secure)
  }

  /**
   * Flag to indicate to use secure connection to minio service or not.
   */
  public fun secure(secure: Boolean) {
    it.property("secure", secure.toString())
  }

  /**
   * If this option is true and includeBody is true, then the MinioObject.close() method will be
   * called on exchange completion. This option is strongly related to includeBody option. In case of
   * setting includeBody to true and autocloseBody to false, it will be up to the caller to close the
   * MinioObject stream. Setting autocloseBody to true, will close the MinioObject stream
   * automatically.
   */
  public fun autoCloseBody(autoCloseBody: String) {
    it.property("autoCloseBody", autoCloseBody)
  }

  /**
   * If this option is true and includeBody is true, then the MinioObject.close() method will be
   * called on exchange completion. This option is strongly related to includeBody option. In case of
   * setting includeBody to true and autocloseBody to false, it will be up to the caller to close the
   * MinioObject stream. Setting autocloseBody to true, will close the MinioObject stream
   * automatically.
   */
  public fun autoCloseBody(autoCloseBody: Boolean) {
    it.property("autoCloseBody", autoCloseBody.toString())
  }

  /**
   * Set this flag if you want to bypassGovernanceMode when deleting a particular object.
   */
  public fun bypassGovernanceMode(bypassGovernanceMode: String) {
    it.property("bypassGovernanceMode", bypassGovernanceMode)
  }

  /**
   * Set this flag if you want to bypassGovernanceMode when deleting a particular object.
   */
  public fun bypassGovernanceMode(bypassGovernanceMode: Boolean) {
    it.property("bypassGovernanceMode", bypassGovernanceMode.toString())
  }

  /**
   * Delete objects from Minio after they have been retrieved. The delete is only performed if the
   * Exchange is committed. If a rollback occurs, the object is not deleted. If this option is false,
   * then the same objects will be retrieve over and over again on the polls. Therefore you need to use
   * the Idempotent Consumer EIP in the route to filter out duplicates. You can filter using the
   * MinioConstants#BUCKET_NAME and MinioConstants#OBJECT_NAME headers, or only the
   * MinioConstants#OBJECT_NAME header.
   */
  public fun deleteAfterRead(deleteAfterRead: String) {
    it.property("deleteAfterRead", deleteAfterRead)
  }

  /**
   * Delete objects from Minio after they have been retrieved. The delete is only performed if the
   * Exchange is committed. If a rollback occurs, the object is not deleted. If this option is false,
   * then the same objects will be retrieve over and over again on the polls. Therefore you need to use
   * the Idempotent Consumer EIP in the route to filter out duplicates. You can filter using the
   * MinioConstants#BUCKET_NAME and MinioConstants#OBJECT_NAME headers, or only the
   * MinioConstants#OBJECT_NAME header.
   */
  public fun deleteAfterRead(deleteAfterRead: Boolean) {
    it.property("deleteAfterRead", deleteAfterRead.toString())
  }

  /**
   * The delimiter which is used in the ListObjectsRequest to only consume objects we are interested
   * in.
   */
  public fun delimiter(delimiter: String) {
    it.property("delimiter", delimiter)
  }

  /**
   * Destination bucket name.
   */
  public fun destinationBucketName(destinationBucketName: String) {
    it.property("destinationBucketName", destinationBucketName)
  }

  /**
   * Destination object name.
   */
  public fun destinationObjectName(destinationObjectName: String) {
    it.property("destinationObjectName", destinationObjectName)
  }

  /**
   * If it is true, the exchange body will be set to a stream to the contents of the file. If false,
   * the headers will be set with the Minio object metadata, but the body will be null. This option is
   * strongly related to autocloseBody option. In case of setting includeBody to true and autocloseBody
   * to false, it will be up to the caller to close the MinioObject stream. Setting autocloseBody to
   * true, will close the MinioObject stream automatically.
   */
  public fun includeBody(includeBody: String) {
    it.property("includeBody", includeBody)
  }

  /**
   * If it is true, the exchange body will be set to a stream to the contents of the file. If false,
   * the headers will be set with the Minio object metadata, but the body will be null. This option is
   * strongly related to autocloseBody option. In case of setting includeBody to true and autocloseBody
   * to false, it will be up to the caller to close the MinioObject stream. Setting autocloseBody to
   * true, will close the MinioObject stream automatically.
   */
  public fun includeBody(includeBody: Boolean) {
    it.property("includeBody", includeBody.toString())
  }

  /**
   * The flag which is used in the ListObjectsRequest to set include folders.
   */
  public fun includeFolders(includeFolders: String) {
    it.property("includeFolders", includeFolders)
  }

  /**
   * The flag which is used in the ListObjectsRequest to set include folders.
   */
  public fun includeFolders(includeFolders: Boolean) {
    it.property("includeFolders", includeFolders.toString())
  }

  /**
   * The flag which is used in the ListObjectsRequest to get objects with user meta data.
   */
  public fun includeUserMetadata(includeUserMetadata: String) {
    it.property("includeUserMetadata", includeUserMetadata)
  }

  /**
   * The flag which is used in the ListObjectsRequest to get objects with user meta data.
   */
  public fun includeUserMetadata(includeUserMetadata: Boolean) {
    it.property("includeUserMetadata", includeUserMetadata.toString())
  }

  /**
   * The flag which is used in the ListObjectsRequest to get objects with versioning.
   */
  public fun includeVersions(includeVersions: String) {
    it.property("includeVersions", includeVersions)
  }

  /**
   * The flag which is used in the ListObjectsRequest to get objects with versioning.
   */
  public fun includeVersions(includeVersions: Boolean) {
    it.property("includeVersions", includeVersions.toString())
  }

  /**
   * Number of bytes of object data from offset.
   */
  public fun length(length: String) {
    it.property("length", length)
  }

  /**
   * Number of bytes of object data from offset.
   */
  public fun length(length: Int) {
    it.property("length", length.toString())
  }

  /**
   * Set match ETag parameter for get object(s).
   */
  public fun matchETag(matchETag: String) {
    it.property("matchETag", matchETag)
  }

  /**
   * Set the maxConnections parameter in the minio client configuration
   */
  public fun maxConnections(maxConnections: String) {
    it.property("maxConnections", maxConnections)
  }

  /**
   * Set the maxConnections parameter in the minio client configuration
   */
  public fun maxConnections(maxConnections: Int) {
    it.property("maxConnections", maxConnections.toString())
  }

  /**
   * Gets the maximum number of messages as a limit to poll at each polling. Gets the maximum number
   * of messages as a limit to poll at each polling. The default value is 10. Use 0 or a negative
   * number to set it as unlimited.
   */
  public fun maxMessagesPerPoll(maxMessagesPerPoll: String) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll)
  }

  /**
   * Gets the maximum number of messages as a limit to poll at each polling. Gets the maximum number
   * of messages as a limit to poll at each polling. The default value is 10. Use 0 or a negative
   * number to set it as unlimited.
   */
  public fun maxMessagesPerPoll(maxMessagesPerPoll: Int) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll.toString())
  }

  /**
   * Set modified since parameter for get object(s).
   */
  public fun modifiedSince(modifiedSince: String) {
    it.property("modifiedSince", modifiedSince)
  }

  /**
   * Move objects from bucket to a different bucket after they have been retrieved. To accomplish
   * the operation the destinationBucket option must be set. The copy bucket operation is only
   * performed if the Exchange is committed. If a rollback occurs, the object is not moved.
   */
  public fun moveAfterRead(moveAfterRead: String) {
    it.property("moveAfterRead", moveAfterRead)
  }

  /**
   * Move objects from bucket to a different bucket after they have been retrieved. To accomplish
   * the operation the destinationBucket option must be set. The copy bucket operation is only
   * performed if the Exchange is committed. If a rollback occurs, the object is not moved.
   */
  public fun moveAfterRead(moveAfterRead: Boolean) {
    it.property("moveAfterRead", moveAfterRead.toString())
  }

  /**
   * Set not match ETag parameter for get object(s).
   */
  public fun notMatchETag(notMatchETag: String) {
    it.property("notMatchETag", notMatchETag)
  }

  /**
   * To get the object from the bucket with the given object name.
   */
  public fun objectName(objectName: String) {
    it.property("objectName", objectName)
  }

  /**
   * Start byte position of object data.
   */
  public fun offset(offset: String) {
    it.property("offset", offset)
  }

  /**
   * Start byte position of object data.
   */
  public fun offset(offset: Int) {
    it.property("offset", offset.toString())
  }

  /**
   * Object name starts with prefix.
   */
  public fun prefix(prefix: String) {
    it.property("prefix", prefix)
  }

  /**
   * List recursively than directory structure emulation.
   */
  public fun recursive(recursive: String) {
    it.property("recursive", recursive)
  }

  /**
   * List recursively than directory structure emulation.
   */
  public fun recursive(recursive: Boolean) {
    it.property("recursive", recursive.toString())
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
   * list objects in bucket after this object name.
   */
  public fun startAfter(startAfter: String) {
    it.property("startAfter", startAfter)
  }

  /**
   * Set un modified since parameter for get object(s).
   */
  public fun unModifiedSince(unModifiedSince: String) {
    it.property("unModifiedSince", unModifiedSince)
  }

  /**
   * when true, version 1 of REST API is used.
   */
  public fun useVersion1(useVersion1: String) {
    it.property("useVersion1", useVersion1)
  }

  /**
   * when true, version 1 of REST API is used.
   */
  public fun useVersion1(useVersion1: Boolean) {
    it.property("useVersion1", useVersion1.toString())
  }

  /**
   * Set specific version_ID of a object when deleting the object.
   */
  public fun versionId(versionId: String) {
    it.property("versionId", versionId)
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
   * Delete file object after the Minio file has been uploaded.
   */
  public fun deleteAfterWrite(deleteAfterWrite: String) {
    it.property("deleteAfterWrite", deleteAfterWrite)
  }

  /**
   * Delete file object after the Minio file has been uploaded.
   */
  public fun deleteAfterWrite(deleteAfterWrite: Boolean) {
    it.property("deleteAfterWrite", deleteAfterWrite.toString())
  }

  /**
   * Setting the key name for an element in the bucket through endpoint parameter.
   */
  public fun keyName(keyName: String) {
    it.property("keyName", keyName)
  }

  /**
   * The operation to do in case the user don't want to do only an upload.
   */
  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  /**
   * If we want to use a POJO request as body or not.
   */
  public fun pojoRequest(pojoRequest: String) {
    it.property("pojoRequest", pojoRequest)
  }

  /**
   * If we want to use a POJO request as body or not.
   */
  public fun pojoRequest(pojoRequest: Boolean) {
    it.property("pojoRequest", pojoRequest.toString())
  }

  /**
   * The storage class to set in the request.
   */
  public fun storageClass(storageClass: String) {
    it.property("storageClass", storageClass)
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
   * Set custom HTTP client for authenticated access.
   */
  public fun customHttpClient(customHttpClient: String) {
    it.property("customHttpClient", customHttpClient)
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
   * Amazon AWS Secret Access Key or Minio Access Key. If not set camel will connect to service for
   * anonymous access.
   */
  public fun accessKey(accessKey: String) {
    it.property("accessKey", accessKey)
  }

  /**
   * Amazon AWS Access Key Id or Minio Secret Key. If not set camel will connect to service for
   * anonymous access.
   */
  public fun secretKey(secretKey: String) {
    it.property("secretKey", secretKey)
  }

  /**
   * Server-side encryption.
   */
  public fun serverSideEncryption(serverSideEncryption: String) {
    it.property("serverSideEncryption", serverSideEncryption)
  }

  /**
   * Server-side encryption for source object while copy/move objects.
   */
  public fun serverSideEncryptionCustomerKey(serverSideEncryptionCustomerKey: String) {
    it.property("serverSideEncryptionCustomerKey", serverSideEncryptionCustomerKey)
  }
}
