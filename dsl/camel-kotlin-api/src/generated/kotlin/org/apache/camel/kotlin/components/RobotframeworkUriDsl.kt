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
 * Pass camel exchanges to acceptance test written in Robot DSL.
 */
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

  /**
   * Path to the resource. You can prefix with: classpath, file, http, ref, or bean. classpath, file
   * and http loads the resource using these protocols (classpath is default). ref will lookup the
   * resource in the registry. bean will call a method on a bean to be used as the resource. For bean
   * you can specify the method name after dot, eg bean:myBean.myMethod.
   */
  public fun resourceUri(resourceUri: String) {
    this.resourceUri = resourceUri
    it.url("$resourceUri")
  }

  /**
   * Sets whether the context map should allow access to all details. By default only the message
   * body and headers can be accessed. This option can be enabled for full access to the current
   * Exchange and CamelContext. Doing so impose a potential security risk as this opens access to the
   * full power of CamelContext API.
   */
  public fun allowContextMapAll(allowContextMapAll: String) {
    it.property("allowContextMapAll", allowContextMapAll)
  }

  /**
   * Sets whether the context map should allow access to all details. By default only the message
   * body and headers can be accessed. This option can be enabled for full access to the current
   * Exchange and CamelContext. Doing so impose a potential security risk as this opens access to the
   * full power of CamelContext API.
   */
  public fun allowContextMapAll(allowContextMapAll: Boolean) {
    it.property("allowContextMapAll", allowContextMapAll.toString())
  }

  /**
   * Whether to allow to use resource template from header or not (default false). Enabling this
   * allows to specify dynamic templates via message header. However this can be seen as a potential
   * security vulnerability if the header is coming from a malicious user, so use this with care.
   */
  public fun allowTemplateFromHeader(allowTemplateFromHeader: String) {
    it.property("allowTemplateFromHeader", allowTemplateFromHeader)
  }

  /**
   * Whether to allow to use resource template from header or not (default false). Enabling this
   * allows to specify dynamic templates via message header. However this can be seen as a potential
   * security vulnerability if the header is coming from a malicious user, so use this with care.
   */
  public fun allowTemplateFromHeader(allowTemplateFromHeader: Boolean) {
    it.property("allowTemplateFromHeader", allowTemplateFromHeader.toString())
  }

  /**
   * A text String to read more arguments from.
   */
  public fun argumentFiles(argumentFiles: String) {
    it.property("argumentFiles", argumentFiles)
  }

  /**
   * Creates combined statistics based on tags. Use the format tags:title List
   */
  public fun combinedTagStats(combinedTagStats: String) {
    it.property("combinedTagStats", combinedTagStats)
  }

  /**
   * Sets whether to use resource content cache or not
   */
  public fun contentCache(contentCache: String) {
    it.property("contentCache", contentCache)
  }

  /**
   * Sets whether to use resource content cache or not
   */
  public fun contentCache(contentCache: Boolean) {
    it.property("contentCache", contentCache.toString())
  }

  /**
   * Tests that have the given tags are considered critical. List
   */
  public fun criticalTags(criticalTags: String) {
    it.property("criticalTags", criticalTags)
  }

  /**
   * A debug String that is written during execution.
   */
  public fun debugFile(debugFile: String) {
    it.property("debugFile", debugFile)
  }

  /**
   * Sets the documentation of the top-level tests suites.
   */
  public fun document(document: String) {
    it.property("document", document)
  }

  /**
   * Sets dryrun mode on use. In the dry run mode tests are run without executing keywords
   * originating from test libraries. Useful for validating test data syntax.
   */
  public fun dryrun(dryrun: String) {
    it.property("dryrun", dryrun)
  }

  /**
   * Sets dryrun mode on use. In the dry run mode tests are run without executing keywords
   * originating from test libraries. Useful for validating test data syntax.
   */
  public fun dryrun(dryrun: Boolean) {
    it.property("dryrun", dryrun.toString())
  }

  /**
   * Selects the tests cases by tags. List
   */
  public fun excludes(excludes: String) {
    it.property("excludes", excludes)
  }

  /**
   * Sets robot to stop execution immediately if a critical test fails.
   */
  public fun exitOnFailure(exitOnFailure: String) {
    it.property("exitOnFailure", exitOnFailure)
  }

  /**
   * Sets robot to stop execution immediately if a critical test fails.
   */
  public fun exitOnFailure(exitOnFailure: Boolean) {
    it.property("exitOnFailure", exitOnFailure.toString())
  }

  /**
   * Selects the tests cases by tags. List
   */
  public fun includes(includes: String) {
    it.property("includes", includes)
  }

  /**
   * Sets a single listener for monitoring tests execution
   */
  public fun listener(listener: String) {
    it.property("listener", listener)
  }

  /**
   * Sets multiple listeners for monitoring tests execution. Use the format
   * ListenerWithArgs:arg1:arg2 or simply ListenerWithoutArgs List
   */
  public fun listeners(listeners: String) {
    it.property("listeners", listeners)
  }

  /**
   * Sets the path to the generated log String.
   */
  public fun log(log: String) {
    it.property("log", log)
  }

  /**
   * Sets the threshold level for logging.
   */
  public fun logLevel(logLevel: String) {
    it.property("logLevel", logLevel)
  }

  /**
   * Sets a title for the generated tests log.
   */
  public fun logTitle(logTitle: String) {
    it.property("logTitle", logTitle)
  }

  /**
   * Sets free metadata for the top level tests suites. comma seperated list of string resulting as
   * List
   */
  public fun metadata(metadata: String) {
    it.property("metadata", metadata)
  }

  /**
   * Using ANSI colors in console. Normally colors work in unixes but not in Windows. Default is
   * 'on'. 'on' - use colors in unixes but not in Windows 'off' - never use colors 'force' - always use
   * colors (also in Windows)
   */
  public fun monitorColors(monitorColors: String) {
    it.property("monitorColors", monitorColors)
  }

  /**
   * Width of the monitor output. Default is 78.
   */
  public fun monitorWidth(monitorWidth: String) {
    it.property("monitorWidth", monitorWidth)
  }

  /**
   * Sets the name of the top-level tests suites.
   */
  public fun name(name: String) {
    it.property("name", name)
  }

  /**
   * Tests that have the given tags are not critical. List
   */
  public fun nonCriticalTags(nonCriticalTags: String) {
    it.property("nonCriticalTags", nonCriticalTags)
  }

  /**
   * If true, sets the return code to zero regardless of failures in test cases. Error codes are
   * returned normally.
   */
  public fun noStatusReturnCode(noStatusReturnCode: String) {
    it.property("noStatusReturnCode", noStatusReturnCode)
  }

  /**
   * If true, sets the return code to zero regardless of failures in test cases. Error codes are
   * returned normally.
   */
  public fun noStatusReturnCode(noStatusReturnCode: Boolean) {
    it.property("noStatusReturnCode", noStatusReturnCode.toString())
  }

  /**
   * Sets the path to the generated output String.
   */
  public fun output(output: String) {
    it.property("output", output)
  }

  /**
   * Configures where generated reports are to be placed.
   */
  public fun outputDirectory(outputDirectory: String) {
    it.property("outputDirectory", outputDirectory)
  }

  /**
   * Sets the test execution order to be randomized. Valid values are all, suite, and test
   */
  public fun randomize(randomize: String) {
    it.property("randomize", randomize)
  }

  /**
   * Sets the path to the generated report String.
   */
  public fun report(report: String) {
    it.property("report", report)
  }

  /**
   * Sets background colors for the generated report and summary.
   */
  public fun reportBackground(reportBackground: String) {
    it.property("reportBackground", reportBackground)
  }

  /**
   * Sets a title for the generated tests report.
   */
  public fun reportTitle(reportTitle: String) {
    it.property("reportTitle", reportTitle)
  }

  /**
   * Executes tests also if the top level test suite is empty. Useful e.g. with --include/--exclude
   * when it is not an error that no test matches the condition.
   */
  public fun runEmptySuite(runEmptySuite: String) {
    it.property("runEmptySuite", runEmptySuite)
  }

  /**
   * Executes tests also if the top level test suite is empty. Useful e.g. with --include/--exclude
   * when it is not an error that no test matches the condition.
   */
  public fun runEmptySuite(runEmptySuite: Boolean) {
    it.property("runEmptySuite", runEmptySuite.toString())
  }

  /**
   * Re-run failed tests, based on output.xml String.
   */
  public fun runFailed(runFailed: String) {
    it.property("runFailed", runFailed)
  }

  /**
   * Sets the execution mode for this tests run. Note that this setting has been deprecated in Robot
   * Framework 2.8. Use separate dryryn, skipTeardownOnExit, exitOnFailure, and randomize settings
   * instead.
   */
  public fun runMode(runMode: String) {
    it.property("runMode", runMode)
  }

  /**
   * Sets whether the teardowns are skipped if the test execution is prematurely stopped.
   */
  public fun skipTeardownOnExit(skipTeardownOnExit: String) {
    it.property("skipTeardownOnExit", skipTeardownOnExit)
  }

  /**
   * Sets whether the teardowns are skipped if the test execution is prematurely stopped.
   */
  public fun skipTeardownOnExit(skipTeardownOnExit: Boolean) {
    it.property("skipTeardownOnExit", skipTeardownOnExit.toString())
  }

  /**
   * Splits output and log files.
   */
  public fun splitOutputs(splitOutputs: String) {
    it.property("splitOutputs", splitOutputs)
  }

  /**
   * Selects the tests suites by name. List
   */
  public fun suites(suites: String) {
    it.property("suites", suites)
  }

  /**
   * Defines how many levels to show in the Statistics by Suite table in outputs.
   */
  public fun suiteStatLevel(suiteStatLevel: String) {
    it.property("suiteStatLevel", suiteStatLevel)
  }

  /**
   * Sets a title for the generated summary report.
   */
  public fun summaryTitle(summaryTitle: String) {
    it.property("summaryTitle", summaryTitle)
  }

  /**
   * Adds documentation to the specified tags. List
   */
  public fun tagDocs(tagDocs: String) {
    it.property("tagDocs", tagDocs)
  }

  /**
   * Sets the tags(s) to all executed tests cases. List
   */
  public fun tags(tags: String) {
    it.property("tags", tags)
  }

  /**
   * Excludes these tags from the Statistics by Tag and Test Details by Tag tables in outputs. List
   */
  public fun tagStatExcludes(tagStatExcludes: String) {
    it.property("tagStatExcludes", tagStatExcludes)
  }

  /**
   * Includes only these tags in the Statistics by Tag and Test Details by Tag tables in outputs.
   * List
   */
  public fun tagStatIncludes(tagStatIncludes: String) {
    it.property("tagStatIncludes", tagStatIncludes)
  }

  /**
   * Adds external links to the Statistics by Tag table in outputs. Use the format
   * pattern:link:title List
   */
  public fun tagStatLinks(tagStatLinks: String) {
    it.property("tagStatLinks", tagStatLinks)
  }

  /**
   * Selects the tests cases by name. List
   */
  public fun tests(tests: String) {
    it.property("tests", tests)
  }

  /**
   * Adds a timestamp to all output files.
   */
  public fun timestampOutputs(timestampOutputs: String) {
    it.property("timestampOutputs", timestampOutputs)
  }

  /**
   * Adds a timestamp to all output files.
   */
  public fun timestampOutputs(timestampOutputs: Boolean) {
    it.property("timestampOutputs", timestampOutputs.toString())
  }

  /**
   * Sets variables using variables files. Use the format path:args List
   */
  public fun variableFiles(variableFiles: String) {
    it.property("variableFiles", variableFiles)
  }

  /**
   * Sets individual variables. Use the format name:value List
   */
  public fun variables(variables: String) {
    it.property("variables", variables)
  }

  /**
   * Show a warning when an invalid String is skipped.
   */
  public fun warnOnSkippedFiles(warnOnSkippedFiles: String) {
    it.property("warnOnSkippedFiles", warnOnSkippedFiles)
  }

  /**
   * Show a warning when an invalid String is skipped.
   */
  public fun warnOnSkippedFiles(warnOnSkippedFiles: Boolean) {
    it.property("warnOnSkippedFiles", warnOnSkippedFiles.toString())
  }

  /**
   * Sets the path to the generated XUnit compatible result String, relative to outputDirectory. The
   * String is in xml format. By default, the String name is derived from the testCasesDirectory
   * parameter, replacing blanks in the directory name by underscores.
   */
  public fun xunitFile(xunitFile: String) {
    it.property("xunitFile", xunitFile)
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
}
