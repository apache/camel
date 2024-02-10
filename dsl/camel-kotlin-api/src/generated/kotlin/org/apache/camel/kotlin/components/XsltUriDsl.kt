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

public fun UriDsl.xslt(i: XsltUriDsl.() -> Unit) {
  XsltUriDsl(this).apply(i)
}

@CamelDslMarker
public class XsltUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("xslt")
  }

  private var resourceUri: String = ""

  public fun resourceUri(resourceUri: String) {
    this.resourceUri = resourceUri
    it.url("$resourceUri")
  }

  public fun allowTemplateFromHeader(allowTemplateFromHeader: String) {
    it.property("allowTemplateFromHeader", allowTemplateFromHeader)
  }

  public fun allowTemplateFromHeader(allowTemplateFromHeader: Boolean) {
    it.property("allowTemplateFromHeader", allowTemplateFromHeader.toString())
  }

  public fun contentCache(contentCache: String) {
    it.property("contentCache", contentCache)
  }

  public fun contentCache(contentCache: Boolean) {
    it.property("contentCache", contentCache.toString())
  }

  public fun deleteOutputFile(deleteOutputFile: String) {
    it.property("deleteOutputFile", deleteOutputFile)
  }

  public fun deleteOutputFile(deleteOutputFile: Boolean) {
    it.property("deleteOutputFile", deleteOutputFile.toString())
  }

  public fun failOnNullBody(failOnNullBody: String) {
    it.property("failOnNullBody", failOnNullBody)
  }

  public fun failOnNullBody(failOnNullBody: Boolean) {
    it.property("failOnNullBody", failOnNullBody.toString())
  }

  public fun output(output: String) {
    it.property("output", output)
  }

  public fun transformerCacheSize(transformerCacheSize: String) {
    it.property("transformerCacheSize", transformerCacheSize)
  }

  public fun transformerCacheSize(transformerCacheSize: Int) {
    it.property("transformerCacheSize", transformerCacheSize.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun entityResolver(entityResolver: String) {
    it.property("entityResolver", entityResolver)
  }

  public fun errorListener(errorListener: String) {
    it.property("errorListener", errorListener)
  }

  public fun resultHandlerFactory(resultHandlerFactory: String) {
    it.property("resultHandlerFactory", resultHandlerFactory)
  }

  public fun transformerFactory(transformerFactory: String) {
    it.property("transformerFactory", transformerFactory)
  }

  public fun transformerFactoryClass(transformerFactoryClass: String) {
    it.property("transformerFactoryClass", transformerFactoryClass)
  }

  public
      fun transformerFactoryConfigurationStrategy(transformerFactoryConfigurationStrategy: String) {
    it.property("transformerFactoryConfigurationStrategy", transformerFactoryConfigurationStrategy)
  }

  public fun uriResolver(uriResolver: String) {
    it.property("uriResolver", uriResolver)
  }

  public fun xsltMessageLogger(xsltMessageLogger: String) {
    it.property("xsltMessageLogger", xsltMessageLogger)
  }
}
