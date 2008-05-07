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
package org.apache.camel.scala

import org.apache.camel.Exchange

/**
 * Rich wrapper for Camel's Exchange implementations
 */
class RichExchange(val exchange : Exchange) {

  def in : Any = exchange.getIn().getBody()
  
  def in_=(message: Any) = exchange.getIn().setBody(message)

  def in(header:String) : Any = exchange.getIn().getHeader(header)

  def in[T](target:Class[T]) : T = exchange.getIn().getBody(target)

  def out : Any = exchange.getOut().getBody()

  def out(header:String) : Any = exchange.getOut().getHeader(header)

  def out_=(message:Any) = exchange.getOut().setBody(message)

}
