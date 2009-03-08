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

import org.apache.camel.model.dataformat.DataFormatDefinition

/**
 * Defines the 'keywords' in our Scala DSL
 */
trait DSL {
  
  def aggregate(expression: Exchange => Any) : SAggregateDefinition
  def as[Target](toType: Class[Target]) : DSL
  def attempt : STryDefinition
  def bean(bean: Any) : DSL
  def choice : SChoiceDefinition
  def delay(delay: Period) : SDelayDefinition
  def idempotentconsumer(expression: Exchange => Any): SIdempotentConsumerDefinition
  def inOnly(): SProcessorDefinition
  def inOut(): SProcessorDefinition
  def loadbalance : SLoadBalanceDefinition
  def loop(expression: Exchange => Any) : SLoopDefinition
  def marshal(format : DataFormatDefinition) : DSL
  def multicast : SMulticastDefinition
  def otherwise : DSL
  def process(function: Exchange => Unit) : DSL
  def recipients(expression: Exchange => Any) : DSL
  def resequence(expression: Exchange => Any) : SResequenceDefinition
  def setbody(expression: Exchange => Any) : DSL
  def setheader(header: String, expression: Exchange => Any) : DSL
  def split(expression: Exchange => Any) : SSplitDefinition
  def thread(number: Int) : SThreadDefinition
  def throttle(frequency: Frequency) : SThrottleDefinition
  def to(uris: String*) : DSL
  def unmarshal(format: DataFormatDefinition) : DSL
  def when(filter: Exchange => Boolean) : SChoiceDefinition
  def -->(uris: String*) : DSL

}
