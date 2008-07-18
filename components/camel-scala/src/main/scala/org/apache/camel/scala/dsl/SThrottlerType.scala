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

import org.apache.camel.model.ThrottlerType
import org.apache.camel.scala.dsl.builder.RouteBuilder

/**
 * Scala enrichment for Camel's ThrottlerType
 */
class SThrottlerType(val target: ThrottlerType)(implicit val builder: RouteBuilder) extends SAbstractType with Wrapper[ThrottlerType] {
 
  val unwrap = target
  
  /**
   * Time period in milliseconds
   */
  def per(period: Int) = {
    target.setTimePeriodMillis(period)
    this
  }
  
  def ms = this
  def milliseconds = ms
    
  def sec = {
    valueInMs *= 1000
    this
  }
  def seconds = sec
  
  def min = {
    valueInMs *= (60 * 1000)
    this
  }
  def minutes = min

  def valueInMs = target.getTimePeriodMillis()
  def valueInMs_=(period: Long) = target.setTimePeriodMillis(period)
}
