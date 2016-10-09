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

import org.apache.camel.model.ResequenceDefinition
import org.apache.camel.model.config.BatchResequencerConfig
import org.apache.camel.model.config.StreamResequencerConfig
import org.apache.camel.processor.resequencer.ExpressionResultComparator
import org.apache.camel.scala.dsl.builder.RouteBuilder

case class SResequenceDefinition(override val target: ResequenceDefinition)(implicit val builder: RouteBuilder) extends SAbstractDefinition[ResequenceDefinition] {
  
  def batch(count: Int) : SResequenceDefinition = {
    val config = new BatchResequencerConfig()
    config.setBatchSize(count)
    target.batch(config)
    this
  }
  
  def stream = wrap(target.stream)
  
  def stream(config: StreamResequencerConfig) = wrap(target.stream(config))
  
  def batch = wrap(target.batch)
  
  def batch(config: BatchResequencerConfig) = wrap(target.batch(config))
  
  def timeout(timeout: Long) = wrap(target.timeout(timeout))
  
  def rejectOld = wrap(target.rejectOld)
  
  def size(size: Int) = wrap(target.size(size))
  
  def capacity(capacity: Int) = wrap(target.capacity(capacity))
  
  def allowDuplicates = wrap(target.allowDuplicates)
  
  def reverse = wrap(target.reverse)
  
  def ignoreInvalidExchanges = wrap(target.ignoreInvalidExchanges)
  
  def comparator(comparator: ExpressionResultComparator) = wrap(target.comparator(comparator))
}
