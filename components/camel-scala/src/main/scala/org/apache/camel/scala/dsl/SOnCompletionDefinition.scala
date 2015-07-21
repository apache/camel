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

import java.util.concurrent.ExecutorService
import model.{WhenDefinition,OnCompletionDefinition}
import org.apache.camel.scala.dsl.builder.RouteBuilder

/**
 * Scala enrichment for the Camel OnCompletionDefinition 
 */
case class SOnCompletionDefinition(override val target : OnCompletionDefinition)(implicit val builder : RouteBuilder) extends SAbstractDefinition[OnCompletionDefinition] {

  override def when(predicate : Exchange => Any) : SOnCompletionDefinition =
    wrap(target.setOnWhen(new WhenDefinition(predicate)))

  def onFailureOnly = wrap(target.onFailureOnly)
  def onCompleteOnly = wrap(target.onCompleteOnly)
  
  def useOriginalBody = wrap(target.useOriginalBody)

  def modeBeforeConsumer = wrap(target.modeBeforeConsumer)
  def modeAfterConsumer = wrap(target.modeAfterConsumer)

  def parallelProcessing = wrap(target.parallelProcessing)
  def executorService(executorService: ExecutorService) = wrap(target.setExecutorService(executorService))
  def executorServiceRef(ref: String) = wrap(target.setExecutorServiceRef(ref))
}
