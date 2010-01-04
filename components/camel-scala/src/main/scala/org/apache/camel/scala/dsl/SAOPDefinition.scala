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

import builder.RouteBuilder
import org.apache.camel.model.AOPDefinition;

/**
 * Scala enrichment for Camel's AOPDefinition
 */
case class SAOPDefinition(override val target: AOPDefinition)(implicit val builder: RouteBuilder) extends SAbstractDefinition[AOPDefinition] {

  def before(before: String) = configure(target.before(before))
  def after(after: String) = configure(target.after(after))
  def afterFinally(after: String) = configure(target.afterFinally(after));
  def around(before: String, after: String) = configure(target.around(before, after))


  def configure(block: => Unit) = {
    block
    this
  }

}
