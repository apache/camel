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

public fun UriDsl.ldap(i: LdapUriDsl.() -> Unit) {
  LdapUriDsl(this).apply(i)
}

@CamelDslMarker
public class LdapUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("ldap")
  }

  private var dirContextName: String = ""

  public fun dirContextName(dirContextName: String) {
    this.dirContextName = dirContextName
    it.url("$dirContextName")
  }

  public fun base(base: String) {
    it.property("base", base)
  }

  public fun pageSize(pageSize: String) {
    it.property("pageSize", pageSize)
  }

  public fun pageSize(pageSize: Int) {
    it.property("pageSize", pageSize.toString())
  }

  public fun returnedAttributes(returnedAttributes: String) {
    it.property("returnedAttributes", returnedAttributes)
  }

  public fun scope(scope: String) {
    it.property("scope", scope)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
