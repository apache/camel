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

/**
 * Invoke methods of Java beans specified by class name.
 */
public fun UriDsl.`class`(i: ClassUriDsl.() -> Unit) {
  ClassUriDsl(this).apply(i)
}

@CamelDslMarker
public class ClassUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("class")
  }

  private var beanName: String = ""

  /**
   * Sets the name of the bean to invoke
   */
  public fun beanName(beanName: String) {
    this.beanName = beanName
    it.url("$beanName")
  }

  /**
   * Sets the name of the method to invoke on the bean
   */
  public fun method(method: String) {
    it.property("method", method)
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
   * which depends on their configuration can act as either singleton or prototype scope. so when using
   * prototype then this depends on the delegated registry.
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

  /**
   * Used for configuring additional properties on the bean
   */
  public fun parameters(parameters: String) {
    it.property("parameters", parameters)
  }
}
