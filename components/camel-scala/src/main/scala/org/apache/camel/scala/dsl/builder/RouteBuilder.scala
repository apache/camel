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
package dsl.builder

import org.apache.camel.model._
import org.apache.camel.builder.{LoggingErrorHandlerBuilder, DeadLetterChannelBuilder, ErrorHandlerBuilder}

import org.apache.camel.spi.Policy
import org.apache.camel.processor.aggregate.AggregationStrategy
import collection.mutable.Stack
import reflect.{ClassTag, classTag}

import org.apache.camel.scala.dsl._

import org.apache.camel.scala.dsl.languages.Languages
import java.util.Comparator
import org.slf4j.{Logger, LoggerFactory}

/**
 * Scala RouteBuilder implementation
 *
 * @deprecated use ScalaRouteBuilder
 */
@Deprecated
class RouteBuilder extends Preamble with DSL with RoutesBuilder with Languages with Functions {

  val builder = new org.apache.camel.builder.RouteBuilder {
    override def configure() {
      onJavaBuilder(this)
    }

    override def checkInitialized(): Unit = {
      super.checkInitialized()

      // must un-prepare the routes as they may have been eager pre-pared before scala has properly build the entire routes
      // Camel will prepare the routes before the routes is started so it happens eventually at the right time
      val it = getRouteCollection.getRoutes.iterator()
      while (it.hasNext) {
        var route = it.next()
        route.markUnprepared()
      }
    }
  }

  val stack = new Stack[DSL]

  val serialization = new org.apache.camel.model.dataformat.SerializationDataFormat

  val failureOnly = new Config[SOnCompletionDefinition] {
    def configure(target: SOnCompletionDefinition) { target.onFailureOnly }
  }

  val completeOnly = new Config[SOnCompletionDefinition] {
    def configure(target: SOnCompletionDefinition) { target.onCompleteOnly }
  }

  /**
   * Callback method to allow people to interact with the Java DSL builder directly
   */
  def onJavaBuilder(builder: org.apache.camel.builder.RouteBuilder) {}

  implicit def stringToRoute(target: String) : SRouteDefinition = new SRouteDefinition(builder.from(target), this)
  implicit def unwrap[W](wrapper: Wrapper[W]) = wrapper.unwrap
  implicit def constantToExpression(value: Any) : (Exchange => Any) = (exchange: Exchange) => value 

  def print() = {
    println(builder)
    this
  }

  def build(context: DSL, block: => Unit) {
    stack.push(context)
    block
    stack.pop()
  }

  def from(uri: String) = new SRouteDefinition(builder.from(uri), this)

  /*
   * This is done a bit differently - the implicit manifest parameter forced us to define the block in the same
   * method definition.
   */
  def handle[E <: Throwable : ClassTag](block: => Unit) = {
    stack.size match {
      case 0 => SOnExceptionDefinition[E](builder.onException(classTag[E].runtimeClass.asInstanceOf[Class[Throwable]]))(this).apply(block)
      case _ => stack.top.handle[E](block)
    }
  }

  def getContext = builder.getContext

  // implementing the Routes interface to allow RouteBuilder to be discovered by Spring
  def addRoutesToCamelContext(context: CamelContext) { builder.addRoutesToCamelContext(context) }

  // EIPs
  //-----------------------------------------------------------------
  def aggregate(expression: Exchange => Any, strategy: AggregationStrategy) = stack.top.aggregate(expression, strategy)
  def as[Target](toType: Class[Target], charset: String = null) = stack.top.as(toType, charset)
  def attempt = stack.top.attempt

  def bean(bean: Any) = stack.top.bean(bean)

  def convertBodyTo[Target](toType: Class[Target], charset: String = null) = stack.top.convertBodyTo(toType, charset)
  def choice = stack.top.choice

  def delay(delay: Period) = stack.top.delay(delay)
  def dynamicRouter(expression: Exchange => Any) = stack.top.dynamicRouter(expression)

  def enrich(uri: String, strategy: AggregationStrategy) = stack.top.enrich(uri, strategy)
  def enrich(uri: String, strategy: AggregationStrategy, aggregateOnException: Boolean) = stack.top.enrich(uri, strategy, aggregateOnException)
  def errorHandler(error: ErrorHandlerBuilder) { builder.setErrorHandlerBuilder(error) }
  def deadLetterChannel(uri: String) = {
    val dlc = new DeadLetterChannelBuilder
    dlc.setDeadLetterUri(uri)
    dlc
  }
  def loggingErrorHandler : LoggingErrorHandlerBuilder = new LoggingErrorHandlerBuilder
  def loggingErrorHandler(log: String) : LoggingErrorHandlerBuilder = loggingErrorHandler(LoggerFactory.getLogger(log))
  def loggingErrorHandler(log: Logger) : LoggingErrorHandlerBuilder = new LoggingErrorHandlerBuilder(log)
  def loggingErrorHandler(log: Logger, level: LoggingLevel) : LoggingErrorHandlerBuilder = new LoggingErrorHandlerBuilder(log, level)
  def defaultErrorHandler = builder.defaultErrorHandler

  def filter(predicate: Exchange => Any) = stack.top.filter(predicate)

