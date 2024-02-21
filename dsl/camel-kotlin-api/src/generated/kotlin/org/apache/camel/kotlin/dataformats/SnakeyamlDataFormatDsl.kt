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
import kotlin.Any
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

public fun DataFormatDsl.snakeYaml(i: SnakeyamlDataFormatDsl.() -> Unit) {
  def = SnakeyamlDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class SnakeyamlDataFormatDsl {
  public val def: YAMLDataFormat

  init {
    def = YAMLDataFormat()}

  public fun id(id: String) {
    def.id = id
  }

  public fun library(library: YAMLLibrary) {
    def.library = library
  }

  public fun unmarshalType(unmarshalType: Class<out Any>) {
    def.unmarshalType = unmarshalType
  }

  public fun `constructor`(`constructor`: String) {
    def.constructor = constructor
  }

  public fun representer(representer: String) {
    def.representer = representer
  }

  public fun dumperOptions(dumperOptions: String) {
    def.dumperOptions = dumperOptions
  }

  public fun resolver(resolver: String) {
    def.resolver = resolver
  }

  public fun useApplicationContextClassLoader(useApplicationContextClassLoader: Boolean) {
    def.useApplicationContextClassLoader = useApplicationContextClassLoader.toString()
  }

  public fun useApplicationContextClassLoader(useApplicationContextClassLoader: String) {
    def.useApplicationContextClassLoader = useApplicationContextClassLoader
  }

  public fun prettyFlow(prettyFlow: Boolean) {
    def.prettyFlow = prettyFlow.toString()
  }

  public fun prettyFlow(prettyFlow: String) {
    def.prettyFlow = prettyFlow
  }

  public fun allowAnyType(allowAnyType: Boolean) {
    def.allowAnyType = allowAnyType.toString()
  }

  public fun allowAnyType(allowAnyType: String) {
    def.allowAnyType = allowAnyType
  }

  public fun typeFilters(typeFilters: MutableList<YAMLTypeFilterDefinition>) {
    def.typeFilters = typeFilters
  }

  public fun maxAliasesForCollections(maxAliasesForCollections: Int) {
    def.maxAliasesForCollections = maxAliasesForCollections.toString()
  }

  public fun maxAliasesForCollections(maxAliasesForCollections: String) {
    def.maxAliasesForCollections = maxAliasesForCollections
  }

  public fun allowRecursiveKeys(allowRecursiveKeys: Boolean) {
    def.allowRecursiveKeys = allowRecursiveKeys.toString()
  }

  public fun allowRecursiveKeys(allowRecursiveKeys: String) {
    def.allowRecursiveKeys = allowRecursiveKeys
  }
}
