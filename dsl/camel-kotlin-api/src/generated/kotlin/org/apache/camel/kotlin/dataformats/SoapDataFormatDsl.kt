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

import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.DataFormatDsl
import org.apache.camel.model.dataformat.SoapDataFormat

public fun DataFormatDsl.soap(i: SoapDataFormatDsl.() -> Unit) {
  def = SoapDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class SoapDataFormatDsl {
  public val def: SoapDataFormat

  init {
    def = SoapDataFormat()}

  public fun id(id: String) {
    def.id = id
  }

  public fun contextPath(contextPath: String) {
    def.contextPath = contextPath
  }

  public fun encoding(encoding: String) {
    def.encoding = encoding
  }

  public fun elementNameStrategyRef(elementNameStrategyRef: String) {
    def.elementNameStrategyRef = elementNameStrategyRef
  }

  public fun version(version: String) {
    def.version = version
  }

  public fun namespacePrefixRef(namespacePrefixRef: String) {
    def.namespacePrefixRef = namespacePrefixRef
  }

  public fun schema(schema: String) {
    def.schema = schema
  }
}
