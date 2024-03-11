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
 * Perform inserts or queries against Apache Lucene databases.
 */
public fun UriDsl.lucene(i: LuceneUriDsl.() -> Unit) {
  LuceneUriDsl(this).apply(i)
}

@CamelDslMarker
public class LuceneUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("lucene")
  }

  private var host: String = ""

  private var operation: String = ""

  /**
   * The URL to the lucene server
   */
  public fun host(host: String) {
    this.host = host
    it.url("$host:$operation")
  }

  /**
   * Operation to do such as insert or query.
   */
  public fun operation(operation: String) {
    this.operation = operation
    it.url("$host:$operation")
  }

  /**
   * An Analyzer builds TokenStreams, which analyze text. It thus represents a policy for extracting
   * index terms from text. The value for analyzer can be any class that extends the abstract class
   * org.apache.lucene.analysis.Analyzer. Lucene also offers a rich set of analyzers out of the box
   */
  public fun analyzer(analyzer: String) {
    it.property("analyzer", analyzer)
  }

  /**
   * A file system directory in which index files are created upon analysis of the document by the
   * specified analyzer
   */
  public fun indexDir(indexDir: String) {
    it.property("indexDir", indexDir)
  }

  /**
   * An integer value that limits the result set of the search operation
   */
  public fun maxHits(maxHits: String) {
    it.property("maxHits", maxHits)
  }

  /**
   * An integer value that limits the result set of the search operation
   */
  public fun maxHits(maxHits: Int) {
    it.property("maxHits", maxHits.toString())
  }

  /**
   * An optional directory containing files to be used to be analyzed and added to the index at
   * producer startup.
   */
  public fun srcDir(srcDir: String) {
    it.property("srcDir", srcDir)
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
