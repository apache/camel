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

import dsl.languages.LanguageFunction

/**
 * Scala implementation for an Apache Camel Expression
 */
class ScalaExpression(val expression: Exchange => Any) extends Expression {
  
  def evaluate(exchange: Exchange) = {
    val value = expression(exchange)
    value match {
      case f : LanguageFunction => f.evaluate(exchange, classOf[Object])
      case _ => value.asInstanceOf[Object]
    }
  }

  def evaluate[Target](exchange: Exchange, toType: Class[Target]) = {
    val value = evaluate(exchange)
    exchange.getContext.getTypeConverter.convertTo(toType, value)
  }

}
