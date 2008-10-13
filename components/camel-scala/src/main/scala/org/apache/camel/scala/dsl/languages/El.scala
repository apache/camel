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
package org.apache.camel.scala.dsl.languages;

import org.apache.camel.builder.xml.XPathBuilder

/**
 * Trait to improve expression language support for Scala DSL
 */
trait El {
  
  implicit def exchangeToEl(exchange: Exchange) = new RichElExchange(exchange)
  
  def language(exchange: Exchange) = exchange.getContext().resolveLanguage("el")
  def el(el: String)(exchange: Exchange) : Any = 
    language(exchange).createExpression(el).evaluate(exchange)
  
  class RichElExchange(val exchange: Exchange) {
    
    def el(el: String) : Any = El.this.el(el)(exchange)
    
  }
  
}
