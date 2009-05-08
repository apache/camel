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

import org.apache.camel.model.ProcessorDefinition
import org.apache.camel.model.FilterDefinition
import org.apache.camel.model.{ChoiceDefinition, EnrichDefinition}
import org.apache.camel.model.IdempotentConsumerDefinition

import org.apache.camel.model.dataformat.DataFormatDefinition

import org.apache.camel.processor.aggregate.AggregationStrategy

import org.apache.camel.scala.dsl.builder.RouteBuilder

abstract class SAbstractDefinition extends DSL {
  
  type RawProcessorDefinition = ProcessorDefinition[P] forSome {type P}
  
  val target : ProcessorDefinition[T] forSome {type T}
  implicit val builder: RouteBuilder
  implicit def expressionBuilder(expression: Exchange => Any) = new ScalaExpression(expression)
  
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
  
  def when(filter: Exchange => Boolean) : SChoiceDefinition =
    new SChoiceDefinition(target.choice).when(filter)
    
  def as[Target](toType: Class[Target]) = {
    target.convertBodyTo(toType)
    new SProcessorDefinition(target.asInstanceOf[RawProcessorDefinition])
  }
  
  def attempt : STryDefinition = new STryDefinition(target.doTry)
  
  def split(expression: Exchange => Any) = 
    new SSplitDefinition(target.split(expression))
    
  def recipients(expression: Exchange => Any) = 
    new SProcessorDefinition(target.recipientList(expression).asInstanceOf[RawProcessorDefinition])

  def apply(block: => Unit) = {
    builder.build(this, block)
    this
  }

  def bean(bean: Any) = bean match {
    case cls: Class[_] => new SProcessorDefinition(target.bean(cls).asInstanceOf[RawProcessorDefinition])
    case ref: String => new SProcessorDefinition(target.beanRef(ref).asInstanceOf[RawProcessorDefinition])
    case obj: Any => new SProcessorDefinition(target.bean(obj).asInstanceOf[RawProcessorDefinition])
  }
  
  def choice = new SChoiceDefinition(target.choice)
  
  def enrich(uri: String, strategy: AggregationStrategy) = {
    target.enrich(uri, strategy)
    this
  }
    
  def otherwise : SChoiceDefinition = 
    throw new Exception("otherwise is only supported in a choice block or after a when statement")
  
  def idempotentconsumer(expression: Exchange => Any) = new SIdempotentConsumerDefinition(target.idempotentConsumer(expression, null))
  
  def inOnly = new SProcessorDefinition(target.inOnly.asInstanceOf[RawProcessorDefinition])
  def inOut = new SProcessorDefinition(target.inOut.asInstanceOf[RawProcessorDefinition])
  
  def loop(expression: Exchange => Any) = new SLoopDefinition(target.loop(expression))
  
  def marshal(format: DataFormatDefinition) = {
    target.marshal(format)
    this
  }
  
  def multicast = new SMulticastDefinition(target.multicast)
  
  def process(function: Exchange => Unit) = {
    target.process(new ScalaProcessor(function))
    this
  }
 
  def throttle(frequency: Frequency) = new SThrottleDefinition(target.throttle(frequency.count).timePeriodMillis(frequency.period.milliseconds))
  
  def loadbalance = new SLoadBalanceDefinition(target.loadBalance)
  
  def delay(period: Period) = new SDelayDefinition(target.delay(period.milliseconds))
  
  def resequence(expression: Exchange => Any) = new SResequenceDefinition(target.resequence(expression))
  
  def rollback = new SProcessorDefinition(target.rollback.asInstanceOf[RawProcessorDefinition])
  
  def setbody(expression: Exchange => Any) = new SProcessorDefinition(target.setBody(expression).asInstanceOf[ProcessorDefinition[P] forSome {type P}])
  
  def setheader(name: String, expression: Exchange => Any) = new SProcessorDefinition(target.setHeader(name, expression).asInstanceOf[ProcessorDefinition[P] forSome {type P}])
  
  def unmarshal(format: DataFormatDefinition) = {
    target.unmarshal(format)
    this
  }
  
  def wiretap(uri: String) = new SProcessorDefinition(target.wireTap(uri).asInstanceOf[RawProcessorDefinition])
  
  def aggregate(expression: Exchange => Any) = new SAggregateDefinition(target.aggregate(expression))

}