  def id(id : String) = stack.top.id(id)
  def idempotentConsumer(expression: Exchange => Any) = stack.top.idempotentConsumer(expression)
  def inOnly = stack.top.inOnly
  def inOut = stack.top.inOut
  def interceptFrom(expression: Exchange => Boolean) = {
    val interceptFrom = builder.interceptFrom
    interceptFrom.when(new ScalaPredicate(expression))
    new SInterceptFromDefinition(interceptFrom)(this)
  }
  def interceptFrom = new SInterceptFromDefinition(builder.interceptFrom)(this)
  def interceptFrom(uri: String) = new SInterceptFromDefinition(builder.interceptFrom(uri))(this)
  def interceptSendTo(uri: String) = {
    val intercept = builder.interceptSendToEndpoint(uri)
    new SInterceptSendToEndpointDefinition(intercept)(this)
  }
  def intercept = new SInterceptDefinition(builder.intercept)(this)

  def loadbalance = stack.top.loadbalance
  def log(message: String) = stack.top.log(message)
  def log(level: LoggingLevel, message: String) = stack.top.log(level, message)
  def log(level: LoggingLevel, logName: String, message: String) = stack.top.log(level, logName, message)
  def log(level: LoggingLevel, logName: String, marker: String, message: String) = stack.top.log(level, logName, marker, message)
  def loop(expression: Exchange => Any) = stack.top.loop(expression)

  def marshal(format: DataFormatDefinition) = stack.top.marshal(format)
  def marshal(dataFormatRef: String) = stack.top.marshal(dataFormatRef)
  def multicast = stack.top.multicast

  def onCompletion = {
    stack.size match {
      case 0 => SOnCompletionDefinition(builder.onCompletion)(this)
      case _ => stack.top.onCompletion
    }
  }
  def onCompletion(predicate: Exchange => Boolean) = stack.top.onCompletion(predicate)
  def onCompletion(config: Config[SOnCompletionDefinition]) = stack.top.onCompletion(config)
  def otherwise = stack.top.otherwise

  def pipeline = stack.top.pipeline
  def pollEnrich(uri: String, strategy: AggregationStrategy = null, timeout: Long = 0) = stack.top.pollEnrich(uri, strategy, timeout)
  def pollEnrich(uri: String, strategy: AggregationStrategy, timeout: Long, aggregateOnException: Boolean) = stack.top.pollEnrich(uri, strategy, timeout, aggregateOnException)
  def policy(policy: Policy) = stack.top.policy(policy)
  def process(function: Exchange => Unit) = stack.top.process(function)
  def process(processor: Processor) = stack.top.process(processor)

  def recipients(expression: Exchange => Any) = stack.top.recipients(expression)
  def removeHeader(name: String) = stack.top.removeHeader(name)
  def removeHeaders(pattern: String) = stack.top.removeHeaders(pattern)
  def removeHeaders(pattern: String, excludePatterns: String*) = stack.top.removeHeaders(pattern, excludePatterns:_*)
  def resequence(expression: Exchange => Any) = stack.top.resequence(expression)
  def rollback = stack.top.rollback
  def routeId(id: String) = stack.top.routeId(id)
  def routeDescription(description: String) = stack.top.routeDescription(description)
  def routingSlip(header: String) = stack.top.routingSlip(header)
  def routingSlip(header: String, separator: String) = stack.top.routingSlip(header, separator)
  def routingSlip(expression: Exchange => Any) = stack.top.routingSlip(expression)

  def script(expression: Exchange => Any) = stack.top.script(expression)
  def setBody(expression : Exchange => Any) = stack.top.setBody(expression)
  def setFaultBody(expression: Exchange => Any) = stack.top.setFaultBody(expression)
  def setHeader(name: String, expression: Exchange => Any) = stack.top.setHeader(name, expression)
  def setExchangePattern(mep: ExchangePattern) = stack.top.setExchangePattern(mep)
  def setProperty(name: String, expression: Exchange => Any) = stack.top.setProperty(name, expression)
  def sort[T](expression: (Exchange) => Any, comparator: Comparator[T] = null) = stack.top.sort(expression, comparator)
  def split(expression: Exchange => Any) = stack.top.split(expression)
  def startupOrder(startupOrder :Int) = stack.top.startupOrder(startupOrder)
  def stop = stack.top.stop

  def threads = stack.top.threads
  def throttle(frequency: Frequency) = stack.top.throttle(frequency)
  def throwException(exception: Exception) = stack.top.throwException(exception)
  def throwException(exceptionType: Class[_ <: Exception], message: String) = stack.top.throwException(exceptionType, message)
  def transacted = stack.top.transacted
  def transacted(uri: String) = stack.top.transacted(uri)
  def transform(expression: Exchange => Any) = stack.top.transform(expression)

  def unmarshal(format: DataFormatDefinition) = stack.top.unmarshal(format)
  def unmarshal(dataFormatRef: String) = stack.top.unmarshal(dataFormatRef)

  def validate(expression: (Exchange) => Any) = stack.top.validate(expression)

  def when(filter: Exchange => Any) = stack.top.when(filter)
  def wireTap(uri: String) = stack.top.wireTap(uri)
  def wireTap(uri: String, expression: Exchange => Any) = stack.top.wireTap(uri, expression)

  def to(uris: String*) = stack.top.to(uris: _*)
  def toD(uri: String) = stack.top.toD(uri)
  def toD(uri: String, ignoreInvalidEndpoint: Boolean) = stack.top.toD(uri, ignoreInvalidEndpoint)
  def -->(uris: String*) = stack.top.to(uris: _*)

}
