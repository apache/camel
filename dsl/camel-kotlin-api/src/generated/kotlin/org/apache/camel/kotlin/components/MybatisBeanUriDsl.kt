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
 * Perform queries, inserts, updates or deletes in a relational database using MyBatis.
 */
public fun UriDsl.`mybatis-bean`(i: MybatisBeanUriDsl.() -> Unit) {
  MybatisBeanUriDsl(this).apply(i)
}

@CamelDslMarker
public class MybatisBeanUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("mybatis-bean")
  }

  private var beanName: String = ""

  private var methodName: String = ""

  /**
   * Name of the bean with the MyBatis annotations. This can either by a type alias or a FQN class
   * name.
   */
  public fun beanName(beanName: String) {
    this.beanName = beanName
    it.url("$beanName:$methodName")
  }

  /**
   * Name of the method on the bean that has the SQL query to be executed.
   */
  public fun methodName(methodName: String) {
    this.methodName = methodName
    it.url("$beanName:$methodName")
  }

  /**
   * The executor type to be used while executing statements. simple - executor does nothing
   * special. reuse - executor reuses prepared statements. batch - executor reuses statements and
   * batches updates.
   */
  public fun executorType(executorType: String) {
    it.property("executorType", executorType)
  }

  /**
   * User the header value for input parameters instead of the message body. By default, inputHeader
   * == null and the input parameters are taken from the message body. If outputHeader is set, the
   * value is used and query parameters will be taken from the header instead of the body.
   */
  public fun inputHeader(inputHeader: String) {
    it.property("inputHeader", inputHeader)
  }

  /**
   * Store the query result in a header instead of the message body. By default, outputHeader ==
   * null and the query result is stored in the message body, any existing content in the message body
   * is discarded. If outputHeader is set, the value is used as the name of the header to store the
   * query result and the original message body is preserved. Setting outputHeader will also omit
   * populating the default CamelMyBatisResult header since it would be the same as outputHeader all
   * the time.
   */
  public fun outputHeader(outputHeader: String) {
    it.property("outputHeader", outputHeader)
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
}
