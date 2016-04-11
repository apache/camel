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
package org.apache.camel
package scala
package dsl 

import org.apache.camel.model.DataFormatDefinition
import reflect.ClassTag
import java.util.Comparator
import org.apache.camel.processor.aggregate.AggregationStrategy

import org.apache.camel.spi.Policy

/**
 * Defines the 'keywords' in our Scala DSL
 */
trait DSL {
  
  def aggregate(expression: Exchange => Any, strategy: AggregationStrategy) : SAggregateDefinition
  def as[Target](toType: Class[Target], charset: String = null) : DSL
  def attempt : STryDefinition

  def bean(bean: Any) : DSL

  def choice : SChoiceDefinition
  def convertBodyTo[Target](toType: Class[Target], charset: String = null) : DSL

  def delay(delay: Period) : SDelayDefinition
  def dynamicRouter(expression: Exchange => Any) : DSL

  def enrich(uri:String, strategy: AggregationStrategy) : DSL
  def enrich(uri:String, strategy: AggregationStrategy, aggregateOnException: Boolean) : DSL

  def filter(predicate: Exchange => Any) : SFilterDefinition

  def handle[E <: Throwable : ClassTag](block: => Unit) : SOnExceptionDefinition[E]

  def id(id : String): DSL
  def idempotentConsumer(expression: Exchange => Any): SIdempotentConsumerDefinition
  def inOnly: DSL with Block
  def inOut: DSL with Block

  def loadbalance : SLoadBalanceDefinition
  def log(message: String) : DSL
  def log(level: LoggingLevel, message: String) : DSL
  def log(level: LoggingLevel, logName: String, message: String) : DSL
  def log(level: LoggingLevel, logName: String, marker: String, message: String) : DSL
  def loop(expression: Exchange => Any) : SLoopDefinition

  def marshal(format : DataFormatDefinition) : DSL
  def marshal(dataFormatRef: String) : DSL
  def multicast : SMulticastDefinition

  def onCompletion : SOnCompletionDefinition
  def onCompletion(predicate: Exchange => Boolean) : SOnCompletionDefinition
  def onCompletion(config: Config[SOnCompletionDefinition]) : SOnCompletionDefinition
  def otherwise : DSL with Block

  def pipeline : SPipelineDefinition
  def policy(policy: Policy) : DSL
  def pollEnrich(uri: String, strategy: AggregationStrategy = null, timeout: Long = 0) : DSL
  def pollEnrich(uri: String, strategy: AggregationStrategy, timeout: Long, aggregateOnException: Boolean) : DSL
  def process(function: Exchange => Unit) : DSL
  def process(processor: Processor) : DSL

  def recipients(expression: Exchange => Any) : DSL
  def removeHeader(name: String): DSL
  def removeHeaders(pattern: String): DSL
  def removeHeaders(pattern: String, excludePatterns: String*): DSL
  def resequence(expression: Exchange => Any) : SResequenceDefinition
  def rollback : DSL
  def routeId(id: String) : DSL
  def routeDescription(description: String): DSL
  def routingSlip(header: String) : DSL
  def routingSlip(header: String, separator: String) : DSL
  def routingSlip(expression: Exchange => Any) : DSL

  def script(expression: Exchange => Any) : DSL
  def setBody(expression: Exchange => Any) : DSL
  def setFaultBody(expression: Exchange => Any) : DSL
  def setHeader(header: String, expression: Exchange => Any) : DSL
  def setExchangePattern(mep: ExchangePattern) : DSL
  def setProperty(header: String, expression: Exchange => Any) : DSL
  def sort[T](expression: Exchange => Any, comparator: Comparator[T] = null) : DSL
  def split(expression: Exchange => Any) : SSplitDefinition
  def startupOrder(startupOrder :Int) : DSL
  def stop : DSL

  def threads : SThreadsDefinition
  def throttle(frequency: Frequency) : SThrottleDefinition
  def throwException(exception: Exception) : DSL
  def throwException(exceptionType: Class[_ <: Exception], message: String) : DSL
  def to(uris: String*) : DSL
  def toD(uri: String) : DSL
  def toD(uri: String, ignoreInvalidEndpoint: Boolean) : DSL
  def transacted : DSL
  def transacted(ref: String) : DSL
  def transform(expression: Exchange => Any) : DSL

  def unmarshal(format: DataFormatDefinition) : DSL
  def unmarshal(dataFormatRef: String) : DSL

  def validate(expression: Exchange => Any) : DSL

  def when(filter: Exchange => Any) : DSL with Block
  def wireTap(uri: String) : DSL
  def wireTap(uri: String, expression: Exchange => Any) : DSL
  
  def -->(uris: String*) : DSL
}
