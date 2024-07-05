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
 * Store and retrieve objects from AWS S3 Storage Service.
 */
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

  /**
   * Bucket name or ARN
   */
  public fun bucketNameOrArn(bucketNameOrArn: String) {
    this.bucketNameOrArn = bucketNameOrArn
    it.url("//$bucketNameOrArn")
  }

  /**
   * Setting the autocreation of the S3 bucket bucketName. This will apply also in case of
   * moveAfterRead option enabled, and it will create the destinationBucket if it doesn't exist
   * already.
   */
  public fun autoCreateBucket(autoCreateBucket: String) {
    it.property("autoCreateBucket", autoCreateBucket)
  }

  /**
   * Setting the autocreation of the S3 bucket bucketName. This will apply also in case of
   * moveAfterRead option enabled, and it will create the destinationBucket if it doesn't exist
   * already.
   */
  public fun autoCreateBucket(autoCreateBucket: Boolean) {
    it.property("autoCreateBucket", autoCreateBucket.toString())
  }

  /**
   * The delimiter which is used in the com.amazonaws.services.s3.model.ListObjectsRequest to only
   * consume objects we are interested in.
   */
  public fun delimiter(delimiter: String) {
    it.property("delimiter", delimiter)
  }

  /**
   * Set whether the S3 client should use path-style URL instead of virtual-hosted-style
   */
  public fun forcePathStyle(forcePathStyle: String) {
    it.property("forcePathStyle", forcePathStyle)
  }

  /**
   * Set whether the S3 client should use path-style URL instead of virtual-hosted-style
   */
  public fun forcePathStyle(forcePathStyle: Boolean) {
    it.property("forcePathStyle", forcePathStyle.toString())
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
   * If we want to use a POJO request as body or not
   */
  public fun pojoRequest(pojoRequest: String) {
    it.property("pojoRequest", pojoRequest)
  }

  /**
   * If we want to use a POJO request as body or not
   */
  public fun pojoRequest(pojoRequest: Boolean) {
    it.property("pojoRequest", pojoRequest.toString())
  }

  /**
   * The policy for this queue to set in the com.amazonaws.services.s3.AmazonS3#setBucketPolicy()
   * method.
   */
  public fun policy(policy: String) {
    it.property("policy", policy)
  }

  /**
   * The prefix which is used in the com.amazonaws.services.s3.model.ListObjectsRequest to only
   * consume objects we are interested in.
   */
  public fun prefix(prefix: String) {
    it.property("prefix", prefix)
  }

  /**
   * The region in which the S3 client needs to work. When using this parameter, the configuration
   * will expect the lowercase name of the region (for example, ap-east-1) You'll need to use the name
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
   * Define the customer algorithm to use in case CustomerKey is enabled
   */
  public fun customerAlgorithm(customerAlgorithm: String) {
    it.property("customerAlgorithm", customerAlgorithm)
  }

  /**
   * Define the id of the Customer key to use in case CustomerKey is enabled
   */
  public fun customerKeyId(customerKeyId: String) {
    it.property("customerKeyId", customerKeyId)
  }

  /**
   * Define the MD5 of Customer key to use in case CustomerKey is enabled
   */
  public fun customerKeyMD5(customerKeyMD5: String) {
    it.property("customerKeyMD5", customerKeyMD5)
  }

  /**
   * Delete objects from S3 after they have been retrieved. The deleting is only performed if the
   * Exchange is committed. If a rollback occurs, the object is not deleted. If this option is false,
   * then the same objects will be retrieved over and over again in the polls. Therefore, you need to
   * use the Idempotent Consumer EIP in the route to filter out duplicates. You can filter using the
   * AWS2S3Constants#BUCKET_NAME and AWS2S3Constants#KEY headers, or only the AWS2S3Constants#KEY
   * header.
   */
  public fun deleteAfterRead(deleteAfterRead: String) {
    it.property("deleteAfterRead", deleteAfterRead)
  }

  /**
   * Delete objects from S3 after they have been retrieved. The deleting is only performed if the
   * Exchange is committed. If a rollback occurs, the object is not deleted. If this option is false,
   * then the same objects will be retrieved over and over again in the polls. Therefore, you need to
   * use the Idempotent Consumer EIP in the route to filter out duplicates. You can filter using the
   * AWS2S3Constants#BUCKET_NAME and AWS2S3Constants#KEY headers, or only the AWS2S3Constants#KEY
   * header.
   */
  public fun deleteAfterRead(deleteAfterRead: Boolean) {
    it.property("deleteAfterRead", deleteAfterRead.toString())
  }

  /**
   * Define the destination bucket where an object must be moved when moveAfterRead is set to true.
   */
  public fun destinationBucket(destinationBucket: String) {
    it.property("destinationBucket", destinationBucket)
  }

  /**
   * Define the destination bucket prefix to use when an object must be moved, and moveAfterRead is
   * set to true.
   */
  public fun destinationBucketPrefix(destinationBucketPrefix: String) {
    it.property("destinationBucketPrefix", destinationBucketPrefix)
  }

  /**
   * Define the destination bucket suffix to use when an object must be moved, and moveAfterRead is
   * set to true.
   */
  public fun destinationBucketSuffix(destinationBucketSuffix: String) {
    it.property("destinationBucketSuffix", destinationBucketSuffix)
  }

  /**
   * If provided, Camel will only consume files if a done file exists.
   */
  public fun doneFileName(doneFileName: String) {
    it.property("doneFileName", doneFileName)
  }

  /**
   * To get the object from the bucket with the given file name
   */
  public fun fileName(fileName: String) {
    it.property("fileName", fileName)
  }

  /**
   * If it is true, the S3 Object Body will be ignored completely if it is set to false, the S3
   * Object will be put in the body. Setting this to true will override any behavior defined by
   * includeBody option.
   */
  public fun ignoreBody(ignoreBody: String) {
    it.property("ignoreBody", ignoreBody)
  }

  /**
   * If it is true, the S3 Object Body will be ignored completely if it is set to false, the S3
   * Object will be put in the body. Setting this to true will override any behavior defined by
   * includeBody option.
   */
  public fun ignoreBody(ignoreBody: Boolean) {
    it.property("ignoreBody", ignoreBody.toString())
  }

  /**
   * If it is true, the S3Object exchange will be consumed and put into the body and closed. If
   * false, the S3Object stream will be put raw into the body and the headers will be set with the S3
   * object metadata. This option is strongly related to the autocloseBody option. In case of setting
   * includeBody to true because the S3Object stream will be consumed then it will also be closed,
   * while in case of includeBody false then it will be up to the caller to close the S3Object stream.
   * However, setting autocloseBody to true when includeBody is false it will schedule to close the
   * S3Object stream automatically on exchange completion.
   */
  public fun includeBody(includeBody: String) {
    it.property("includeBody", includeBody)
  }

  /**
   * If it is true, the S3Object exchange will be consumed and put into the body and closed. If
   * false, the S3Object stream will be put raw into the body and the headers will be set with the S3
   * object metadata. This option is strongly related to the autocloseBody option. In case of setting
   * includeBody to true because the S3Object stream will be consumed then it will also be closed,
   * while in case of includeBody false then it will be up to the caller to close the S3Object stream.
   * However, setting autocloseBody to true when includeBody is false it will schedule to close the
   * S3Object stream automatically on exchange completion.
   */
  public fun includeBody(includeBody: Boolean) {
    it.property("includeBody", includeBody.toString())
  }

  /**
   * If it is true, the folders/directories will be consumed. If it is false, they will be ignored,
   * and Exchanges will not be created for those
   */
  public fun includeFolders(includeFolders: String) {
    it.property("includeFolders", includeFolders)
  }

  /**
   * If it is true, the folders/directories will be consumed. If it is false, they will be ignored,
   * and Exchanges will not be created for those
   */
  public fun includeFolders(includeFolders: Boolean) {
    it.property("includeFolders", includeFolders.toString())
  }

  /**
   * Set the maxConnections parameter in the S3 client configuration
   */
  public fun maxConnections(maxConnections: String) {
    it.property("maxConnections", maxConnections)
  }

  /**
   * Set the maxConnections parameter in the S3 client configuration
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
   * Move objects from S3 bucket to a different bucket after they have been retrieved. To accomplish
   * the operation, the destinationBucket option must be set. The copy bucket operation is only
   * performed if the Exchange is committed. If a rollback occurs, the object is not moved.
   */
  public fun moveAfterRead(moveAfterRead: String) {
    it.property("moveAfterRead", moveAfterRead)
  }

  /**
   * Move objects from S3 bucket to a different bucket after they have been retrieved. To accomplish
   * the operation, the destinationBucket option must be set. The copy bucket operation is only
   * performed if the Exchange is committed. If a rollback occurs, the object is not moved.
   */
  public fun moveAfterRead(moveAfterRead: Boolean) {
    it.property("moveAfterRead", moveAfterRead.toString())
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
   * If this option is true and includeBody is false, then the S3Object.close() method will be
   * called on exchange completion. This option is strongly related to includeBody option. In case of
   * setting includeBody to false and autocloseBody to false, it will be up to the caller to close the
   * S3Object stream. Setting autocloseBody to true, will close the S3Object stream automatically.
   */
  public fun autocloseBody(autocloseBody: String) {
    it.property("autocloseBody", autocloseBody)
  }

  /**
   * If this option is true and includeBody is false, then the S3Object.close() method will be
   * called on exchange completion. This option is strongly related to includeBody option. In case of
   * setting includeBody to false and autocloseBody to false, it will be up to the caller to close the
   * S3Object stream. Setting autocloseBody to true, will close the S3Object stream automatically.
   */
  public fun autocloseBody(autocloseBody: Boolean) {
    it.property("autocloseBody", autocloseBody.toString())
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
   * The number of messages composing a batch in streaming upload mode
   */
  public fun batchMessageNumber(batchMessageNumber: String) {
    it.property("batchMessageNumber", batchMessageNumber)
  }

  /**
   * The number of messages composing a batch in streaming upload mode
   */
  public fun batchMessageNumber(batchMessageNumber: Int) {
    it.property("batchMessageNumber", batchMessageNumber.toString())
  }

  /**
   * The batch size (in bytes) in streaming upload mode
   */
  public fun batchSize(batchSize: String) {
    it.property("batchSize", batchSize)
  }

  /**
   * The batch size (in bytes) in streaming upload mode
   */
  public fun batchSize(batchSize: Int) {
    it.property("batchSize", batchSize.toString())
  }

  /**
   * The buffer size (in bytes) in streaming upload mode
   */
  public fun bufferSize(bufferSize: String) {
    it.property("bufferSize", bufferSize)
  }

  /**
   * The buffer size (in bytes) in streaming upload mode
   */
  public fun bufferSize(bufferSize: Int) {
    it.property("bufferSize", bufferSize.toString())
  }

  /**
   * Delete file object after the S3 file has been uploaded
   */
  public fun deleteAfterWrite(deleteAfterWrite: String) {
    it.property("deleteAfterWrite", deleteAfterWrite)
  }

  /**
   * Delete file object after the S3 file has been uploaded
   */
  public fun deleteAfterWrite(deleteAfterWrite: Boolean) {
    it.property("deleteAfterWrite", deleteAfterWrite.toString())
  }

  /**
   * Setting the key name for an element in the bucket through endpoint parameter
   */
  public fun keyName(keyName: String) {
    it.property("keyName", keyName)
  }

  /**
   * If it is true, camel will upload the file with multipart format. The part size is decided by
   * the partSize option. Camel will only do multipart uploads for files that are larger than the
   * part-size thresholds. Files that are smaller will be uploaded in a single operation.
   */
  public fun multiPartUpload(multiPartUpload: String) {
    it.property("multiPartUpload", multiPartUpload)
  }

  /**
   * If it is true, camel will upload the file with multipart format. The part size is decided by
   * the partSize option. Camel will only do multipart uploads for files that are larger than the
   * part-size thresholds. Files that are smaller will be uploaded in a single operation.
   */
  public fun multiPartUpload(multiPartUpload: Boolean) {
    it.property("multiPartUpload", multiPartUpload.toString())
  }

  /**
   * The naming strategy to use in streaming upload mode
   */
  public fun namingStrategy(namingStrategy: String) {
    it.property("namingStrategy", namingStrategy)
  }

  /**
   * The operation to do in case the user don't want to do only an upload
   */
  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  /**
   * Set up the partSize which is used in multipart upload, the default size is 25M. Camel will only
   * do multipart uploads for files that are larger than the part-size thresholds. Files that are
   * smaller will be uploaded in a single operation.
   */
  public fun partSize(partSize: String) {
    it.property("partSize", partSize)
  }

  /**
   * Set up the partSize which is used in multipart upload, the default size is 25M. Camel will only
   * do multipart uploads for files that are larger than the part-size thresholds. Files that are
   * smaller will be uploaded in a single operation.
   */
  public fun partSize(partSize: Int) {
    it.property("partSize", partSize.toString())
  }

  /**
   * The restarting policy to use in streaming upload mode
   */
  public fun restartingPolicy(restartingPolicy: String) {
    it.property("restartingPolicy", restartingPolicy)
  }

  /**
   * The storage class to set in the com.amazonaws.services.s3.model.PutObjectRequest request.
   */
  public fun storageClass(storageClass: String) {
    it.property("storageClass", storageClass)
  }

  /**
   * When stream mode is true, the upload to bucket will be done in streaming
   */
  public fun streamingUploadMode(streamingUploadMode: String) {
    it.property("streamingUploadMode", streamingUploadMode)
  }

  /**
   * When stream mode is true, the upload to bucket will be done in streaming
   */
  public fun streamingUploadMode(streamingUploadMode: Boolean) {
    it.property("streamingUploadMode", streamingUploadMode.toString())
  }

  /**
   * While streaming upload mode is true, this option set the timeout to complete upload
   */
  public fun streamingUploadTimeout(streamingUploadTimeout: String) {
    it.property("streamingUploadTimeout", streamingUploadTimeout)
  }

  /**
   * While streaming upload mode is true, this option set the timeout to complete upload
   */
  public fun streamingUploadTimeout(streamingUploadTimeout: Int) {
    it.property("streamingUploadTimeout", streamingUploadTimeout.toString())
  }

  /**
   * Define the id of KMS key to use in case KMS is enabled
   */
  public fun awsKMSKeyId(awsKMSKeyId: String) {
    it.property("awsKMSKeyId", awsKMSKeyId)
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
   * Define if KMS must be used or not
   */
  public fun useAwsKMS(useAwsKMS: String) {
    it.property("useAwsKMS", useAwsKMS)
  }

  /**
   * Define if KMS must be used or not
   */
  public fun useAwsKMS(useAwsKMS: Boolean) {
    it.property("useAwsKMS", useAwsKMS.toString())
  }

  /**
   * Define if Customer Key must be used or not
   */
  public fun useCustomerKey(useCustomerKey: String) {
    it.property("useCustomerKey", useCustomerKey)
  }

  /**
   * Define if Customer Key must be used or not
   */
  public fun useCustomerKey(useCustomerKey: Boolean) {
    it.property("useCustomerKey", useCustomerKey.toString())
  }

  /**
   * Define if SSE S3 must be used or not
   */
  public fun useSSES3(useSSES3: String) {
    it.property("useSSES3", useSSES3)
  }

  /**
   * Define if SSE S3 must be used or not
   */
  public fun useSSES3(useSSES3: Boolean) {
    it.property("useSSES3", useSSES3.toString())
  }

  /**
   * Reference to a com.amazonaws.services.s3.AmazonS3 in the registry.
   */
  public fun amazonS3Client(amazonS3Client: String) {
    it.property("amazonS3Client", amazonS3Client)
  }

  /**
   * An S3 Presigner for Request, used mainly in createDownloadLink operation
   */
  public fun amazonS3Presigner(amazonS3Presigner: String) {
    it.property("amazonS3Presigner", amazonS3Presigner)
  }

  /**
   * To define a proxy host when instantiating the SQS client
   */
  public fun proxyHost(proxyHost: String) {
    it.property("proxyHost", proxyHost)
  }

  /**
   * Specify a proxy port to be used inside the client definition.
   */
  public fun proxyPort(proxyPort: String) {
    it.property("proxyPort", proxyPort)
  }

  /**
   * Specify a proxy port to be used inside the client definition.
   */
  public fun proxyPort(proxyPort: Int) {
    it.property("proxyPort", proxyPort.toString())
  }

  /**
   * To define a proxy protocol when instantiating the S3 client
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
   * Set whether the S3 client should expect to load credentials through a default credentials
   * provider.
   */
  public fun useDefaultCredentialsProvider(useDefaultCredentialsProvider: String) {
    it.property("useDefaultCredentialsProvider", useDefaultCredentialsProvider)
  }

  /**
   * Set whether the S3 client should expect to load credentials through a default credentials
   * provider.
   */
  public fun useDefaultCredentialsProvider(useDefaultCredentialsProvider: Boolean) {
    it.property("useDefaultCredentialsProvider", useDefaultCredentialsProvider.toString())
  }

  /**
   * Set whether the S3 client should expect to load credentials through a profile credentials
   * provider.
   */
  public fun useProfileCredentialsProvider(useProfileCredentialsProvider: String) {
    it.property("useProfileCredentialsProvider", useProfileCredentialsProvider)
  }

  /**
   * Set whether the S3 client should expect to load credentials through a profile credentials
   * provider.
   */
  public fun useProfileCredentialsProvider(useProfileCredentialsProvider: Boolean) {
    it.property("useProfileCredentialsProvider", useProfileCredentialsProvider.toString())
  }

  /**
   * Set whether the S3 client should expect to use Session Credentials. This is useful in a
   * situation in which the user needs to assume an IAM role for doing operations in S3.
   */
  public fun useSessionCredentials(useSessionCredentials: String) {
    it.property("useSessionCredentials", useSessionCredentials)
  }

  /**
   * Set whether the S3 client should expect to use Session Credentials. This is useful in a
   * situation in which the user needs to assume an IAM role for doing operations in S3.
   */
  public fun useSessionCredentials(useSessionCredentials: Boolean) {
    it.property("useSessionCredentials", useSessionCredentials.toString())
  }
}
