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
package org.apache.camel.scala.dsl;

import org.apache.camel.model._
import org.apache.camel.spi.Policy

import org.apache.camel.model.dataformat.DataFormatDefinition

import org.apache.camel.processor.aggregate.AggregationStrategy

import org.apache.camel.scala.dsl.builder.RouteBuilder

abstract class SAbstractDefinition[P <: ProcessorDefinition[_]] extends DSL with Wrapper[P] with Block {

  val target : P
  val unwrap = target
  implicit val builder: RouteBuilder
  implicit def expressionBuilder(expression: Exchange => Any) = new ScalaExpression(expression)
  implicit def predicateBuilder(predicate: Exchange => Boolean) = new ScalaPredicate(predicate)
  
  def -->(uris: String*) = to(uris:_*)
  def to(uris: String*) = {
    uris.length match {
      case 1 => target.to(uris(0))
      case _ => {
        val multicast = target.multicast
        uris.foreach(multicast.to(_))
      }
    }
    this
  }
  
  def when(filter: Exchange => Boolean) : DSL with Block = SChoiceDefinition(target.choice).when(filter)
    
  def as[Target](toType: Class[Target]) = wrap(target.convertBodyTo(toType))
  
  def attempt : STryDefinition = STryDefinition(target.doTry)
  
  def split(expression: Exchange => Any) = SSplitDefinition(target.split(expression))
    
  def apply(block: => Unit) = {
    builder.build(this, block)
    this
  }

  def bean(bean: Any) = bean match {
    case cls: Class[_] => wrap(target.bean(cls))
    case ref: String => wrap(target.beanRef(ref))
    case obj: Any => wrap(target.bean(obj))
  }
  
  def choice = SChoiceDefinition(target.choice)
  
  def enrich(uri: String, strategy: AggregationStrategy) = wrap(target.enrich(uri, strategy))
    
  def otherwise : SChoiceDefinition = 
    throw new Exception("otherwise is only supported in a choice block or after a when statement")
  
  def idempotentconsumer(expression: Exchange => Any) = SIdempotentConsumerDefinition(target.idempotentConsumer(expression, null))
  
  def inOnly = wrap(target.inOnly)
  def inOut = wrap(target.inOut)
  
  def loop(expression: Exchange => Any) = SLoopDefinition(target.loop(expression))
  
  def marshal(format: DataFormatDefinition) = wrap(target.marshal(format))
  
  def multicast = SMulticastDefinition(target.multicast)
  
  def process(function: Exchange => Unit) = wrap(target.process(new ScalaProcessor(function)))
  def process(processor: Processor) = wrap(target.process(processor))
 
  def throttle(frequency: Frequency) = SThrottleDefinition(target.throttle(frequency.count).timePeriodMillis(frequency.period.milliseconds))
  
  def loadbalance = SLoadBalanceDefinition(target.loadBalance)
  
  def delay(period: Period) = SDelayDefinition(target.delay(period.milliseconds))
  
  def onCompletion : SOnCompletionDefinition = {
    var completion = SOnCompletionDefinition(target.onCompletion)
    // let's end the block in the Java DSL, we have a better way of handling blocks here
    completion.target.end
    completion
  }
  def onCompletion(predicate: Exchange => Boolean) = onCompletion().when(predicate).asInstanceOf[SOnCompletionDefinition]
  def onCompletion(config: Config[SOnCompletionDefinition]) = {
    val completion = onCompletion().asInstanceOf[SOnCompletionDefinition]
    config.configure(completion)
    completion
  }
  
  def policy(policy: Policy) = wrap(target.policy(policy))

  def recipients(expression: Exchange => Any) = wrap(target.recipientList(expression))
  
  def resequence(expression: Exchange => Any) = SResequenceDefinition(target.resequence(expression))
  
  def rollback = wrap(target.rollback)
  
  def setbody(expression: Exchange => Any) = wrap(target.setBody(expression))
  
  def setheader(name: String, expression: Exchange => Any) = wrap(target.setHeader(name, expression))
  
  def unmarshal(format: DataFormatDefinition) = wrap(target.unmarshal(format))
  
  def wiretap(uri: String) = wrap(target.wireTap(uri))
  def wiretap(uri: String, expression: Exchange => Any) = wrap(target.wireTap(uri, expression))
  
  def aggregate(expression: Exchange => Any) = SAggregateDefinition(target.aggregate(expression))

  /**
   * Helper method to return this Scala type instead of creating another wrapper type for the processor
   */
  def wrap(block : => Unit) : SAbstractDefinition[_]  = {
     block
     this
  }
}
