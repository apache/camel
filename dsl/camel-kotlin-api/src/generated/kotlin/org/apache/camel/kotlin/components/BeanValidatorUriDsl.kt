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
 * Validate the message body using the Java Bean Validation API.
 */
public fun UriDsl.`bean-validator`(i: BeanValidatorUriDsl.() -> Unit) {
  BeanValidatorUriDsl(this).apply(i)
}

@CamelDslMarker
public class BeanValidatorUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("bean-validator")
  }

  private var label: String = ""

  /**
   * Where label is an arbitrary text value describing the endpoint
   */
  public fun label(label: String) {
    this.label = label
    it.url("$label")
  }

  /**
   * To use a custom validation group
   */
  public fun group(group: String) {
    it.property("group", group)
  }

  /**
   * Whether to ignore data from the META-INF/validation.xml file.
   */
  public fun ignoreXmlConfiguration(ignoreXmlConfiguration: String) {
    it.property("ignoreXmlConfiguration", ignoreXmlConfiguration)
  }

  /**
   * Whether to ignore data from the META-INF/validation.xml file.
   */
  public fun ignoreXmlConfiguration(ignoreXmlConfiguration: Boolean) {
    it.property("ignoreXmlConfiguration", ignoreXmlConfiguration.toString())
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
   * To use a custom ConstraintValidatorFactory
   */
  public fun constraintValidatorFactory(constraintValidatorFactory: String) {
    it.property("constraintValidatorFactory", constraintValidatorFactory)
  }

  /**
   * To use a custom MessageInterpolator
   */
  public fun messageInterpolator(messageInterpolator: String) {
    it.property("messageInterpolator", messageInterpolator)
  }

  /**
   * To use a custom TraversableResolver
   */
  public fun traversableResolver(traversableResolver: String) {
    it.property("traversableResolver", traversableResolver)
  }

  /**
   * To use a a custom ValidationProviderResolver
   */
  public fun validationProviderResolver(validationProviderResolver: String) {
    it.property("validationProviderResolver", validationProviderResolver)
  }

  /**
   * To use a custom ValidatorFactory
   */
  public fun validatorFactory(validatorFactory: String) {
    it.property("validatorFactory", validatorFactory)
  }
}
