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
import org.apache.camel.model.ThrottleDefinition
import org.apache.camel.scala.dsl.builder.RouteBuilder

/**
 * Scala enrichment for Camel's ThrottleDefinition
 */
case class SThrottleDefinition(override val target: ThrottleDefinition)(implicit val builder: RouteBuilder) extends SAbstractDefinition[ThrottleDefinition] {
 
  def executorService(executorService: ExecutorService) = wrap(target.setExecutorService(executorService))
  
  def executorServiceRef(ref: String) = wrap(target.setExecutorServiceRef(ref))
  
  def timePeriodMillis(timePeriodMillis: Long) = wrap(target.timePeriodMillis(timePeriodMillis))
  
  def maximumRequestsPerPeriod(maximumRequestsPerPeriod: Long) = wrap(target.maximumRequestsPerPeriod(maximumRequestsPerPeriod))
  
  def callerRunsWhenRejected(callerRunsWhenRejected: Boolean) = wrap(target.callerRunsWhenRejected(callerRunsWhenRejected))
  
  def asyncDelayed = wrap(target.asyncDelayed)
  
}
