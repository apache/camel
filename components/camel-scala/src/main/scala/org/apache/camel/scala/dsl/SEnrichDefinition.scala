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

import org.apache.camel.model.EnrichDefinition
import org.apache.camel.processor.aggregate.AggregationStrategy
import org.apache.camel.scala.dsl.builder.RouteBuilder

/**
 * Scala enrichment for Camel's EnrichDefinition
 */
case class SEnrichDefinition(override val target: EnrichDefinition)(implicit val builder: RouteBuilder) extends SAbstractDefinition[EnrichDefinition] {
  
  def aggregationStrategy(strategy: AggregationStrategy) = wrap(target.setAggregationStrategy(strategy))
  def aggregationStrategyRef(ref: String) = wrap(target.setAggregationStrategyRef(ref))
  def aggregationStrategyMethodName(name: String) = wrap(target.setAggregationStrategyMethodName(name))
  def aggregationStrategyMethodAllowNull(allowNull: Boolean) = wrap(target.setAggregationStrategyMethodAllowNull(allowNull))
  def aggregateOnException(aggregateOnException: Boolean) = wrap(target.setAggregateOnException(aggregateOnException))
  def shareUnitOfWork() = wrap(target.setShareUnitOfWork(true))
  def cacheSize(size: Integer) = wrap(target.setCacheSize(size))

}
