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

import org.apache.camel.model._
import org.apache.camel.processor.aggregate.AggregationStrategy
import org.apache.camel.scala.dsl.builder.RouteBuilder
import spi.Policy

import reflect.{ClassTag, classTag}
import java.util.Comparator

abstract class SAbstractDefinition[P <: ProcessorDefinition[_]] extends DSL with Wrapper[P] with Block {

  val target: P
  val unwrap = target
  implicit val builder: RouteBuilder

  implicit def predicateBuilder(predicate: Exchange => Any) = new ScalaPredicate(predicate)

  implicit def expressionBuilder(expression: Exchange => Any) = new ScalaExpression(expression)

  def apply(block: => Unit) = {
    builder.build(this, block)
    this
  }

  /**
   * Helper method to return this Scala type instead of creating another wrapper type for the processor
   */
  def wrap(block: => Unit): this.type = {
    block
    this
  }


  // EIPs
  //-----------------------------------------------------------------

  def aggregate(expression: Exchange => Any, strategy: AggregationStrategy) = SAggregateDefinition(target.aggregate(expression, strategy))
  def as[Target](toType: Class[Target], charset: String = null) = wrap(target.convertBodyTo(toType, charset))
  def attempt: STryDefinition = STryDefinition(target.doTry())

  def bean(bean: Any) = bean match {
    case cls: Class[_] => wrap(target.bean(cls))
    case ref: String => wrap(target.bean(ref))
    case obj: Any => wrap(target.bean(obj))
  }

  def choice = SChoiceDefinition(target.choice)
  def convertBodyTo[Target](toType: Class[Target], charset: String = null) = wrap(target.convertBodyTo(toType, charset))

  def delay(period: Period) = SDelayDefinition(target.delay(period.milliseconds))
  def dynamicRouter(expression: Exchange => Any) = wrap(target.dynamicRouter(expression))

  def enrich(uri: String, strategy: AggregationStrategy) = wrap(target.enrich(uri, strategy))
  def enrich(uri: String, strategy: AggregationStrategy, aggregateOnException: Boolean) = wrap(target.enrich(uri, strategy, aggregateOnException))

  def filter(predicate: Exchange => Any) = SFilterDefinition(target.filter(predicateBuilder(predicate)))

  def handle[E <: Throwable : ClassTag](block: => Unit) = SOnExceptionDefinition[E](target.onException(classTag[E].runtimeClass.asInstanceOf[Class[E]])).apply(block)

  def id(id : String) = wrap(target.id(id))
  def idempotentConsumer(expression: Exchange => Any) = SIdempotentConsumerDefinition(target.idempotentConsumer(expression, null))
  @Deprecated
  def inOnly = wrap(target.inOnly)
  @Deprecated
  def inOut = wrap(target.inOut)

  def loadbalance = SLoadBalanceDefinition(target.loadBalance)
  def log(message: String) = wrap(target.log(message))
  def log(level: LoggingLevel, message: String) = wrap(target.log(level, message))
  def log(level: LoggingLevel, logName: String, message: String) = wrap(target.log(level, logName, message))
  def log(level: LoggingLevel, logName: String, marker: String, message: String) = wrap(target.log(level, logName, marker, message))
  def loop(expression: Exchange => Any) = SLoopDefinition(target.loop(expression))

  def marshal(format: DataFormatDefinition) = wrap(target.marshal(format))
  def marshal(dataFormatRef: String) = wrap(target.marshal(dataFormatRef))
  def multicast = SMulticastDefinition(target.multicast)

  def onCompletion: SOnCompletionDefinition = {
    val completion = SOnCompletionDefinition(target.onCompletion)
    // let's end the block in the Java DSL, we have a better way of handling blocks here
    completion.target.end
    completion
  }
  def onCompletion(predicate: Exchange => Boolean) = onCompletion.when(predicate).asInstanceOf[SOnCompletionDefinition]
  def onCompletion(config: Config[SOnCompletionDefinition]) = {
    config.configure(onCompletion)
    onCompletion
  }
  def otherwise: SChoiceDefinition = throw new Exception("otherwise is only supported in a choice block or after a when statement")

