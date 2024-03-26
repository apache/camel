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
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

/**
 * Perform SQL queries as a JDBC Stored Procedures using Spring JDBC.
 */
public fun UriDsl.`sql-stored`(i: SqlStoredUriDsl.() -> Unit) {
  SqlStoredUriDsl(this).apply(i)
}

@CamelDslMarker
public class SqlStoredUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("sql-stored")
  }

  private var template: String = ""

  /**
   * Sets the stored procedure template to perform. You can externalize the template by using file:
   * or classpath: as prefix and specify the location of the file.
   */
  public fun template(template: String) {
    this.template = template
    it.url("$template")
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
   * Sets the DataSource to use to communicate with the database.
   */
  public fun dataSource(dataSource: String) {
    it.property("dataSource", dataSource)
  }

  /**
   * Whether this call is for a function.
   */
  public fun function(function: String) {
    it.property("function", function)
  }

  /**
   * Whether this call is for a function.
   */
  public fun function(function: Boolean) {
    it.property("function", function.toString())
  }

  /**
   * If set, will ignore the results of the stored procedure template and use the existing IN
   * message as the OUT message for the continuation of processing
   */
  public fun noop(noop: String) {
    it.property("noop", noop)
  }

  /**
   * If set, will ignore the results of the stored procedure template and use the existing IN
   * message as the OUT message for the continuation of processing
   */
  public fun noop(noop: Boolean) {
    it.property("noop", noop.toString())
  }

  /**
   * Store the template result in a header instead of the message body. By default, outputHeader ==
   * null and the template result is stored in the message body, any existing content in the message
   * body is discarded. If outputHeader is set, the value is used as the name of the header to store
   * the template result and the original message body is preserved.
   */
  public fun outputHeader(outputHeader: String) {
    it.property("outputHeader", outputHeader)
  }

  /**
   * Whether to use the message body as the stored procedure template and then headers for
   * parameters. If this option is enabled then the template in the uri is not used.
   */
  public fun useMessageBodyForTemplate(useMessageBodyForTemplate: String) {
    it.property("useMessageBodyForTemplate", useMessageBodyForTemplate)
  }

  /**
   * Whether to use the message body as the stored procedure template and then headers for
   * parameters. If this option is enabled then the template in the uri is not used.
   */
  public fun useMessageBodyForTemplate(useMessageBodyForTemplate: Boolean) {
    it.property("useMessageBodyForTemplate", useMessageBodyForTemplate.toString())
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
   * Configures the Spring JdbcTemplate with the key/values from the Map
   */
  public fun templateOptions(templateOptions: String) {
    it.property("templateOptions", templateOptions)
  }
}
