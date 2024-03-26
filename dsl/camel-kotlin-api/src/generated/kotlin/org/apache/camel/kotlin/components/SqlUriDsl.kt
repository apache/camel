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
 * Perform SQL queries using Spring JDBC.
 */
public fun UriDsl.sql(i: SqlUriDsl.() -> Unit) {
  SqlUriDsl(this).apply(i)
}

@CamelDslMarker
public class SqlUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("sql")
  }

  private var query: String = ""

  /**
   * Sets the SQL query to perform. You can externalize the query by using file: or classpath: as
   * prefix and specify the location of the file.
   */
  public fun query(query: String) {
    this.query = query
    it.url("$query")
  }

  /**
   * Whether to allow using named parameters in the queries.
   */
  public fun allowNamedParameters(allowNamedParameters: String) {
    it.property("allowNamedParameters", allowNamedParameters)
  }

  /**
   * Whether to allow using named parameters in the queries.
   */
  public fun allowNamedParameters(allowNamedParameters: Boolean) {
    it.property("allowNamedParameters", allowNamedParameters.toString())
  }

  /**
   * Sets the DataSource to use to communicate with the database at endpoint level.
   */
  public fun dataSource(dataSource: String) {
    it.property("dataSource", dataSource)
  }

  /**
   * Specify the full package and class name to use as conversion when outputType=SelectOne.
   */
  public fun outputClass(outputClass: String) {
    it.property("outputClass", outputClass)
  }

  /**
   * Store the query result in a header instead of the message body. By default, outputHeader ==
   * null and the query result is stored in the message body, any existing content in the message body
   * is discarded. If outputHeader is set, the value is used as the name of the header to store the
   * query result and the original message body is preserved.
   */
  public fun outputHeader(outputHeader: String) {
    it.property("outputHeader", outputHeader)
  }

  /**
   * Make the output of consumer or producer to SelectList as List of Map, or SelectOne as single
   * Java object in the following way: a) If the query has only single column, then that JDBC Column
   * object is returned. (such as SELECT COUNT( ) FROM PROJECT will return a Long object. b) If the
   * query has more than one column, then it will return a Map of that result. c) If the outputClass is
   * set, then it will convert the query result into an Java bean object by calling all the setters
   * that match the column names. It will assume your class has a default constructor to create
   * instance with. d) If the query resulted in more than one rows, it throws an non-unique result
   * exception. StreamList streams the result of the query using an Iterator. This can be used with the
   * Splitter EIP in streaming mode to process the ResultSet in streaming fashion.
   */
  public fun outputType(outputType: String) {
    it.property("outputType", outputType)
  }

  /**
   * The separator to use when parameter values is taken from message body (if the body is a String
   * type), to be inserted at # placeholders. Notice if you use named parameters, then a Map type is
   * used instead. The default value is comma
   */
  public fun separator(separator: String) {
    it.property("separator", separator)
  }

  /**
   * Sets whether to break batch if onConsume failed.
   */
  public fun breakBatchOnConsumeFail(breakBatchOnConsumeFail: String) {
    it.property("breakBatchOnConsumeFail", breakBatchOnConsumeFail)
  }

  /**
   * Sets whether to break batch if onConsume failed.
   */
  public fun breakBatchOnConsumeFail(breakBatchOnConsumeFail: Boolean) {
    it.property("breakBatchOnConsumeFail", breakBatchOnConsumeFail.toString())
  }

  /**
   * Sets an expected update count to validate when using onConsume.
   */
  public fun expectedUpdateCount(expectedUpdateCount: String) {
    it.property("expectedUpdateCount", expectedUpdateCount)
  }

  /**
   * Sets an expected update count to validate when using onConsume.
   */
  public fun expectedUpdateCount(expectedUpdateCount: Int) {
    it.property("expectedUpdateCount", expectedUpdateCount.toString())
  }

  /**
   * Sets the maximum number of messages to poll
   */
  public fun maxMessagesPerPoll(maxMessagesPerPoll: String) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll)
  }

  /**
   * Sets the maximum number of messages to poll
   */
  public fun maxMessagesPerPoll(maxMessagesPerPoll: Int) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll.toString())
  }

  /**
   * After processing each row then this query can be executed, if the Exchange was processed
   * successfully, for example to mark the row as processed. The query can have parameter.
   */
  public fun onConsume(onConsume: String) {
    it.property("onConsume", onConsume)
  }

  /**
   * After processing the entire batch, this query can be executed to bulk update rows etc. The
   * query cannot have parameters.
   */
  public fun onConsumeBatchComplete(onConsumeBatchComplete: String) {
    it.property("onConsumeBatchComplete", onConsumeBatchComplete)
  }

  /**
   * After processing each row then this query can be executed, if the Exchange failed, for example
   * to mark the row as failed. The query can have parameter.
   */
  public fun onConsumeFailed(onConsumeFailed: String) {
    it.property("onConsumeFailed", onConsumeFailed)
  }

  /**
   * Sets whether empty resultset should be allowed to be sent to the next hop. Defaults to false.
   * So the empty resultset will be filtered out.
   */
  public fun routeEmptyResultSet(routeEmptyResultSet: String) {
    it.property("routeEmptyResultSet", routeEmptyResultSet)
  }

  /**
   * Sets whether empty resultset should be allowed to be sent to the next hop. Defaults to false.
   * So the empty resultset will be filtered out.
   */
  public fun routeEmptyResultSet(routeEmptyResultSet: Boolean) {
    it.property("routeEmptyResultSet", routeEmptyResultSet.toString())
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
   * Enables or disables transaction. If enabled then if processing an exchange failed then the
   * consumer breaks out processing any further exchanges to cause a rollback eager.
   */
  public fun transacted(transacted: String) {
    it.property("transacted", transacted)
  }

  /**
   * Enables or disables transaction. If enabled then if processing an exchange failed then the
   * consumer breaks out processing any further exchanges to cause a rollback eager.
   */
  public fun transacted(transacted: Boolean) {
    it.property("transacted", transacted.toString())
  }

  /**
   * Sets how resultset should be delivered to route. Indicates delivery as either a list or
   * individual object. defaults to true.
   */
  public fun useIterator(useIterator: String) {
    it.property("useIterator", useIterator)
  }

  /**
   * Sets how resultset should be delivered to route. Indicates delivery as either a list or
   * individual object. defaults to true.
   */
  public fun useIterator(useIterator: Boolean) {
    it.property("useIterator", useIterator.toString())
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
   * Allows to plugin to use a custom org.apache.camel.component.sql.SqlProcessingStrategy to
   * execute queries when the consumer has processed the rows/batch.
   */
  public fun processingStrategy(processingStrategy: String) {
    it.property("processingStrategy", processingStrategy)
  }

  /**
   * Enables or disables batch mode
   */
  public fun batch(batch: String) {
    it.property("batch", batch)
  }

  /**
   * Enables or disables batch mode
   */
  public fun batch(batch: Boolean) {
    it.property("batch", batch.toString())
  }

  /**
   * If set, will ignore the results of the SQL query and use the existing IN message as the OUT
   * message for the continuation of processing
   */
  public fun noop(noop: String) {
    it.property("noop", noop)
  }

  /**
   * If set, will ignore the results of the SQL query and use the existing IN message as the OUT
   * message for the continuation of processing
   */
  public fun noop(noop: Boolean) {
    it.property("noop", noop.toString())
  }

  /**
   * Whether to use the message body as the SQL and then headers for parameters. If this option is
   * enabled then the SQL in the uri is not used. Note that query parameters in the message body are
   * represented by a question mark instead of a # symbol.
   */
  public fun useMessageBodyForSql(useMessageBodyForSql: String) {
    it.property("useMessageBodyForSql", useMessageBodyForSql)
  }

  /**
   * Whether to use the message body as the SQL and then headers for parameters. If this option is
   * enabled then the SQL in the uri is not used. Note that query parameters in the message body are
   * represented by a question mark instead of a # symbol.
   */
  public fun useMessageBodyForSql(useMessageBodyForSql: Boolean) {
    it.property("useMessageBodyForSql", useMessageBodyForSql.toString())
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
   * If enabled then the populateStatement method from
   * org.apache.camel.component.sql.SqlPrepareStatementStrategy is always invoked, also if there is no
   * expected parameters to be prepared. When this is false then the populateStatement is only invoked
   * if there is 1 or more expected parameters to be set; for example this avoids reading the message
   * body/headers for SQL queries with no parameters.
   */
  public fun alwaysPopulateStatement(alwaysPopulateStatement: String) {
    it.property("alwaysPopulateStatement", alwaysPopulateStatement)
  }

  /**
   * If enabled then the populateStatement method from
   * org.apache.camel.component.sql.SqlPrepareStatementStrategy is always invoked, also if there is no
   * expected parameters to be prepared. When this is false then the populateStatement is only invoked
   * if there is 1 or more expected parameters to be set; for example this avoids reading the message
   * body/headers for SQL queries with no parameters.
   */
  public fun alwaysPopulateStatement(alwaysPopulateStatement: Boolean) {
    it.property("alwaysPopulateStatement", alwaysPopulateStatement.toString())
  }

  /**
   * If set greater than zero, then Camel will use this count value of parameters to replace instead
   * of querying via JDBC metadata API. This is useful if the JDBC vendor could not return correct
   * parameters count, then user may override instead.
   */
  public fun parametersCount(parametersCount: String) {
    it.property("parametersCount", parametersCount)
  }

  /**
   * If set greater than zero, then Camel will use this count value of parameters to replace instead
   * of querying via JDBC metadata API. This is useful if the JDBC vendor could not return correct
   * parameters count, then user may override instead.
   */
  public fun parametersCount(parametersCount: Int) {
    it.property("parametersCount", parametersCount.toString())
  }

  /**
   * Specifies a character that will be replaced to in SQL query. Notice, that it is simple
   * String.replaceAll() operation and no SQL parsing is involved (quoted strings will also change).
   */
  public fun placeholder(placeholder: String) {
    it.property("placeholder", placeholder)
  }

  /**
   * Allows to plugin to use a custom org.apache.camel.component.sql.SqlPrepareStatementStrategy to
   * control preparation of the query and prepared statement.
   */
  public fun prepareStatementStrategy(prepareStatementStrategy: String) {
    it.property("prepareStatementStrategy", prepareStatementStrategy)
  }

  /**
   * Factory for creating RowMapper
   */
  public fun rowMapperFactory(rowMapperFactory: String) {
    it.property("rowMapperFactory", rowMapperFactory)
  }

  /**
   * Configures the Spring JdbcTemplate with the key/values from the Map
   */
  public fun templateOptions(templateOptions: String) {
    it.property("templateOptions", templateOptions)
  }

  /**
   * Sets whether to use placeholder and replace all placeholder characters with sign in the SQL
   * queries.
   */
  public fun usePlaceholder(usePlaceholder: String) {
    it.property("usePlaceholder", usePlaceholder)
  }

  /**
   * Sets whether to use placeholder and replace all placeholder characters with sign in the SQL
   * queries.
   */
  public fun usePlaceholder(usePlaceholder: Boolean) {
    it.property("usePlaceholder", usePlaceholder.toString())
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
