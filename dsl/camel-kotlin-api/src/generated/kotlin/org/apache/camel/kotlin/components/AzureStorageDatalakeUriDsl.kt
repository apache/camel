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
 * Sends and receives files to/from Azure Data Lake Storage.
 */
public fun UriDsl.`azure-storage-datalake`(i: AzureStorageDatalakeUriDsl.() -> Unit) {
  AzureStorageDatalakeUriDsl(this).apply(i)
}

@CamelDslMarker
public class AzureStorageDatalakeUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("azure-storage-datalake")
  }

  private var accountName: String = ""

  private var fileSystemName: String = ""

  /**
   * name of the azure account
   */
  public fun accountName(accountName: String) {
    this.accountName = accountName
    it.url("$accountName/$fileSystemName")
  }

  /**
   * name of filesystem to be used
   */
  public fun fileSystemName(fileSystemName: String) {
    this.fileSystemName = fileSystemName
    it.url("$accountName/$fileSystemName")
  }

  /**
   * client id for azure account
   */
  public fun clientId(clientId: String) {
    it.property("clientId", clientId)
  }

  /**
   * Whether or not a file changed event raised indicates completion (true) or modification (false)
   */
  public fun close(close: String) {
    it.property("close", close)
  }

  /**
   * Whether or not a file changed event raised indicates completion (true) or modification (false)
   */
  public fun close(close: Boolean) {
    it.property("close", close.toString())
  }

  /**
   * check for closing stream after read
   */
  public fun closeStreamAfterRead(closeStreamAfterRead: String) {
    it.property("closeStreamAfterRead", closeStreamAfterRead)
  }

  /**
   * check for closing stream after read
   */
  public fun closeStreamAfterRead(closeStreamAfterRead: Boolean) {
    it.property("closeStreamAfterRead", closeStreamAfterRead.toString())
  }

  /**
   * Determines the credential strategy to adopt
   */
  public fun credentialType(credentialType: String) {
    it.property("credentialType", credentialType)
  }

  /**
   * count number of bytes to download
   */
  public fun dataCount(dataCount: String) {
    it.property("dataCount", dataCount)
  }

  /**
   * count number of bytes to download
   */
  public fun dataCount(dataCount: Int) {
    it.property("dataCount", dataCount.toString())
  }

  /**
   * service client of data lake
   */
  public fun dataLakeServiceClient(dataLakeServiceClient: String) {
    it.property("dataLakeServiceClient", dataLakeServiceClient)
  }

  /**
   * directory of the file to be handled in component
   */
  public fun directoryName(directoryName: String) {
    it.property("directoryName", directoryName)
  }

  /**
   * download link expiration time
   */
  public fun downloadLinkExpiration(downloadLinkExpiration: String) {
    it.property("downloadLinkExpiration", downloadLinkExpiration)
  }

  /**
   * download link expiration time
   */
  public fun downloadLinkExpiration(downloadLinkExpiration: Int) {
    it.property("downloadLinkExpiration", downloadLinkExpiration.toString())
  }

  /**
   * expression for queryInputStream
   */
  public fun expression(expression: String) {
    it.property("expression", expression)
  }

  /**
   * directory of file to do operations in the local system
   */
  public fun fileDir(fileDir: String) {
    it.property("fileDir", fileDir)
  }

  /**
   * name of file to be handled in component
   */
  public fun fileName(fileName: String) {
    it.property("fileName", fileName)
  }

  /**
   * offset position in file for different operations
   */
  public fun fileOffset(fileOffset: String) {
    it.property("fileOffset", fileOffset)
  }

  /**
   * offset position in file for different operations
   */
  public fun fileOffset(fileOffset: Int) {
    it.property("fileOffset", fileOffset.toString())
  }

  /**
   * maximum number of results to show at a time
   */
  public fun maxResults(maxResults: String) {
    it.property("maxResults", maxResults)
  }

  /**
   * maximum number of results to show at a time
   */
  public fun maxResults(maxResults: Int) {
    it.property("maxResults", maxResults.toString())
  }

  /**
   * no of retries to a given request
   */
  public fun maxRetryRequests(maxRetryRequests: String) {
    it.property("maxRetryRequests", maxRetryRequests)
  }

  /**
   * no of retries to a given request
   */
  public fun maxRetryRequests(maxRetryRequests: Int) {
    it.property("maxRetryRequests", maxRetryRequests.toString())
  }

  /**
   * set open options for creating file
   */
  public fun openOptions(openOptions: String) {
    it.property("openOptions", openOptions)
  }

  /**
   * path in azure data lake for operations
   */
  public fun path(path: String) {
    it.property("path", path)
  }

  /**
   * permission string for the file
   */
  public fun permission(permission: String) {
    it.property("permission", permission)
  }

  /**
   * This parameter allows the caller to upload data in parallel and control the order in which it
   * is appended to the file.
   */
  public fun position(position: String) {
    it.property("position", position)
  }

  /**
   * This parameter allows the caller to upload data in parallel and control the order in which it
   * is appended to the file.
   */
  public fun position(position: Int) {
    it.property("position", position.toString())
  }

  /**
   * recursively include all paths
   */
  public fun recursive(recursive: String) {
    it.property("recursive", recursive)
  }

  /**
   * recursively include all paths
   */
  public fun recursive(recursive: Boolean) {
    it.property("recursive", recursive.toString())
  }

  /**
   * regular expression for matching file names
   */
  public fun regex(regex: String) {
    it.property("regex", regex)
  }

  /**
   * Whether or not uncommitted data is to be retained after the operation
   */
  public fun retainUncommitedData(retainUncommitedData: String) {
    it.property("retainUncommitedData", retainUncommitedData)
  }

  /**
   * Whether or not uncommitted data is to be retained after the operation
   */
  public fun retainUncommitedData(retainUncommitedData: Boolean) {
    it.property("retainUncommitedData", retainUncommitedData.toString())
  }

  /**
   * data lake service client for azure storage data lake
   */
  public fun serviceClient(serviceClient: String) {
    it.property("serviceClient", serviceClient)
  }

  /**
   * shared key credential for azure data lake gen2
   */
  public fun sharedKeyCredential(sharedKeyCredential: String) {
    it.property("sharedKeyCredential", sharedKeyCredential)
  }

  /**
   * tenant id for azure account
   */
  public fun tenantId(tenantId: String) {
    it.property("tenantId", tenantId)
  }

  /**
   * Timeout for operation
   */
  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  /**
   * umask permission for file
   */
  public fun umask(umask: String) {
    it.property("umask", umask)
  }

  /**
   * whether or not to use upn
   */
  public fun userPrincipalNameReturned(userPrincipalNameReturned: String) {
    it.property("userPrincipalNameReturned", userPrincipalNameReturned)
  }

  /**
   * whether or not to use upn
   */
  public fun userPrincipalNameReturned(userPrincipalNameReturned: Boolean) {
    it.property("userPrincipalNameReturned", userPrincipalNameReturned.toString())
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
   * operation to be performed
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
   * account key for authentication
   */
  public fun accountKey(accountKey: String) {
    it.property("accountKey", accountKey)
  }

  /**
   * client secret for azure account
   */
  public fun clientSecret(clientSecret: String) {
    it.property("clientSecret", clientSecret)
  }

  /**
   * client secret credential for authentication
   */
  public fun clientSecretCredential(clientSecretCredential: String) {
    it.property("clientSecretCredential", clientSecretCredential)
  }

  /**
   * SAS token credential
   */
  public fun sasCredential(sasCredential: String) {
    it.property("sasCredential", sasCredential)
  }

  /**
   * SAS token signature
   */
  public fun sasSignature(sasSignature: String) {
    it.property("sasSignature", sasSignature)
  }
}
