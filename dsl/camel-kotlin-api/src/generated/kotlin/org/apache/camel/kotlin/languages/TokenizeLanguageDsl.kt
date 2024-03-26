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
package org.apache.camel.kotlin.languages

import java.lang.Class
import kotlin.Boolean
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.model.language.TokenizerExpression

/**
 * Tokenize text payloads using delimiter patterns.
 */
public fun tokenize(tokenize: String, i: TokenizeLanguageDsl.() -> Unit = {}): TokenizerExpression {
  val def = TokenizerExpression(tokenize)
  TokenizeLanguageDsl(def).apply(i)
  return def
}

@CamelDslMarker
public class TokenizeLanguageDsl(
  def: TokenizerExpression,
) {
  public val def: TokenizerExpression

  init {
    this.def = def
  }

  /**
   * Sets the id of this node
   */
  public fun id(id: String) {
    def.id = id
  }

  /**
   * The (start) token to use as tokenizer, for example you can use the new line token. You can use
   * simple language as the token to support dynamic tokens.
   */
  public fun token(token: String) {
    def.token = token
  }

  /**
   * The end token to use as tokenizer if using start/end token pairs. You can use simple language
   * as the token to support dynamic tokens.
   */
  public fun endToken(endToken: String) {
    def.endToken = endToken
  }

  /**
   * To inherit namespaces from a root/parent tag name when using XML You can use simple language as
   * the tag name to support dynamic names.
   */
  public fun inheritNamespaceTagName(inheritNamespaceTagName: String) {
    def.inheritNamespaceTagName = inheritNamespaceTagName
  }

  /**
   * If the token is a regular expression pattern. The default value is false
   */
  public fun regex(regex: Boolean) {
    def.regex = regex.toString()
  }

  /**
   * If the token is a regular expression pattern. The default value is false
   */
  public fun regex(regex: String) {
    def.regex = regex
  }

  /**
   * Whether the input is XML messages. This option must be set to true if working with XML
   * payloads.
   */
  public fun xml(xml: Boolean) {
    def.xml = xml.toString()
  }

  /**
   * Whether the input is XML messages. This option must be set to true if working with XML
   * payloads.
   */
  public fun xml(xml: String) {
    def.xml = xml
  }

  /**
   * Whether to include the tokens in the parts when using pairs. When including tokens then the
   * endToken property must also be configured (to use pair mode). The default value is false
   */
  public fun includeTokens(includeTokens: Boolean) {
    def.includeTokens = includeTokens.toString()
  }

  /**
   * Whether to include the tokens in the parts when using pairs. When including tokens then the
   * endToken property must also be configured (to use pair mode). The default value is false
   */
  public fun includeTokens(includeTokens: String) {
    def.includeTokens = includeTokens
  }

  /**
   * To group N parts together, for example to split big files into chunks of 1000 lines. You can
   * use simple language as the group to support dynamic group sizes.
   */
  public fun group(group: String) {
    def.group = group
  }

  /**
   * Sets the delimiter to use when grouping. If this has not been set then token will be used as
   * the delimiter.
   */
  public fun groupDelimiter(groupDelimiter: String) {
    def.groupDelimiter = groupDelimiter
  }

  /**
   * To skip the very first element
   */
  public fun skipFirst(skipFirst: Boolean) {
    def.skipFirst = skipFirst.toString()
  }

  /**
   * To skip the very first element
   */
  public fun skipFirst(skipFirst: String) {
    def.skipFirst = skipFirst
  }

  /**
   * Source to use, instead of message body. You can prefix with variable:, header:, or property: to
   * specify kind of source. Otherwise, the source is assumed to be a variable. Use empty or null to
   * use default source, which is the message body.
   */
  public fun source(source: String) {
    def.source = source
  }

  /**
   * Sets the class of the result type (type from output)
   */
  public fun resultType(resultType: Class<*>) {
    def.resultType = resultType
  }

  /**
   * Whether to trim the value to remove leading and trailing whitespaces and line breaks
   */
  public fun trim(trim: Boolean) {
    def.trim = trim.toString()
  }

  /**
   * Whether to trim the value to remove leading and trailing whitespaces and line breaks
   */
  public fun trim(trim: String) {
    def.trim = trim
  }
}
