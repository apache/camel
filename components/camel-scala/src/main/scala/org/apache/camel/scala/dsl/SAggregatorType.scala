/*
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

import org.apache.camel.model.AggregatorType
import org.apache.camel.processor.aggregate.AggregationStrategy
import org.apache.camel.model.config.BatchResequencerConfig
import org.apache.camel.scala.dsl.builder.RouteBuilder

/**
 * Scala wrapper for Camel AggregatorType
 */
class SAggregatorType(val target: AggregatorType)(implicit val builder: RouteBuilder) extends SAbstractType with Wrapper[AggregatorType] {
  
  val unwrap = target
  
  def strategy(function: (Exchange, Exchange) => Exchange) = {
    println("testing")
    target.setAggregationStrategy(
      new AggregationStrategy() {
        def aggregate(oldExchange: Exchange, newExchange: Exchange) ={
          println(oldExchange + " + " + newExchange)
          try {
            val result = function(oldExchange, newExchange)
            println(" -> " + result)
            result
          } catch {
            case e:Exception => println(e); e.printStackTrace()
          }
          null
        }
      }
    )
    this
  }

  def batch(count: Int) = {
    target.batchSize(count)
    this
  }
  
}
