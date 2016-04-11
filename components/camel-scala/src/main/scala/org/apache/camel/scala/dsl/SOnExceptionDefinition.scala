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
package scala.dsl

import org.apache.camel.model.OnExceptionDefinition
import org.apache.camel.scala.ScalaProcessor
import org.apache.camel.scala.dsl.builder.RouteBuilder

/**
 * Scala enrichment for Camel's OnExceptionDefinition
 */
case class SOnExceptionDefinition[E <: Throwable](override val target: OnExceptionDefinition)(implicit val builder: RouteBuilder) extends SAbstractDefinition[OnExceptionDefinition] {

  override def apply(block: => Unit) = super.apply(block).asInstanceOf[SOnExceptionDefinition[E]]

  def handled = wrap(target.handled(true))
  def handled(predicate: Exchange => Any) = wrap(target.handled(predicateBuilder(predicate)))

  def continued = wrap(target.continued(true))
  def continued(predicate: Exchange => Any) = wrap(target.continued(predicateBuilder(predicate)))

  def maximumRedeliveries(count: Int) = wrap(target.maximumRedeliveries(count))

  def onRedelivery(processor: Exchange => Unit) = wrap(target.onRedelivery(new ScalaProcessor(processor)))

  def onWhen(when: Exchange => Any) = wrap(target.onWhen(predicateBuilder(when)))
  
  def backOffMultiplier(backOffMultiplier: Double) = wrap(target.backOffMultiplier(backOffMultiplier))
  
  def collisionAvoidanceFactor(collisionAvoidanceFactor: Double) = wrap(target.collisionAvoidanceFactor(collisionAvoidanceFactor))
  
  def collisionAvoidancePercent(collisionAvoidancePercent: Double) = wrap(target.collisionAvoidancePercent(collisionAvoidancePercent))
  
  def redeliveryDelay(redeliveryDelay: Long) = wrap(target.redeliveryDelay(redeliveryDelay))
  
  def asyncDelayedRedelivery = wrap(target.asyncDelayedRedelivery)
  
  def retriesExhaustedLogLevel(logLevel: LoggingLevel) = wrap(target.retriesExhaustedLogLevel(logLevel))
  
  def retryAttemptedLogLevel(logLevel: LoggingLevel) = wrap(target.retryAttemptedLogLevel(logLevel))
  
  def logHandled(logHandled: Boolean) = wrap(target.logHandled(logHandled))
  
  def logContinued(logContinued: Boolean) = wrap(target.logContinued(logContinued))
  
  def logRetryAttempted(logRetryAttempted: Boolean) = wrap(target.logRetryAttempted(logRetryAttempted))
  
  def logExhausted(logExhausted: Boolean) = wrap(target.logExhausted(logExhausted))
  
  def useCollisionAvoidance = wrap(target.useCollisionAvoidance)
  
  def useExponentialBackOff = wrap(target.useExponentialBackOff)
  
  def maximumRedeliveryDelay(maximumRedeliveryDelay: Long) = wrap(target.maximumRedeliveryDelay(maximumRedeliveryDelay))
  
  def logStackTrace(log: Boolean) = wrap(target.logStackTrace(log))
  
  def logRetryStackTrace(log: Boolean) = wrap(target.logRetryStackTrace(log))
  
  def redeliveryPolicyRef(ref: String) = wrap(target.redeliveryPolicyRef(ref))
  
  def delayPattern(pattern: String) = wrap(target.delayPattern(pattern))

  def retryWhile(retryWhile: Exchange => Any) = wrap(target.retryWhile(predicateBuilder(retryWhile)))

  def useOriginalMessage = wrap(target.useOriginalMessage)
}
