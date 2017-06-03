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

import processor.aggregate.AggregationStrategy
import org.apache.camel.builder.ExchangeBuilder.anExchange
import org.apache.camel.scala.Preamble.FnAggregationStrategy.exchangeWrappingAggregator

/**
 * Trait containing common implicit conversion definitions.
 */
trait Preamble {

  implicit def exchangeWrapper(exchange: Exchange) = new RichExchange(exchange)
  implicit def enrichInt(int: Int) = new RichInt(int)
  implicit def int2Period(value: Int) = new SimplePeriod(value)

  implicit def enrichMessage(msg: Message) = new RichMessage(msg)

  implicit def enrichFnAny(f: Exchange => Any) = new ScalaPredicate(f)
  implicit def enrichAggr(f: (Exchange, Exchange) => Exchange) = new FnAggregationStrategy(f)
  implicit def enrichWrappingAggregator[T <: Any](f: (Exchange, Exchange) => T) = exchangeWrappingAggregator(f)

  /**
   * process { in(classOf[String]) { _+"11" } .toIn }
   * process { in(classOf[Int]) { 11+ } .toOut }
   *
   * process(in(classOf[Event]) {
   *   case event: LoginEvent => doSession(event)
   *   case event: LogoutEvent => removeSession(event)
   * })
   */
  def in[T](clazz: Class[T]) = new BodyExtractor[T](_.getIn.getBody(clazz))

  /**
   * process { out(classOf[String]) { (s: String) => s+"11" } .toIn }
   * process { out(classOf[Int]) { _+11 } .toOut }
   */
  def out[T](clazz: Class[T]) = new BodyExtractor[T](_.getOut.getBody(clazz))

  /**
   * filter { in(classOf[Int]) { _ % 2 == 0 } }
   * filter { out(classOf[String]) { (s: String) => s.startsWith("aa") } }
   */
  implicit def wrapperFilter(w: WrappedProcessor) = w.predicate

  trait WrappedProcessor extends Processor {
    implicit def enrichFnUnit(f: Exchange => Unit) = new ScalaProcessor(f)

    def run(exchange: Exchange): Option[Any]

    def toIn: Processor =
      (exchange: Exchange) =>
        run(exchange) foreach {
          case () => throw new RuntimeTransformException("Cannot save Unit result into message")
          case v => exchange.in = v
        }

    def toOut: Processor =
      (exchange: Exchange) =>
        run(exchange) foreach {
          case () => throw new RuntimeTransformException("Cannot save Unit result into message")
          case v => exchange.out = v
        }

    def predicate: Predicate =
      (exchange: Exchange) =>
        run(exchange) map {
          case () => throw new RuntimeTransformException("Unit result cannot be used in Predicate")
          case v => v
        } getOrElse false

    override def process(exchange: Exchange) {
      run(exchange) foreach {
        case () =>
        case v => exchange.in = v
      }
    }
  }

  class BodyExtractor[T](val get: (Exchange) => T) {
    def by(f: (T) => Any): WrappedProcessor = new FnProcessor(f)

    /**
     * process { in(classOf[Event]) collect { case event: LoginEvent => doSession(event) } }
     * filter { in(classOf[Event]) collect { case event: LoginEvent => event.isAdmin } }
     */
    def collect(pf: PartialFunction[T,Any]): WrappedProcessor = new PfProcessor(pf)

    def apply(f: (T) => Any): WrappedProcessor = by(f)

    /**
     * Wrapper for function processor / predicate
     */
    class FnProcessor(val f: (T) => Any) extends WrappedProcessor {
      override def run(exchange: Exchange): Option[Any] = Some(f(get(exchange)))
    }

    /**
     * Wrapper for PartialFunction processor / predicate
     */
    class PfProcessor(val pf: PartialFunction[T,Any]) extends WrappedProcessor {
      override def run(exchange: Exchange): Option[Any] = PartialFunction.condOpt(get(exchange))(pf)
    }
  }

  /**
   * Wrapper for (Exchange, Exchange) => Exchange that acts as AggregationStrategy
   */
  class FnAggregationStrategy(aggregator: (Exchange, Exchange) => Exchange) extends AggregationStrategy {
    override def aggregate(original: Exchange, resource: Exchange): Exchange = aggregator(original, resource)
  }

  object FnAggregationStrategy {

    def exchangeWrappingAggregator[T <: Any](aggregator: (Exchange, Exchange) => T) = {
      val wrappingAggregator =
        (oldExch: Exchange, newExch: Exchange) => newExch match {
          case null => oldExch
          case _ => anExchange(newExch.getContext).withBody(aggregator(oldExch, newExch)).build
      }
      new FnAggregationStrategy(wrappingAggregator)
    }

  }

}

/**
 * Object globally exposing [[org.apache.camel.scala.Preamble]] trait. Useful to import explicit conversions
 * without extending trait. For example:
 *
 * `import org.apache.camel.scala.Preamble._`
 *
 */
object Preamble extends Preamble