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

public fun UriDsl.git(i: GitUriDsl.() -> Unit) {
  GitUriDsl(this).apply(i)
}

@CamelDslMarker
public class GitUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("git")
  }

  private var localPath: String = ""

  public fun localPath(localPath: String) {
    this.localPath = localPath
    it.url("$localPath")
  }

  public fun branchName(branchName: String) {
    it.property("branchName", branchName)
  }

  public fun type(type: String) {
    it.property("type", type)
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

  public fun allowEmpty(allowEmpty: String) {
    it.property("allowEmpty", allowEmpty)
  }

  public fun allowEmpty(allowEmpty: Boolean) {
    it.property("allowEmpty", allowEmpty.toString())
  }

  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun remoteName(remoteName: String) {
    it.property("remoteName", remoteName)
  }

  public fun remotePath(remotePath: String) {
    it.property("remotePath", remotePath)
  }

  public fun tagName(tagName: String) {
    it.property("tagName", tagName)
  }

  public fun targetBranchName(targetBranchName: String) {
    it.property("targetBranchName", targetBranchName)
  }

  public fun username(username: String) {
    it.property("username", username)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun gitConfigFile(gitConfigFile: String) {
    it.property("gitConfigFile", gitConfigFile)
  }
}
