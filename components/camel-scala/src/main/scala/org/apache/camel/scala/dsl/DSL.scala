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

/**
 * Defines the 'keywords' in our Scala DSL
 */
trait DSL {
  
  def attempt : STryType
  def bean(bean: Any) : DSL
  def choice : SChoiceType
  def -->(uris: String*) : DSL
  def to(uris: String*) : DSL
  def when(filter: Exchange => Boolean) : SChoiceType
  def as[Target](toType: Class[Target]) : DSL
  def recipients(expression: Exchange => Any) : DSL
  def splitter(expression: Exchange => Any) : SSplitterType
  def otherwise : DSL
  def multicast : SMulticastType
  def process(function: Exchange => Unit) : DSL
  def throttle(frequency: Frequency) : SThrottlerType
  def loadbalance : SLoadBalanceType
  def delay(delay: Period) : SDelayerType
  def resequence(expression: Exchange => Any) : SResequencerType
  def setbody(expression: Exchange => Any) : DSL
  def setheader(header: String, expression: Exchange => Any) : DSL
  def aggregate(expression: Exchange => Any) : SAggregatorType
  def idempotentconsumer(expression: Exchange => Any): SIdempotentConsumerType

}