  def pipeline = SPipelineDefinition(target.pipeline)
  def policy(policy: Policy) = SPolicyDefinition(target.policy(policy))
  def pollEnrich(uri: String, strategy: AggregationStrategy = null, timeout: Long = -1) =
    wrap(target.pollEnrich(uri, timeout, strategy))
  def pollEnrich(uri: String, strategy: AggregationStrategy, timeout: Long, aggregateOnException: Boolean) =
    wrap(target.pollEnrich(uri, timeout, strategy, aggregateOnException))
  def process(function: Exchange => Unit) = wrap(target.process(new ScalaProcessor(function)))
  def process(processor: Processor) = wrap(target.process(processor))

  def recipients(expression: Exchange => Any) = wrap(target.recipientList(expression))
  def resequence(expression: Exchange => Any) = SResequenceDefinition(target.resequence(expression))
  def removeHeader(name : String) = wrap(target.removeHeader(name))
  def removeHeaders(pattern: String) = wrap(target.removeHeaders(pattern))
  def removeHeaders(pattern: String, excludePatterns: String*) = wrap(target.removeHeaders(pattern, excludePatterns:_*))
  def removeProperty(name: String) = wrap(target.removeProperty(name))
  def removeProperties(pattern: String) = wrap(target.removeProperties(pattern))
  def removeProperties(pattern: String, excludePatterns: String*) = wrap(target.removeProperties(pattern, excludePatterns:_*))
  def rollback = wrap(target.rollback)
  def routeId(routeId: String) = wrap(target.routeId(routeId))
  def routeDescription(description: String) = wrap(target.routeDescription(description))
  @Deprecated
  def routingSlip(header: String) = wrap(target.routingSlip(header))
  @Deprecated
  def routingSlip(header: String, separator: String) = wrap(target.routingSlip(header, separator))
  def routingSlip(expression: Exchange => Any, separator: String) = wrap(target.routingSlip(expression, separator))
  def routingSlip(expression: Exchange => Any) = wrap(target.routingSlip(expression))

  def script(expression: Exchange => Any) = wrap(target.script(expression))
  def setBody(expression: Exchange => Any) = wrap(target.setBody(expression))
  def setFaultBody(expression: Exchange => Any) = wrap(target.setFaultBody(expression))
  def setHeader(name: String, expression: Exchange => Any) = wrap(target.setHeader(name, expression))
  def setExchangePattern(mep: ExchangePattern) = wrap(target.setExchangePattern(mep))
  def setProperty(name: String, expression: Exchange => Any) = wrap(target.setProperty(name, expression))
  def sort[T](expression: (Exchange) => Any, comparator: Comparator[T] = null) = wrap(target.sort(expression, comparator))
  def split(expression: Exchange => Any) = SSplitDefinition(target.split(expression))
  def startupOrder(startupOrder :Int) = wrap(target.startupOrder(startupOrder))
  def stop = wrap(target.stop)

  def threads = SThreadsDefinition(target.threads)
  def throttle(frequency: Frequency) = SThrottleDefinition(target.throttle(frequency.count).timePeriodMillis(frequency.period.milliseconds))
  def throwException(exception: Exception) = wrap(target.throwException(exception))
  def throwException(exceptionType: Class[_ <: Exception], message: String) = wrap(target.throwException(exceptionType, message))
  def transacted = STransactedDefinition(target.transacted)
  def transacted(ref: String) = STransactedDefinition(target.transacted(ref))
  def transform(expression: Exchange => Any) = wrap(target.transform(expression))

  def unmarshal(format: DataFormatDefinition) = wrap(target.unmarshal(format))
  def unmarshal(dataFormatRef: String) = wrap(target.unmarshal(dataFormatRef))

  def validate(expression: Exchange => Any) = SValidateDefinition(target.validate(predicateBuilder(expression)))

  def when(filter: Exchange => Any): DSL with Block = SChoiceDefinition(target.choice).when(filter)
  def wireTap(uri: String) = SWireTapDefinition(target.wireTap(uri))
  def wireTap(uri: String, expression: Exchange => Any) = SWireTapDefinition(target.wireTap(uri).newExchangeBody(expression))

  def -->(pattern: ExchangePattern, uri: String) = wrap(target.to(pattern, uri))
  def -->(uris: String*) = to(uris:_*)
  def to(pattern: ExchangePattern, uri: String) = wrap(target.to(pattern, uri))
  def to(uris: String*) = {
    uris.length match {
      case 1 => target.to(uris(0))
      case _ =>
        val multi = multicast
        uris.foreach(multi.to(_))
    }
    this
  }
  def toD(uri: String) = wrap(target.toD(uri))
  def toD(uri: String, ignoreInvalidEndpoint: Boolean) = wrap(target.toD(uri, ignoreInvalidEndpoint))

}
