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
package org.apache.camel.kotlin.dataformats

import java.lang.Class
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Unit
import kotlin.collections.MutableList
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.DataFormatDsl
import org.apache.camel.model.dataformat.YAMLDataFormat
import org.apache.camel.model.dataformat.YAMLLibrary
import org.apache.camel.model.dataformat.YAMLTypeFilterDefinition

/**
 * Marshal and unmarshal Java objects to and from YAML using SnakeYAML
 */
public fun DataFormatDsl.snakeYaml(i: SnakeYamlDataFormatDsl.() -> Unit) {
  def = SnakeYamlDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class SnakeYamlDataFormatDsl {
  public val def: YAMLDataFormat

  init {
    def = YAMLDataFormat()}

  /**
   * The id of this node
   */
  public fun id(id: String) {
    def.id = id
  }

  /**
   * Which yaml library to use. By default it is SnakeYAML
   */
  public fun library(library: YAMLLibrary) {
    def.library = library
  }

  /**
   * Class name of the java type to use when unmarshalling
   */
  public fun unmarshalType(unmarshalType: Class<*>) {
    def.unmarshalType = unmarshalType
  }

  /**
   * BaseConstructor to construct incoming documents.
   */
  public fun `constructor`(`constructor`: String) {
    def.constructor = constructor
  }

  /**
   * Representer to emit outgoing objects.
   */
  public fun representer(representer: String) {
    def.representer = representer
  }

  /**
   * DumperOptions to configure outgoing objects.
   */
  public fun dumperOptions(dumperOptions: String) {
    def.dumperOptions = dumperOptions
  }

  /**
   * Resolver to detect implicit type
   */
  public fun resolver(resolver: String) {
    def.resolver = resolver
  }

  /**
   * Use ApplicationContextClassLoader as custom ClassLoader
   */
  public fun useApplicationContextClassLoader(useApplicationContextClassLoader: Boolean) {
    def.useApplicationContextClassLoader = useApplicationContextClassLoader.toString()
  }

  /**
   * Use ApplicationContextClassLoader as custom ClassLoader
   */
  public fun useApplicationContextClassLoader(useApplicationContextClassLoader: String) {
    def.useApplicationContextClassLoader = useApplicationContextClassLoader
  }

  /**
   * Force the emitter to produce a pretty YAML document when using the flow style.
   */
  public fun prettyFlow(prettyFlow: Boolean) {
    def.prettyFlow = prettyFlow.toString()
  }

  /**
   * Force the emitter to produce a pretty YAML document when using the flow style.
   */
  public fun prettyFlow(prettyFlow: String) {
    def.prettyFlow = prettyFlow
  }

  /**
   * Allow any class to be un-marshaled
   */
  public fun allowAnyType(allowAnyType: Boolean) {
    def.allowAnyType = allowAnyType.toString()
  }

  /**
   * Allow any class to be un-marshaled
   */
  public fun allowAnyType(allowAnyType: String) {
    def.allowAnyType = allowAnyType
  }

  /**
   * Set the types SnakeYAML is allowed to un-marshall
   */
  public fun typeFilters(typeFilters: MutableList<YAMLTypeFilterDefinition>) {
    def.typeFilters = typeFilters
  }

  /**
   * Set the maximum amount of aliases allowed for collections.
   */
  public fun maxAliasesForCollections(maxAliasesForCollections: Int) {
    def.maxAliasesForCollections = maxAliasesForCollections.toString()
  }

  /**
   * Set the maximum amount of aliases allowed for collections.
   */
  public fun maxAliasesForCollections(maxAliasesForCollections: String) {
    def.maxAliasesForCollections = maxAliasesForCollections
  }

  /**
   * Set whether recursive keys are allowed.
   */
  public fun allowRecursiveKeys(allowRecursiveKeys: Boolean) {
    def.allowRecursiveKeys = allowRecursiveKeys.toString()
  }

  /**
   * Set whether recursive keys are allowed.
   */
  public fun allowRecursiveKeys(allowRecursiveKeys: String) {
    def.allowRecursiveKeys = allowRecursiveKeys
  }
}
