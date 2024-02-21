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

public fun UriDsl.jdbc(i: JdbcUriDsl.() -> Unit) {
  JdbcUriDsl(this).apply(i)
}

@CamelDslMarker
public class JdbcUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("jdbc")
  }

  private var dataSourceName: String = ""

  public fun dataSourceName(dataSourceName: String) {
    this.dataSourceName = dataSourceName
    it.url("$dataSourceName")
  }

  public fun allowNamedParameters(allowNamedParameters: String) {
    it.property("allowNamedParameters", allowNamedParameters)
  }

  public fun allowNamedParameters(allowNamedParameters: Boolean) {
    it.property("allowNamedParameters", allowNamedParameters.toString())
  }

  public fun outputClass(outputClass: String) {
    it.property("outputClass", outputClass)
  }

  public fun outputType(outputType: String) {
    it.property("outputType", outputType)
  }

  public fun parameters(parameters: String) {
    it.property("parameters", parameters)
  }

  public fun readSize(readSize: String) {
    it.property("readSize", readSize)
  }

  public fun readSize(readSize: Int) {
    it.property("readSize", readSize.toString())
  }

  public fun resetAutoCommit(resetAutoCommit: String) {
    it.property("resetAutoCommit", resetAutoCommit)
  }

  public fun resetAutoCommit(resetAutoCommit: Boolean) {
    it.property("resetAutoCommit", resetAutoCommit.toString())
  }

  public fun transacted(transacted: String) {
    it.property("transacted", transacted)
  }

  public fun transacted(transacted: Boolean) {
    it.property("transacted", transacted.toString())
  }

  public fun useGetBytesForBlob(useGetBytesForBlob: String) {
    it.property("useGetBytesForBlob", useGetBytesForBlob)
  }

  public fun useGetBytesForBlob(useGetBytesForBlob: Boolean) {
    it.property("useGetBytesForBlob", useGetBytesForBlob.toString())
  }

  public fun useHeadersAsParameters(useHeadersAsParameters: String) {
    it.property("useHeadersAsParameters", useHeadersAsParameters)
  }

  public fun useHeadersAsParameters(useHeadersAsParameters: Boolean) {
    it.property("useHeadersAsParameters", useHeadersAsParameters.toString())
  }

  public fun useJDBC4ColumnNameAndLabelSemantics(useJDBC4ColumnNameAndLabelSemantics: String) {
    it.property("useJDBC4ColumnNameAndLabelSemantics", useJDBC4ColumnNameAndLabelSemantics)
  }

  public fun useJDBC4ColumnNameAndLabelSemantics(useJDBC4ColumnNameAndLabelSemantics: Boolean) {
    it.property("useJDBC4ColumnNameAndLabelSemantics",
        useJDBC4ColumnNameAndLabelSemantics.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun beanRowMapper(beanRowMapper: String) {
    it.property("beanRowMapper", beanRowMapper)
  }

  public fun connectionStrategy(connectionStrategy: String) {
    it.property("connectionStrategy", connectionStrategy)
  }

  public fun prepareStatementStrategy(prepareStatementStrategy: String) {
    it.property("prepareStatementStrategy", prepareStatementStrategy)
  }
}
