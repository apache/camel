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
package org.apache.camel.scala.builder;

import org.apache.camel.model.ProcessorType
import org.apache.camel.model.ChoiceType

import collection.mutable.Stack

import org.apache.camel.scala.dsl._

/**
  Scala RouteBuilder implementation
  */
class RouteBuilder extends Preamble {

  val builder = new org.apache.camel.builder.RouteBuilder {
    override def configure() =  {}
  }

  val stack = new Stack[ScalaDsl];

  implicit def stringToRoute(target: String) : SRouteType = new SRouteType(builder.from(target), this)  
  implicit def unwrap[W](wrapper: Wrapper[W]) = wrapper.unwrap

  def print() = {
    println(builder)
    this
  }

  def build(context: ScalaDsl, block: => Unit) {
    stack.push(context)
    block
    stack.pop()
  }

  def from(uri: String) = new SRouteType(builder.from(uri), this)

  def choice = stack.top.choice
  def -->(uris: String*) = stack.top.to(uris: _*)
  def to(uris: String*) = stack.top.to(uris: _*)
  def when(filter: Exchange => Boolean) = stack.top.when(filter)
  def as[Target](toType: Class[Target]) = stack.top.as(toType)
  def recipients(expression: Exchange => Any) = stack.top.recipients(expression)
  def splitter(expression: Exchange => Any) = stack.top.splitter(expression)
  def otherwise = stack.top.otherwise
  def multicast = stack.top.multicast
  def process(function: Exchange => Unit) = stack.top.process(function)
  def throttle(frequency: Frequency) = stack.top.throttle(frequency)
  def loadbalance = stack.top.loadbalance
  def delay(delay: Period) = stack.top.delay(delay)
  def resequence(expression: Exchange => Any) = stack.top.resequence(expression)

}
