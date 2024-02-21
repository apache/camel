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

  public fun accountName(accountName: String) {
    this.accountName = accountName
    it.url("$accountName/$fileSystemName")
  }

  public fun fileSystemName(fileSystemName: String) {
    this.fileSystemName = fileSystemName
    it.url("$accountName/$fileSystemName")
  }

  public fun clientId(clientId: String) {
    it.property("clientId", clientId)
  }

  public fun close(close: String) {
    it.property("close", close)
  }

  public fun close(close: Boolean) {
    it.property("close", close.toString())
  }

  public fun closeStreamAfterRead(closeStreamAfterRead: String) {
    it.property("closeStreamAfterRead", closeStreamAfterRead)
  }

  public fun closeStreamAfterRead(closeStreamAfterRead: Boolean) {
    it.property("closeStreamAfterRead", closeStreamAfterRead.toString())
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

  public fun dataLakeServiceClient(dataLakeServiceClient: String) {
    it.property("dataLakeServiceClient", dataLakeServiceClient)
  }

  public fun directoryName(directoryName: String) {
    it.property("directoryName", directoryName)
  }

  public fun downloadLinkExpiration(downloadLinkExpiration: String) {
    it.property("downloadLinkExpiration", downloadLinkExpiration)
  }

  public fun downloadLinkExpiration(downloadLinkExpiration: Int) {
    it.property("downloadLinkExpiration", downloadLinkExpiration.toString())
  }

  public fun expression(expression: String) {
    it.property("expression", expression)
  }

  public fun fileDir(fileDir: String) {
    it.property("fileDir", fileDir)
  }

  public fun fileName(fileName: String) {
    it.property("fileName", fileName)
  }

  public fun fileOffset(fileOffset: String) {
    it.property("fileOffset", fileOffset)
  }

  public fun fileOffset(fileOffset: Int) {
    it.property("fileOffset", fileOffset.toString())
  }

  public fun maxResults(maxResults: String) {
    it.property("maxResults", maxResults)
  }

  public fun maxResults(maxResults: Int) {
    it.property("maxResults", maxResults.toString())
  }

  public fun maxRetryRequests(maxRetryRequests: String) {
    it.property("maxRetryRequests", maxRetryRequests)
  }

  public fun maxRetryRequests(maxRetryRequests: Int) {
    it.property("maxRetryRequests", maxRetryRequests.toString())
  }

  public fun openOptions(openOptions: String) {
    it.property("openOptions", openOptions)
  }

  public fun path(path: String) {
    it.property("path", path)
  }

  public fun permission(permission: String) {
    it.property("permission", permission)
  }

  public fun position(position: String) {
    it.property("position", position)
  }

  public fun position(position: Int) {
    it.property("position", position.toString())
  }

  public fun recursive(recursive: String) {
    it.property("recursive", recursive)
  }

  public fun recursive(recursive: Boolean) {
    it.property("recursive", recursive.toString())
  }

  public fun regex(regex: String) {
    it.property("regex", regex)
  }

  public fun retainUncommitedData(retainUncommitedData: String) {
    it.property("retainUncommitedData", retainUncommitedData)
  }

  public fun retainUncommitedData(retainUncommitedData: Boolean) {
    it.property("retainUncommitedData", retainUncommitedData.toString())
  }

  public fun serviceClient(serviceClient: String) {
    it.property("serviceClient", serviceClient)
  }

  public fun sharedKeyCredential(sharedKeyCredential: String) {
    it.property("sharedKeyCredential", sharedKeyCredential)
  }

  public fun tenantId(tenantId: String) {
    it.property("tenantId", tenantId)
  }

  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  public fun umask(umask: String) {
    it.property("umask", umask)
  }

  public fun userPrincipalNameReturned(userPrincipalNameReturned: String) {
    it.property("userPrincipalNameReturned", userPrincipalNameReturned)
  }

  public fun userPrincipalNameReturned(userPrincipalNameReturned: Boolean) {
    it.property("userPrincipalNameReturned", userPrincipalNameReturned.toString())
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

  public fun operation(operation: String) {
    it.property("operation", operation)
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

  public fun accountKey(accountKey: String) {
    it.property("accountKey", accountKey)
  }

  public fun clientSecret(clientSecret: String) {
    it.property("clientSecret", clientSecret)
  }

  public fun clientSecretCredential(clientSecretCredential: String) {
    it.property("clientSecretCredential", clientSecretCredential)
  }

  public fun sasCredential(sasCredential: String) {
    it.property("sasCredential", sasCredential)
  }

  public fun sasSignature(sasSignature: String) {
    it.property("sasSignature", sasSignature)
  }
}
