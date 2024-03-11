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
import org.apache.camel.model.language.MethodCallExpression

/**
 * Calls a Java bean method.
 */
public fun bean(bean: String, i: BeanLanguageDsl.() -> Unit = {}): MethodCallExpression {
  val def = MethodCallExpression(bean)
  BeanLanguageDsl(def).apply(i)
  return def
}

@CamelDslMarker
public class BeanLanguageDsl(
  def: MethodCallExpression,
) {
  public val def: MethodCallExpression

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
   * Reference to an existing bean (bean id) to lookup in the registry
   */
  public fun ref(ref: String) {
    def.ref = ref
  }

  /**
   * Name of method to call
   */
  public fun method(method: String) {
    def.method = method
  }

  /**
   * Class name (fully qualified) of the bean to use Will lookup in registry and if there is a
   * single instance of the same type, then the existing bean is used, otherwise a new bean is created
   * (requires a default no-arg constructor).
   */
  public fun beanType(beanType: Class<*>) {
    def.beanType = beanType
  }

  /**
   * Scope of bean. When using singleton scope (default) the bean is created or looked up only once
   * and reused for the lifetime of the endpoint. The bean should be thread-safe in case concurrent
   * threads is calling the bean at the same time. When using request scope the bean is created or
   * looked up once per request (exchange). This can be used if you want to store state on a bean while
   * processing a request and you want to call the same bean instance multiple times while processing
   * the request. The bean does not have to be thread-safe as the instance is only called from the same
   * request. When using prototype scope, then the bean will be looked up or created per call. However
   * in case of lookup then this is delegated to the bean registry such as Spring or CDI (if in use),
   * which depends on their configuration can act as either singleton or prototype scope. So when using
   * prototype scope then this depends on the bean registry implementation.
   */
  public fun scope(scope: String) {
    def.scope = scope
  }

  /**
   * Whether to validate the bean has the configured method.
   */
  public fun validate(validate: Boolean) {
    def.validate = validate.toString()
  }

  /**
   * Whether to validate the bean has the configured method.
   */
  public fun validate(validate: String) {
    def.validate = validate
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
