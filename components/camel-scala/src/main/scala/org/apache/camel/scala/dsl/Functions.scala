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

import org.apache.camel.Exchange

/**
 * A set of convenience functions for use in RouteBuilders and other Scala code interacting with Camel
 */
trait Functions {

  /**
   * Convenience function for extracting the 'in' message body from a Camel org.apache.camel.Exchange
   *
   * Can also be used as a partially applied function where the DSL requires Exchange => Any
   */
  def body(exchange: Exchange) = exchange.getIn.getBody

}