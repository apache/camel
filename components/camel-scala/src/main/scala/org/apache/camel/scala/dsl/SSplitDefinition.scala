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
import org.apache.camel.Processor
import org.apache.camel.model.SplitDefinition
import org.apache.camel.processor.aggregate.AggregationStrategy
import org.apache.camel.scala.dsl.builder.RouteBuilder

case class SSplitDefinition(override val target: SplitDefinition)(implicit val builder: RouteBuilder) extends SAbstractDefinition[SplitDefinition] {
  def aggregationStrategy(aggregationStrategy: AggregationStrategy) = wrap(target.aggregationStrategy(aggregationStrategy))
  
  def aggregationStrategyRef(ref: String) = wrap(target.aggregationStrategyRef(ref))
  
  def parallelProcessing = wrap(target.parallelProcessing)

  def parallelAggregate = wrap(target.parallelAggregate)

  def streaming = wrap(target.streaming)
  
  def stopOnException = wrap(target.stopOnException)
  
  def executorService(executorService: ExecutorService) = wrap(target.setExecutorService(executorService))
  
  def executorServiceRef(ref: String) = wrap(target.setExecutorServiceRef(ref))
  
  def onPrepare(onPrepare :Processor) = wrap(target.onPrepare(onPrepare))
  
  def onPrepareRef(ref :String) = wrap(target.onPrepareRef(ref))
  
  def timeout(timeout :Long) = wrap(target.timeout(timeout))
  
  def shareUnitOfWork = wrap(target.shareUnitOfWork)
  
}
