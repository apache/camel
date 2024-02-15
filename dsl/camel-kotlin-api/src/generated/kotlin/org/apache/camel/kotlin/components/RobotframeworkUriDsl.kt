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

public fun UriDsl.robotframework(i: RobotframeworkUriDsl.() -> Unit) {
  RobotframeworkUriDsl(this).apply(i)
}

@CamelDslMarker
public class RobotframeworkUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("robotframework")
  }

  private var resourceUri: String = ""

  public fun resourceUri(resourceUri: String) {
    this.resourceUri = resourceUri
    it.url("$resourceUri")
  }

  public fun allowContextMapAll(allowContextMapAll: String) {
    it.property("allowContextMapAll", allowContextMapAll)
  }

  public fun allowContextMapAll(allowContextMapAll: Boolean) {
    it.property("allowContextMapAll", allowContextMapAll.toString())
  }

  public fun allowTemplateFromHeader(allowTemplateFromHeader: String) {
    it.property("allowTemplateFromHeader", allowTemplateFromHeader)
  }

  public fun allowTemplateFromHeader(allowTemplateFromHeader: Boolean) {
    it.property("allowTemplateFromHeader", allowTemplateFromHeader.toString())
  }

  public fun argumentFiles(argumentFiles: String) {
    it.property("argumentFiles", argumentFiles)
  }

  public fun combinedTagStats(combinedTagStats: String) {
    it.property("combinedTagStats", combinedTagStats)
  }

  public fun contentCache(contentCache: String) {
    it.property("contentCache", contentCache)
  }

  public fun contentCache(contentCache: Boolean) {
    it.property("contentCache", contentCache.toString())
  }

  public fun criticalTags(criticalTags: String) {
    it.property("criticalTags", criticalTags)
  }

  public fun debugFile(debugFile: String) {
    it.property("debugFile", debugFile)
  }

  public fun document(document: String) {
    it.property("document", document)
  }

  public fun dryrun(dryrun: String) {
    it.property("dryrun", dryrun)
  }

  public fun dryrun(dryrun: Boolean) {
    it.property("dryrun", dryrun.toString())
  }

  public fun excludes(excludes: String) {
    it.property("excludes", excludes)
  }

  public fun exitOnFailure(exitOnFailure: String) {
    it.property("exitOnFailure", exitOnFailure)
  }

  public fun exitOnFailure(exitOnFailure: Boolean) {
    it.property("exitOnFailure", exitOnFailure.toString())
  }

  public fun includes(includes: String) {
    it.property("includes", includes)
  }

  public fun listener(listener: String) {
    it.property("listener", listener)
  }

  public fun listeners(listeners: String) {
    it.property("listeners", listeners)
  }

  public fun log(log: String) {
    it.property("log", log)
  }

  public fun logLevel(logLevel: String) {
    it.property("logLevel", logLevel)
  }

  public fun logTitle(logTitle: String) {
    it.property("logTitle", logTitle)
  }

  public fun metadata(metadata: String) {
    it.property("metadata", metadata)
  }

  public fun monitorColors(monitorColors: String) {
    it.property("monitorColors", monitorColors)
  }

  public fun monitorWidth(monitorWidth: String) {
    it.property("monitorWidth", monitorWidth)
  }

  public fun name(name: String) {
    it.property("name", name)
  }

  public fun nonCriticalTags(nonCriticalTags: String) {
    it.property("nonCriticalTags", nonCriticalTags)
  }

  public fun noStatusReturnCode(noStatusReturnCode: String) {
    it.property("noStatusReturnCode", noStatusReturnCode)
  }

  public fun noStatusReturnCode(noStatusReturnCode: Boolean) {
    it.property("noStatusReturnCode", noStatusReturnCode.toString())
  }

  public fun output(output: String) {
    it.property("output", output)
  }

  public fun outputDirectory(outputDirectory: String) {
    it.property("outputDirectory", outputDirectory)
  }

  public fun randomize(randomize: String) {
    it.property("randomize", randomize)
  }

  public fun report(report: String) {
    it.property("report", report)
  }

  public fun reportBackground(reportBackground: String) {
    it.property("reportBackground", reportBackground)
  }

  public fun reportTitle(reportTitle: String) {
    it.property("reportTitle", reportTitle)
  }

  public fun runEmptySuite(runEmptySuite: String) {
    it.property("runEmptySuite", runEmptySuite)
  }

  public fun runEmptySuite(runEmptySuite: Boolean) {
    it.property("runEmptySuite", runEmptySuite.toString())
  }

  public fun runFailed(runFailed: String) {
    it.property("runFailed", runFailed)
  }

  public fun runMode(runMode: String) {
    it.property("runMode", runMode)
  }

  public fun skipTeardownOnExit(skipTeardownOnExit: String) {
    it.property("skipTeardownOnExit", skipTeardownOnExit)
  }

  public fun skipTeardownOnExit(skipTeardownOnExit: Boolean) {
    it.property("skipTeardownOnExit", skipTeardownOnExit.toString())
  }

  public fun splitOutputs(splitOutputs: String) {
    it.property("splitOutputs", splitOutputs)
  }

  public fun suites(suites: String) {
    it.property("suites", suites)
  }

  public fun suiteStatLevel(suiteStatLevel: String) {
    it.property("suiteStatLevel", suiteStatLevel)
  }

  public fun summaryTitle(summaryTitle: String) {
    it.property("summaryTitle", summaryTitle)
  }

  public fun tagDocs(tagDocs: String) {
    it.property("tagDocs", tagDocs)
  }

  public fun tags(tags: String) {
    it.property("tags", tags)
  }

  public fun tagStatExcludes(tagStatExcludes: String) {
    it.property("tagStatExcludes", tagStatExcludes)
  }

  public fun tagStatIncludes(tagStatIncludes: String) {
    it.property("tagStatIncludes", tagStatIncludes)
  }

  public fun tagStatLinks(tagStatLinks: String) {
    it.property("tagStatLinks", tagStatLinks)
  }

  public fun tests(tests: String) {
    it.property("tests", tests)
  }

  public fun timestampOutputs(timestampOutputs: String) {
    it.property("timestampOutputs", timestampOutputs)
  }

  public fun timestampOutputs(timestampOutputs: Boolean) {
    it.property("timestampOutputs", timestampOutputs.toString())
  }

  public fun variableFiles(variableFiles: String) {
    it.property("variableFiles", variableFiles)
  }

  public fun variables(variables: String) {
    it.property("variables", variables)
  }

  public fun warnOnSkippedFiles(warnOnSkippedFiles: String) {
    it.property("warnOnSkippedFiles", warnOnSkippedFiles)
  }

  public fun warnOnSkippedFiles(warnOnSkippedFiles: Boolean) {
    it.property("warnOnSkippedFiles", warnOnSkippedFiles.toString())
  }

  public fun xunitFile(xunitFile: String) {
    it.property("xunitFile", xunitFile)
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
}
