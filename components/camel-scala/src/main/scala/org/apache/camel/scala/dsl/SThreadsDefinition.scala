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

import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import builder.RouteBuilder
import org.apache.camel.ThreadPoolRejectedPolicy
import org.apache.camel.model.ThreadsDefinition

/**
 * Scala enrichment for Camel's ThreadsDefinition
 */
case class SThreadsDefinition(override val target: ThreadsDefinition)(implicit val builder: RouteBuilder) extends SAbstractDefinition[ThreadsDefinition] {

  def poolSize(size: Int) = wrap(target.poolSize(size))
  
  def maxPoolSize(size: Int) = wrap(target.maxPoolSize(size))
  
  def keepAliveTime(size: Long) = wrap(target.keepAliveTime(size))
  
  def timeUnit(timeUnit: TimeUnit) = wrap(target.timeUnit(timeUnit))
  
  def maxQueueSize(size: Int) = wrap(target.maxQueueSize(size))
  
  def rejectedPolicy(policy: ThreadPoolRejectedPolicy) = wrap(target.rejectedPolicy(policy))
  
  def threadName(name: String) = wrap(target.threadName(name))
  
  def callerRunsWhenRejected(callerRunsWhenRejected: Boolean) = wrap(target.callerRunsWhenRejected(callerRunsWhenRejected))

  def executorService(executorService: ExecutorService) = wrap(target.setExecutorService(executorService))
  
  def executorServiceRef(ref: String) = wrap(target.setExecutorServiceRef(ref))

}
  
