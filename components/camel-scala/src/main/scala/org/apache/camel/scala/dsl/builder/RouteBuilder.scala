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

import org.apache.camel.model.{ChoiceDefinition, ProcessorDefinition}
import org.apache.camel.model.DataFormatDefinition
import org.apache.camel.{Exchange, RoutesBuilder}
import org.apache.camel.builder.{DeadLetterChannelBuilder, ErrorHandlerBuilder}

import org.apache.camel.spi.Policy
import org.apache.camel.processor.aggregate.AggregationStrategy
import collection.mutable.Stack
import _root_.scala.reflect.Manifest

import org.apache.camel.scala.dsl._

import org.apache.camel.scala.dsl.languages.Languages
import java.lang.String
import java.util.Comparator

/**
 * Scala RouteBuilder implementation
 */
class RouteBuilder extends Preamble with DSL with RoutesBuilder with Languages with Functions {

  val builder = new org.apache.camel.builder.RouteBuilder {
    override def configure() =  {
      onJavaBuilder(this)
    }
  }

  val stack = new Stack[DSL];

  /**
   * Callback method to allow people to interact with the Java DSL builder directly
   */
  def onJavaBuilder(builder: org.apache.camel.builder.RouteBuilder) = {}

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
   * This is done a bit differently - the implicit manifest parameter forces us to define the block in the same
   * method definition
   */
  def handle[E](block: => Unit)(implicit manifest: Manifest[E]) = {
    stack.size match {
      case 0 => SOnExceptionDefinition(builder.onException(manifest.erasure))(this).apply(block)
      case _ => stack.top.handle[E](block)
    }
  }

  def attempt = stack.top.attempt
  def bean(bean: Any) = stack.top.bean(bean)
  def choice = stack.top.choice
  def -->(uris: String*) = stack.top.to(uris: _*)
  def to(uris: String*) = stack.top.to(uris: _*)
  
  def when(filter: Exchange => Any) = stack.top.when(filter)
  def as[Target](toType: Class[Target]) = stack.top.as(toType)

  def recipients(expression: Exchange => Any) = stack.top.recipients(expression)
  def filter(predicate: Exchange => Any) = stack.top.filter(predicate)
  def idempotentconsumer(expression: Exchange => Any) = stack.top.idempotentconsumer(expression)
  def inOnly = stack.top.inOnly
  def inOut = stack.top.inOut

  def log(message: String) = stack.top.log(message)
  def log(level: LoggingLevel, message: String) = stack.top.log(level, message)
  def log(level: LoggingLevel, logName: String, message: String) = stack.top.log(level, logName, message)

  def loop(expression: Exchange => Any) = stack.top.loop(expression)
  def split(expression: Exchange => Any) = stack.top.split(expression)
  def otherwise = stack.top.otherwise
  def marshal(format: DataFormatDefinition) = stack.top.marshal(format)
  def multicast = stack.top.multicast

  def throttle(frequency: Frequency) = stack.top.throttle(frequency)
  def loadbalance = stack.top.loadbalance
  def delay(delay: Period) = stack.top.delay(delay)
  def enrich(uri: String, strategy: AggregationStrategy) = stack.top.enrich(uri, strategy)
  def onCompletion = {
    stack.size match {
      case 0 => SOnCompletionDefinition(builder.onCompletion)(this)
      case _ => stack.top.onCompletion;
    }
  }
  def onCompletion(predicate: Exchange => Boolean) = stack.top.onCompletion(predicate)
  def onCompletion(config: Config[SOnCompletionDefinition]) = stack.top.onCompletion(config)
  def pipeline = stack.top.pipeline
  def pollEnrich(uri: String, strategy: AggregationStrategy = null, timeout: Long = 0) = stack.top.pollEnrich(uri, strategy, timeout)
  def policy(policy: Policy) = stack.top.policy(policy)
  def process(function: Exchange => Unit) = stack.top.process(function)
  def process(processor: Processor) = stack.top.process(processor)
  def resequence(expression: Exchange => Any) = stack.top.resequence(expression)
  def rollback = stack.top.rollback
  def routingSlip(header: String) = stack.top.routingSlip(header)
  def routingSlip(header: String, separator: String) = stack.top.routingSlip(header, separator)
  def routingSlip(expression: Exchange => Any) = stack.top.routingSlip(expression)
  def dynamicRouter(expression: Exchange => Any) = stack.top.dynamicRouter(expression)
  def setbody(expression : Exchange => Any) = stack.top.setbody(expression)
  def setfaultbody(expression: Exchange => Any) = stack.top.setfaultbody(expression)
  def setheader(name: String, expression: Exchange => Any) = stack.top.setheader(name, expression)
  def sort[T](expression: (Exchange) => Any, comparator: Comparator[T] = null) = stack.top.sort(expression, comparator)
  def stop = stack.top.stop
  def threads = stack.top.threads
  def throwException(exception: Exception) = stack.top.throwException(exception)
  def transacted = stack.top.transacted
  def transacted(uri: String) = stack.top.transacted
  def transform(expression: Exchange => Any) = stack.top.transform(expression)
  def unmarshal(format: DataFormatDefinition) = stack.top.unmarshal(format)
  def validate(expression: (Exchange) => Any) = stack.top.validate(expression)
  def wiretap(uri: String) = stack.top.wiretap(uri)
  def wiretap(uri: String, expression: Exchange => Any) = stack.top.wiretap(uri, expression)
  def aggregate(expression: Exchange => Any, strategy: AggregationStrategy) = stack.top.aggregate(expression, strategy)

  // delegate to Java builder
  def errorHandler(error: ErrorHandlerBuilder) = builder.setErrorHandlerBuilder(error) 
  def deadLetterChannel(uri: String) = {
    val dlc = new DeadLetterChannelBuilder
    dlc.setDeadLetterUri(uri)
    dlc
  }
  def defaultErrorHandler = builder.defaultErrorHandler
  def getContext = builder.getContext

  // interceptor methods
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

  // implementing the Routes interface to allow RouteBuilder to be discovered by Spring
  def addRoutesToCamelContext(context: CamelContext) = builder.addRoutesToCamelContext(context)
  
  val serialization = new org.apache.camel.model.dataformat.SerializationDataFormat

  val failureOnly = new Config[SOnCompletionDefinition] {
    def configure(target: SOnCompletionDefinition) = target.onFailureOnly()
  }

  val completeOnly = new Config[SOnCompletionDefinition] {
    def configure(target: SOnCompletionDefinition) = target.onCompleteOnly()
  }

}
