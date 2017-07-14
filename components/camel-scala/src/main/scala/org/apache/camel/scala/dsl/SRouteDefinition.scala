/**
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
package org.apache.camel.scala.dsl

import org.apache.camel.model.RouteDefinition
import org.apache.camel.scala.dsl.builder.RouteBuilder
import org.apache.camel.builder.ErrorHandlerBuilder
import org.apache.camel.spi.RoutePolicy

import scala.collection.JavaConversions._

case class SRouteDefinition(override val target: RouteDefinition, builder: RouteBuilder) extends SAbstractDefinition[RouteDefinition] {
 
  def ==> (block: => Unit) : SRouteDefinition = this.apply(block).asInstanceOf[SRouteDefinition]

  def ::(id: String) : SRouteDefinition = {
    target.routeId(id)
    this
  }

  def errorHandler(handler: ErrorHandlerBuilder): SRouteDefinition = {
    target.errorHandler(handler)
    this
  }

  def autoStartup(autoStartup: String) = wrap(target.autoStartup(autoStartup))

  def autoStartup(autoStartup: Boolean) = wrap(target.autoStartup(autoStartup))

  def noAutoStartup() = wrap(target.autoStartup(false))

  def routePolicy(routePolicy: RoutePolicy*) = wrap(target.setRoutePolicies(routePolicy))
  
}
