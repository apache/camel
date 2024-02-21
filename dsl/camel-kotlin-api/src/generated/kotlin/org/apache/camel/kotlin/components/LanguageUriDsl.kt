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

public fun UriDsl.language(i: LanguageUriDsl.() -> Unit) {
  LanguageUriDsl(this).apply(i)
}

@CamelDslMarker
public class LanguageUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("language")
  }

  private var languageName: String = ""

  private var resourceUri: String = ""

  public fun languageName(languageName: String) {
    this.languageName = languageName
    it.url("$languageName:$resourceUri")
  }

  public fun resourceUri(resourceUri: String) {
    this.resourceUri = resourceUri
    it.url("$languageName:$resourceUri")
  }

  public fun allowContextMapAll(allowContextMapAll: String) {
    it.property("allowContextMapAll", allowContextMapAll)
  }

  public fun allowContextMapAll(allowContextMapAll: Boolean) {
    it.property("allowContextMapAll", allowContextMapAll.toString())
  }

  public fun binary(binary: String) {
    it.property("binary", binary)
  }

  public fun binary(binary: Boolean) {
    it.property("binary", binary.toString())
  }

  public fun cacheScript(cacheScript: String) {
    it.property("cacheScript", cacheScript)
  }

  public fun cacheScript(cacheScript: Boolean) {
    it.property("cacheScript", cacheScript.toString())
  }

  public fun contentCache(contentCache: String) {
    it.property("contentCache", contentCache)
  }

  public fun contentCache(contentCache: Boolean) {
    it.property("contentCache", contentCache.toString())
  }

  public fun resultType(resultType: String) {
    it.property("resultType", resultType)
  }

  public fun script(script: String) {
    it.property("script", script)
  }

  public fun transform(transform: String) {
    it.property("transform", transform)
  }

  public fun transform(transform: Boolean) {
    it.property("transform", transform.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
