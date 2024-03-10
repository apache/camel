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
 * Perform searches on LDAP servers.
 */
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

  /**
   * Name of either a javax.naming.directory.DirContext, or java.util.Hashtable, or Map bean to
   * lookup in the registry. If the bean is either a Hashtable or Map then a new
   * javax.naming.directory.DirContext instance is created for each use. If the bean is a
   * javax.naming.directory.DirContext then the bean is used as given. The latter may not be possible
   * in all situations where the javax.naming.directory.DirContext must not be shared, and in those
   * situations it can be better to use java.util.Hashtable or Map instead.
   */
  public fun dirContextName(dirContextName: String) {
    this.dirContextName = dirContextName
    it.url("$dirContextName")
  }

  /**
   * The base DN for searches.
   */
  public fun base(base: String) {
    it.property("base", base)
  }

  /**
   * When specified the ldap module uses paging to retrieve all results (most LDAP Servers throw an
   * exception when trying to retrieve more than 1000 entries in one query). To be able to use this a
   * LdapContext (subclass of DirContext) has to be passed in as ldapServerBean (otherwise an exception
   * is thrown)
   */
  public fun pageSize(pageSize: String) {
    it.property("pageSize", pageSize)
  }

  /**
   * When specified the ldap module uses paging to retrieve all results (most LDAP Servers throw an
   * exception when trying to retrieve more than 1000 entries in one query). To be able to use this a
   * LdapContext (subclass of DirContext) has to be passed in as ldapServerBean (otherwise an exception
   * is thrown)
   */
  public fun pageSize(pageSize: Int) {
    it.property("pageSize", pageSize.toString())
  }

  /**
   * Comma-separated list of attributes that should be set in each entry of the result
   */
  public fun returnedAttributes(returnedAttributes: String) {
    it.property("returnedAttributes", returnedAttributes)
  }

  /**
   * Specifies how deeply to search the tree of entries, starting at the base DN.
   */
  public fun scope(scope: String) {
    it.property("scope", scope)
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
