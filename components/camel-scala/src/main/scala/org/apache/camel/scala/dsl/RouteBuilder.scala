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
package org.apache.camel.scala.dsl;

import org.apache.camel.model.ProcessorType
import org.apache.camel.model.ChoiceType

import collection.mutable.Stack

/**
  Scala RouteBuilder implementation
  */
class RouteBuilder {

  val builder = new org.apache.camel.builder.RouteBuilder {
    override def configure() =  {}
  }

  val stack = new Stack[ProcessorType[T] forSome {type T}];

  implicit def stringToUri(uri:String) : RichUriString = new RichUriString(uri, this)
  implicit def choiceWrapper(choice: ChoiceType) = new RichChoiceType(choice, this);
  implicit def processorWrapper(processor: ProcessorType[T] forSome {type T}) = new RichProcessor(processor)
  implicit def exchangeWrapper(exchange: Exchange) = new RichExchange(exchange)

  def print() = {
    println(builder)
    this
  }

  def build(context: ProcessorType[T] forSome {type T}, block: => Unit) {
    stack.push(context)
    block
    stack.pop()
  }

  def from(uri: String) = builder.from(uri)

  def -->(uris: String*) = to(uris:_*)
  def to(uris: String*) = {
    uris.length match {
      case 1 => stack.top.to(uris(0))
      case _ => {
        val multicast = stack.top.multicast
        for (uri <- uris) multicast.to(uri)
        stack.top
      }
    }
  }

  var when = (filter: Exchange => Boolean) => {
    val choice = stack.top match {
      case c: ChoiceType => c
      case _  => stack.top.choice()
    }
    new RichChoiceType(choice.when(new WhenPredicate(filter)),  this) : RichChoiceType
  }

  def choice(block: => Unit) = {
    build(stack.top.choice(), block)
  }

  def otherwise : ChoiceType = {
    stack.top match {
      case choice: ChoiceType => choice.otherwise
      case _ => throw new Exception("otherwise is only supported in a choice block or after a when statement")
    }
  }

}
