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
 * Access databases through SQL and JDBC with Spring Transaction support.
 */
public fun UriDsl.`spring-jdbc`(i: SpringJdbcUriDsl.() -> Unit) {
  SpringJdbcUriDsl(this).apply(i)
}

@CamelDslMarker
public class SpringJdbcUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("spring-jdbc")
  }

  private var dataSourceName: String = ""

  /**
   * Name of DataSource to lookup in the Registry. If the name is dataSource or default, then Camel
   * will attempt to lookup a default DataSource from the registry, meaning if there is a only one
   * instance of DataSource found, then this DataSource will be used.
   */
  public fun dataSourceName(dataSourceName: String) {
    this.dataSourceName = dataSourceName
    it.url("$dataSourceName")
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
   * Specify the full package and class name to use as conversion when outputType=SelectOne or
   * SelectList.
   */
  public fun outputClass(outputClass: String) {
    it.property("outputClass", outputClass)
  }

  /**
   * Determines the output the producer should use.
   */
  public fun outputType(outputType: String) {
    it.property("outputType", outputType)
  }

  /**
   * Optional parameters to the java.sql.Statement. For example to set maxRows, fetchSize etc.
   */
  public fun parameters(parameters: String) {
    it.property("parameters", parameters)
  }

  /**
   * The default maximum number of rows that can be read by a polling query. The default value is 0.
   */
  public fun readSize(readSize: String) {
    it.property("readSize", readSize)
  }

  /**
   * The default maximum number of rows that can be read by a polling query. The default value is 0.
   */
  public fun readSize(readSize: Int) {
    it.property("readSize", readSize.toString())
  }

  /**
   * Camel will set the autoCommit on the JDBC connection to be false, commit the change after
   * executed the statement and reset the autoCommit flag of the connection at the end, if the
   * resetAutoCommit is true. If the JDBC connection doesn't support to reset the autoCommit flag, you
   * can set the resetAutoCommit flag to be false, and Camel will not try to reset the autoCommit flag.
   * When used with XA transactions you most likely need to set it to false so that the transaction
   * manager is in charge of committing this tx.
   */
  public fun resetAutoCommit(resetAutoCommit: String) {
    it.property("resetAutoCommit", resetAutoCommit)
  }

  /**
   * Camel will set the autoCommit on the JDBC connection to be false, commit the change after
   * executed the statement and reset the autoCommit flag of the connection at the end, if the
   * resetAutoCommit is true. If the JDBC connection doesn't support to reset the autoCommit flag, you
   * can set the resetAutoCommit flag to be false, and Camel will not try to reset the autoCommit flag.
   * When used with XA transactions you most likely need to set it to false so that the transaction
   * manager is in charge of committing this tx.
   */
  public fun resetAutoCommit(resetAutoCommit: Boolean) {
    it.property("resetAutoCommit", resetAutoCommit.toString())
  }

  /**
   * Whether transactions are in use.
   */
  public fun transacted(transacted: String) {
    it.property("transacted", transacted)
  }

  /**
   * Whether transactions are in use.
   */
  public fun transacted(transacted: Boolean) {
    it.property("transacted", transacted.toString())
  }

  /**
   * To read BLOB columns as bytes instead of string data. This may be needed for certain databases
   * such as Oracle where you must read BLOB columns as bytes.
   */
  public fun useGetBytesForBlob(useGetBytesForBlob: String) {
    it.property("useGetBytesForBlob", useGetBytesForBlob)
  }

  /**
   * To read BLOB columns as bytes instead of string data. This may be needed for certain databases
   * such as Oracle where you must read BLOB columns as bytes.
   */
  public fun useGetBytesForBlob(useGetBytesForBlob: Boolean) {
    it.property("useGetBytesForBlob", useGetBytesForBlob.toString())
  }

  /**
   * Set this option to true to use the prepareStatementStrategy with named parameters. This allows
   * to define queries with named placeholders, and use headers with the dynamic values for the query
   * placeholders.
   */
  public fun useHeadersAsParameters(useHeadersAsParameters: String) {
    it.property("useHeadersAsParameters", useHeadersAsParameters)
  }

  /**
   * Set this option to true to use the prepareStatementStrategy with named parameters. This allows
   * to define queries with named placeholders, and use headers with the dynamic values for the query
   * placeholders.
   */
  public fun useHeadersAsParameters(useHeadersAsParameters: Boolean) {
    it.property("useHeadersAsParameters", useHeadersAsParameters.toString())
  }

  /**
   * Sets whether to use JDBC 4 or JDBC 3.0 or older semantic when retrieving column name. JDBC 4.0
   * uses columnLabel to get the column name where as JDBC 3.0 uses both columnName or columnLabel.
   * Unfortunately JDBC drivers behave differently so you can use this option to work out issues around
   * your JDBC driver if you get problem using this component This option is default true.
   */
  public fun useJDBC4ColumnNameAndLabelSemantics(useJDBC4ColumnNameAndLabelSemantics: String) {
    it.property("useJDBC4ColumnNameAndLabelSemantics", useJDBC4ColumnNameAndLabelSemantics)
  }

  /**
   * Sets whether to use JDBC 4 or JDBC 3.0 or older semantic when retrieving column name. JDBC 4.0
   * uses columnLabel to get the column name where as JDBC 3.0 uses both columnName or columnLabel.
   * Unfortunately JDBC drivers behave differently so you can use this option to work out issues around
   * your JDBC driver if you get problem using this component This option is default true.
   */
  public fun useJDBC4ColumnNameAndLabelSemantics(useJDBC4ColumnNameAndLabelSemantics: Boolean) {
    it.property("useJDBC4ColumnNameAndLabelSemantics",
        useJDBC4ColumnNameAndLabelSemantics.toString())
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
   * To use a custom org.apache.camel.component.jdbc.BeanRowMapper when using outputClass. The
   * default implementation will lower case the row names and skip underscores, and dashes. For example
   * CUST_ID is mapped as custId.
   */
  public fun beanRowMapper(beanRowMapper: String) {
    it.property("beanRowMapper", beanRowMapper)
  }

  /**
   * To use a custom strategy for working with connections. Do not use a custom strategy when using
   * the spring-jdbc component because a special Spring ConnectionStrategy is used by default to
   * support Spring Transactions.
   */
  public fun connectionStrategy(connectionStrategy: String) {
    it.property("connectionStrategy", connectionStrategy)
  }

  /**
   * Allows the plugin to use a custom org.apache.camel.component.jdbc.JdbcPrepareStatementStrategy
   * to control preparation of the query and prepared statement.
   */
  public fun prepareStatementStrategy(prepareStatementStrategy: String) {
    it.property("prepareStatementStrategy", prepareStatementStrategy)
  }
}
