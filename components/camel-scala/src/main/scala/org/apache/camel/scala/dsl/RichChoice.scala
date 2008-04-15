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
package org.apache.camel.scala.dsl;

import org.apache.camel.model.ChoiceType

class RichChoiceType(val choice: ChoiceType, val builder:RouteBuilder) {

  def when(test: Exchange => Boolean)(block: => Unit) : ChoiceType = {
    choice
  }

  def apply(block: => Unit) : RichChoiceType = {
    builder.build(choice, block)
    this
  }

  def to(uri: String) = {
    choice.to(uri)
  }

  def otherwise(block: => Unit) = {
    choice.otherwise
    builder.build(choice, block)
  }
}
