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

public fun UriDsl.sftp(i: SftpUriDsl.() -> Unit) {
  SftpUriDsl(this).apply(i)
}

@CamelDslMarker
public class SftpUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("sftp")
  }

  private var host: String = ""

  private var port: String = ""

  private var directoryName: String = ""

  public fun host(host: String) {
    this.host = host
    it.url("$host:$port/$directoryName")
  }

  public fun port(port: String) {
    this.port = port
    it.url("$host:$port/$directoryName")
  }

  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$host:$port/$directoryName")
  }

  public fun directoryName(directoryName: String) {
    this.directoryName = directoryName
    it.url("$host:$port/$directoryName")
  }

  public fun binary(binary: String) {
    it.property("binary", binary)
  }

  public fun binary(binary: Boolean) {
    it.property("binary", binary.toString())
  }

  public fun charset(charset: String) {
    it.property("charset", charset)
  }

  public fun disconnect(disconnect: String) {
    it.property("disconnect", disconnect)
  }

  public fun disconnect(disconnect: Boolean) {
    it.property("disconnect", disconnect.toString())
  }

  public fun doneFileName(doneFileName: String) {
    it.property("doneFileName", doneFileName)
  }

  public fun fileName(fileName: String) {
    it.property("fileName", fileName)
  }

  public fun jschLoggingLevel(jschLoggingLevel: String) {
    it.property("jschLoggingLevel", jschLoggingLevel)
  }

  public fun passiveMode(passiveMode: String) {
    it.property("passiveMode", passiveMode)
  }

  public fun passiveMode(passiveMode: Boolean) {
    it.property("passiveMode", passiveMode.toString())
  }

  public fun separator(separator: String) {
    it.property("separator", separator)
  }

  public fun fastExistsCheck(fastExistsCheck: String) {
    it.property("fastExistsCheck", fastExistsCheck)
  }

  public fun fastExistsCheck(fastExistsCheck: Boolean) {
    it.property("fastExistsCheck", fastExistsCheck.toString())
  }

  public fun delete(delete: String) {
    it.property("delete", delete)
  }

  public fun delete(delete: Boolean) {
    it.property("delete", delete.toString())
  }

  public fun moveFailed(moveFailed: String) {
    it.property("moveFailed", moveFailed)
  }

  public fun noop(noop: String) {
    it.property("noop", noop)
  }

  public fun noop(noop: Boolean) {
    it.property("noop", noop.toString())
  }

  public fun preMove(preMove: String) {
    it.property("preMove", preMove)
  }

  public fun preSort(preSort: String) {
    it.property("preSort", preSort)
  }

  public fun preSort(preSort: Boolean) {
    it.property("preSort", preSort.toString())
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

  public fun streamDownload(streamDownload: String) {
    it.property("streamDownload", streamDownload)
  }

  public fun streamDownload(streamDownload: Boolean) {
    it.property("streamDownload", streamDownload.toString())
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
  }

  public fun download(download: String) {
    it.property("download", download)
  }

  public fun download(download: Boolean) {
    it.property("download", download.toString())
  }

  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
  }

  public fun ignoreFileNotFoundOrPermissionError(ignoreFileNotFoundOrPermissionError: String) {
    it.property("ignoreFileNotFoundOrPermissionError", ignoreFileNotFoundOrPermissionError)
  }

  public fun ignoreFileNotFoundOrPermissionError(ignoreFileNotFoundOrPermissionError: Boolean) {
    it.property("ignoreFileNotFoundOrPermissionError",
        ignoreFileNotFoundOrPermissionError.toString())
  }

  public fun inProgressRepository(inProgressRepository: String) {
    it.property("inProgressRepository", inProgressRepository)
  }

  public fun localWorkDirectory(localWorkDirectory: String) {
    it.property("localWorkDirectory", localWorkDirectory)
  }

  public fun onCompletionExceptionHandler(onCompletionExceptionHandler: String) {
    it.property("onCompletionExceptionHandler", onCompletionExceptionHandler)
  }

  public fun pollStrategy(pollStrategy: String) {
    it.property("pollStrategy", pollStrategy)
  }

  public fun processStrategy(processStrategy: String) {
    it.property("processStrategy", processStrategy)
  }

  public fun useList(useList: String) {
    it.property("useList", useList)
  }

  public fun useList(useList: Boolean) {
    it.property("useList", useList.toString())
  }

  public fun checksumFileAlgorithm(checksumFileAlgorithm: String) {
    it.property("checksumFileAlgorithm", checksumFileAlgorithm)
  }

  public fun fileExist(fileExist: String) {
    it.property("fileExist", fileExist)
  }

  public fun flatten(flatten: String) {
    it.property("flatten", flatten)
  }

  public fun flatten(flatten: Boolean) {
    it.property("flatten", flatten.toString())
  }

  public fun jailStartingDirectory(jailStartingDirectory: String) {
    it.property("jailStartingDirectory", jailStartingDirectory)
  }

  public fun jailStartingDirectory(jailStartingDirectory: Boolean) {
    it.property("jailStartingDirectory", jailStartingDirectory.toString())
  }

  public fun moveExisting(moveExisting: String) {
    it.property("moveExisting", moveExisting)
  }

  public fun tempFileName(tempFileName: String) {
    it.property("tempFileName", tempFileName)
  }

  public fun tempPrefix(tempPrefix: String) {
    it.property("tempPrefix", tempPrefix)
  }

  public fun allowNullBody(allowNullBody: String) {
    it.property("allowNullBody", allowNullBody)
  }

  public fun allowNullBody(allowNullBody: Boolean) {
    it.property("allowNullBody", allowNullBody.toString())
  }

  public fun chmod(chmod: String) {
    it.property("chmod", chmod)
  }

  public fun chmodDirectory(chmodDirectory: String) {
    it.property("chmodDirectory", chmodDirectory)
  }

  public fun disconnectOnBatchComplete(disconnectOnBatchComplete: String) {
    it.property("disconnectOnBatchComplete", disconnectOnBatchComplete)
  }

  public fun disconnectOnBatchComplete(disconnectOnBatchComplete: Boolean) {
    it.property("disconnectOnBatchComplete", disconnectOnBatchComplete.toString())
  }

  public fun eagerDeleteTargetFile(eagerDeleteTargetFile: String) {
    it.property("eagerDeleteTargetFile", eagerDeleteTargetFile)
  }

  public fun eagerDeleteTargetFile(eagerDeleteTargetFile: Boolean) {
    it.property("eagerDeleteTargetFile", eagerDeleteTargetFile.toString())
  }

  public fun keepLastModified(keepLastModified: String) {
    it.property("keepLastModified", keepLastModified)
  }

  public fun keepLastModified(keepLastModified: Boolean) {
    it.property("keepLastModified", keepLastModified.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun moveExistingFileStrategy(moveExistingFileStrategy: String) {
    it.property("moveExistingFileStrategy", moveExistingFileStrategy)
  }

  public fun sendNoop(sendNoop: String) {
    it.property("sendNoop", sendNoop)
  }

  public fun sendNoop(sendNoop: Boolean) {
    it.property("sendNoop", sendNoop.toString())
  }

  public fun autoCreate(autoCreate: String) {
    it.property("autoCreate", autoCreate)
  }

  public fun autoCreate(autoCreate: Boolean) {
    it.property("autoCreate", autoCreate.toString())
  }

  public fun bindAddress(bindAddress: String) {
    it.property("bindAddress", bindAddress)
  }

  public fun bulkRequests(bulkRequests: String) {
    it.property("bulkRequests", bulkRequests)
  }

  public fun bulkRequests(bulkRequests: Int) {
    it.property("bulkRequests", bulkRequests.toString())
  }

  public fun compression(compression: String) {
    it.property("compression", compression)
  }

  public fun compression(compression: Int) {
    it.property("compression", compression.toString())
  }

  public fun connectTimeout(connectTimeout: String) {
    it.property("connectTimeout", connectTimeout)
  }

  public fun existDirCheckUsingLs(existDirCheckUsingLs: String) {
    it.property("existDirCheckUsingLs", existDirCheckUsingLs)
  }

  public fun existDirCheckUsingLs(existDirCheckUsingLs: Boolean) {
    it.property("existDirCheckUsingLs", existDirCheckUsingLs.toString())
  }

  public fun filenameEncoding(filenameEncoding: String) {
    it.property("filenameEncoding", filenameEncoding)
  }

  public fun maximumReconnectAttempts(maximumReconnectAttempts: String) {
    it.property("maximumReconnectAttempts", maximumReconnectAttempts)
  }

  public fun maximumReconnectAttempts(maximumReconnectAttempts: Int) {
    it.property("maximumReconnectAttempts", maximumReconnectAttempts.toString())
  }

  public fun proxy(proxy: String) {
    it.property("proxy", proxy)
  }

  public fun reconnectDelay(reconnectDelay: String) {
    it.property("reconnectDelay", reconnectDelay)
  }

  public fun serverAliveCountMax(serverAliveCountMax: String) {
    it.property("serverAliveCountMax", serverAliveCountMax)
  }

  public fun serverAliveCountMax(serverAliveCountMax: Int) {
    it.property("serverAliveCountMax", serverAliveCountMax.toString())
  }

  public fun serverAliveInterval(serverAliveInterval: String) {
    it.property("serverAliveInterval", serverAliveInterval)
  }

  public fun serverAliveInterval(serverAliveInterval: Int) {
    it.property("serverAliveInterval", serverAliveInterval.toString())
  }

  public fun serverMessageLoggingLevel(serverMessageLoggingLevel: String) {
    it.property("serverMessageLoggingLevel", serverMessageLoggingLevel)
  }

  public fun soTimeout(soTimeout: String) {
    it.property("soTimeout", soTimeout)
  }

  public fun stepwise(stepwise: String) {
    it.property("stepwise", stepwise)
  }

  public fun stepwise(stepwise: Boolean) {
    it.property("stepwise", stepwise.toString())
  }

  public fun throwExceptionOnConnectFailed(throwExceptionOnConnectFailed: String) {
    it.property("throwExceptionOnConnectFailed", throwExceptionOnConnectFailed)
  }

  public fun throwExceptionOnConnectFailed(throwExceptionOnConnectFailed: Boolean) {
    it.property("throwExceptionOnConnectFailed", throwExceptionOnConnectFailed.toString())
  }

  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  public fun antExclude(antExclude: String) {
    it.property("antExclude", antExclude)
  }

  public fun antFilterCaseSensitive(antFilterCaseSensitive: String) {
    it.property("antFilterCaseSensitive", antFilterCaseSensitive)
  }

  public fun antFilterCaseSensitive(antFilterCaseSensitive: Boolean) {
    it.property("antFilterCaseSensitive", antFilterCaseSensitive.toString())
  }

  public fun antInclude(antInclude: String) {
    it.property("antInclude", antInclude)
  }

  public fun eagerMaxMessagesPerPoll(eagerMaxMessagesPerPoll: String) {
    it.property("eagerMaxMessagesPerPoll", eagerMaxMessagesPerPoll)
  }

  public fun eagerMaxMessagesPerPoll(eagerMaxMessagesPerPoll: Boolean) {
    it.property("eagerMaxMessagesPerPoll", eagerMaxMessagesPerPoll.toString())
  }

  public fun exclude(exclude: String) {
    it.property("exclude", exclude)
  }

  public fun excludeExt(excludeExt: String) {
    it.property("excludeExt", excludeExt)
  }

  public fun filter(filter: String) {
    it.property("filter", filter)
  }

  public fun filterDirectory(filterDirectory: String) {
    it.property("filterDirectory", filterDirectory)
  }

  public fun filterFile(filterFile: String) {
    it.property("filterFile", filterFile)
  }

  public fun idempotent(idempotent: String) {
    it.property("idempotent", idempotent)
  }

  public fun idempotent(idempotent: Boolean) {
    it.property("idempotent", idempotent.toString())
  }

  public fun idempotentKey(idempotentKey: String) {
    it.property("idempotentKey", idempotentKey)
  }

  public fun idempotentRepository(idempotentRepository: String) {
    it.property("idempotentRepository", idempotentRepository)
  }

  public fun include(include: String) {
    it.property("include", include)
  }

  public fun includeExt(includeExt: String) {
    it.property("includeExt", includeExt)
  }

  public fun maxDepth(maxDepth: String) {
    it.property("maxDepth", maxDepth)
  }

  public fun maxDepth(maxDepth: Int) {
    it.property("maxDepth", maxDepth.toString())
  }

  public fun maxMessagesPerPoll(maxMessagesPerPoll: String) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll)
  }

  public fun maxMessagesPerPoll(maxMessagesPerPoll: Int) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll.toString())
  }

  public fun minDepth(minDepth: String) {
    it.property("minDepth", minDepth)
  }

  public fun minDepth(minDepth: Int) {
    it.property("minDepth", minDepth.toString())
  }

  public fun move(move: String) {
    it.property("move", move)
  }

  public fun exclusiveReadLockStrategy(exclusiveReadLockStrategy: String) {
    it.property("exclusiveReadLockStrategy", exclusiveReadLockStrategy)
  }

  public fun readLock(readLock: String) {
    it.property("readLock", readLock)
  }

  public fun readLockCheckInterval(readLockCheckInterval: String) {
    it.property("readLockCheckInterval", readLockCheckInterval)
  }

  public fun readLockCheckInterval(readLockCheckInterval: Int) {
    it.property("readLockCheckInterval", readLockCheckInterval.toString())
  }

  public fun readLockDeleteOrphanLockFiles(readLockDeleteOrphanLockFiles: String) {
    it.property("readLockDeleteOrphanLockFiles", readLockDeleteOrphanLockFiles)
  }

  public fun readLockDeleteOrphanLockFiles(readLockDeleteOrphanLockFiles: Boolean) {
    it.property("readLockDeleteOrphanLockFiles", readLockDeleteOrphanLockFiles.toString())
  }

  public fun readLockIdempotentReleaseAsync(readLockIdempotentReleaseAsync: String) {
    it.property("readLockIdempotentReleaseAsync", readLockIdempotentReleaseAsync)
  }

  public fun readLockIdempotentReleaseAsync(readLockIdempotentReleaseAsync: Boolean) {
    it.property("readLockIdempotentReleaseAsync", readLockIdempotentReleaseAsync.toString())
  }

  public
      fun readLockIdempotentReleaseAsyncPoolSize(readLockIdempotentReleaseAsyncPoolSize: String) {
    it.property("readLockIdempotentReleaseAsyncPoolSize", readLockIdempotentReleaseAsyncPoolSize)
  }

  public fun readLockIdempotentReleaseAsyncPoolSize(readLockIdempotentReleaseAsyncPoolSize: Int) {
    it.property("readLockIdempotentReleaseAsyncPoolSize",
        readLockIdempotentReleaseAsyncPoolSize.toString())
  }

  public fun readLockIdempotentReleaseDelay(readLockIdempotentReleaseDelay: String) {
    it.property("readLockIdempotentReleaseDelay", readLockIdempotentReleaseDelay)
  }

  public fun readLockIdempotentReleaseDelay(readLockIdempotentReleaseDelay: Int) {
    it.property("readLockIdempotentReleaseDelay", readLockIdempotentReleaseDelay.toString())
  }

  public
      fun readLockIdempotentReleaseExecutorService(readLockIdempotentReleaseExecutorService: String) {
    it.property("readLockIdempotentReleaseExecutorService",
        readLockIdempotentReleaseExecutorService)
  }

  public fun readLockLoggingLevel(readLockLoggingLevel: String) {
    it.property("readLockLoggingLevel", readLockLoggingLevel)
  }

  public fun readLockMarkerFile(readLockMarkerFile: String) {
    it.property("readLockMarkerFile", readLockMarkerFile)
  }

  public fun readLockMarkerFile(readLockMarkerFile: Boolean) {
    it.property("readLockMarkerFile", readLockMarkerFile.toString())
  }

  public fun readLockMinAge(readLockMinAge: String) {
    it.property("readLockMinAge", readLockMinAge)
  }

  public fun readLockMinAge(readLockMinAge: Int) {
    it.property("readLockMinAge", readLockMinAge.toString())
  }

  public fun readLockMinLength(readLockMinLength: String) {
    it.property("readLockMinLength", readLockMinLength)
  }

  public fun readLockMinLength(readLockMinLength: Int) {
    it.property("readLockMinLength", readLockMinLength.toString())
  }

  public fun readLockRemoveOnCommit(readLockRemoveOnCommit: String) {
    it.property("readLockRemoveOnCommit", readLockRemoveOnCommit)
  }

  public fun readLockRemoveOnCommit(readLockRemoveOnCommit: Boolean) {
    it.property("readLockRemoveOnCommit", readLockRemoveOnCommit.toString())
  }

  public fun readLockRemoveOnRollback(readLockRemoveOnRollback: String) {
    it.property("readLockRemoveOnRollback", readLockRemoveOnRollback)
  }

  public fun readLockRemoveOnRollback(readLockRemoveOnRollback: Boolean) {
    it.property("readLockRemoveOnRollback", readLockRemoveOnRollback.toString())
  }

  public fun readLockTimeout(readLockTimeout: String) {
    it.property("readLockTimeout", readLockTimeout)
  }

  public fun readLockTimeout(readLockTimeout: Int) {
    it.property("readLockTimeout", readLockTimeout.toString())
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

  public fun autoCreateKnownHostsFile(autoCreateKnownHostsFile: String) {
    it.property("autoCreateKnownHostsFile", autoCreateKnownHostsFile)
  }

  public fun autoCreateKnownHostsFile(autoCreateKnownHostsFile: Boolean) {
    it.property("autoCreateKnownHostsFile", autoCreateKnownHostsFile.toString())
  }

  public fun ciphers(ciphers: String) {
    it.property("ciphers", ciphers)
  }

  public fun keyExchangeProtocols(keyExchangeProtocols: String) {
    it.property("keyExchangeProtocols", keyExchangeProtocols)
  }

  public fun keyPair(keyPair: String) {
    it.property("keyPair", keyPair)
  }

  public fun knownHosts(knownHosts: String) {
    it.property("knownHosts", knownHosts)
  }

  public fun knownHostsFile(knownHostsFile: String) {
    it.property("knownHostsFile", knownHostsFile)
  }

  public fun knownHostsUri(knownHostsUri: String) {
    it.property("knownHostsUri", knownHostsUri)
  }

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun preferredAuthentications(preferredAuthentications: String) {
    it.property("preferredAuthentications", preferredAuthentications)
  }

  public fun privateKey(privateKey: String) {
    it.property("privateKey", privateKey)
  }

  public fun privateKeyFile(privateKeyFile: String) {
    it.property("privateKeyFile", privateKeyFile)
  }

  public fun privateKeyPassphrase(privateKeyPassphrase: String) {
    it.property("privateKeyPassphrase", privateKeyPassphrase)
  }

  public fun privateKeyUri(privateKeyUri: String) {
    it.property("privateKeyUri", privateKeyUri)
  }

  public fun publicKeyAcceptedAlgorithms(publicKeyAcceptedAlgorithms: String) {
    it.property("publicKeyAcceptedAlgorithms", publicKeyAcceptedAlgorithms)
  }

  public fun serverHostKeys(serverHostKeys: String) {
    it.property("serverHostKeys", serverHostKeys)
  }

  public fun strictHostKeyChecking(strictHostKeyChecking: String) {
    it.property("strictHostKeyChecking", strictHostKeyChecking)
  }

  public fun username(username: String) {
    it.property("username", username)
  }

  public fun useUserKnownHostsFile(useUserKnownHostsFile: String) {
    it.property("useUserKnownHostsFile", useUserKnownHostsFile)
  }

  public fun useUserKnownHostsFile(useUserKnownHostsFile: Boolean) {
    it.property("useUserKnownHostsFile", useUserKnownHostsFile.toString())
  }

  public fun shuffle(shuffle: String) {
    it.property("shuffle", shuffle)
  }

  public fun shuffle(shuffle: Boolean) {
    it.property("shuffle", shuffle.toString())
  }

  public fun sortBy(sortBy: String) {
    it.property("sortBy", sortBy)
  }

  public fun sorter(sorter: String) {
    it.property("sorter", sorter)
  }
}
