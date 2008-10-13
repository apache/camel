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

import org.apache.camel.model.ProcessorType
import org.apache.camel.model.FilterType
import org.apache.camel.model.ChoiceType
import org.apache.camel.model.IdempotentConsumerType

import org.apache.camel.scala.dsl.builder.RouteBuilder

abstract class SAbstractType extends DSL {
  
  val target : ProcessorType[T] forSome {type T}
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
  
  def when(filter: Exchange => Boolean) : SChoiceType =
    new SChoiceType(target.choice).when(filter)
    
  def as[Target](toType: Class[Target]) = {
    target.convertBodyTo(toType)
    new SProcessorType(target.asInstanceOf[ProcessorType[P] forSome {type P}])
  }
  
  def attempt : STryType = new STryType(target.tryBlock)
  
  def splitter(expression: Exchange => Any) = 
    new SSplitterType(target.splitter(expression))
    
  def recipients(expression: Exchange => Any) = 
    new SProcessorType(target.recipientList(expression).asInstanceOf[ProcessorType[P] forSome {type P}])

  def apply(block: => Unit) = {
    builder.build(this, block)
    this
  }

  def bean(bean: Any) = bean match {
    case cls: Class[_] => new SProcessorType(target.bean(cls).asInstanceOf[ProcessorType[P] forSome {type P}])
    case ref: String => new SProcessorType(target.beanRef(ref).asInstanceOf[ProcessorType[P] forSome {type P}])
    case obj: Any => new SProcessorType(target.bean(obj).asInstanceOf[ProcessorType[P] forSome {type P}])
  }
  
  def choice = new SChoiceType(target.choice)
    
  def otherwise : SChoiceType = 
    throw new Exception("otherwise is only supported in a choice block or after a when statement")
  
  def idempotentconsumer(expression: Exchange => Any) = new SIdempotentConsumerType(target.idempotentConsumer(expression, null))
  
  def multicast = new SMulticastType(target.multicast)
  
  def process(function: Exchange => Unit) = {
    target.process(new ScalaProcessor(function))
    this
  }
  
  def throttle(frequency: Frequency) = new SThrottlerType(target.throttler(frequency.count).timePeriodMillis(frequency.period.milliseconds))
  
  def loadbalance = new SLoadBalanceType(target.loadBalance)
  
  def delay(period: Period) = new SDelayerType(target.delayer(period.milliseconds))
  
  def resequence(expression: Exchange => Any) = new SResequencerType(target.resequencer(expression))
  
  def setbody(expression: Exchange => Any) = new SProcessorType(target.setBody(expression).asInstanceOf[ProcessorType[P] forSome {type P}])
  
  def setheader(name: String, expression: Exchange => Any) = new SProcessorType(target.setHeader(name, expression).asInstanceOf[ProcessorType[P] forSome {type P}])
  
  def aggregate(expression: Exchange => Any) = new SAggregatorType(target.aggregator(expression))

}
