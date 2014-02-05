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
 
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy
import org.junit.Test
import builder.RouteBuilder
import org.apache.camel.Exchange
import org.apache.camel.builder.ExchangeBuilder.anExchange

/**
 * Test case for message aggregator
 */
class AggregatorTest extends ScalaTestSupport {
  
  val count = 100
  
  @Test
  def testSimpleAggregator() {
    "mock:a" expect { _.received("message " + count) } 
    test {
      for (i <- 1 to count) {
        "direct:a" ! ("message " + i)
      }
    }
  }

 @Test
 def testBlockAggregator() {
    "mock:b" expect { _.received("message " + count) } 
    test {
      for (i <- 1 to count) {
        "direct:b" ! ("message " + i)
      }
    }
  }

  @Test
  def testWrappingFunctionalAggregator() {
    "mock:c" expect {
      _.received("foobar")
    }
    test {
      "direct:c" ! "foo"
      "direct:c" ! "bar"
    }
  }

  @Test
  def testFunctionalAggregator() {
    "mock:d" expect {
      _.received("foobar")
    }
    test {
      "direct:d" ! "foo"
      "direct:d" ! "bar"
    }
  }

  val builder =
    new RouteBuilder {
       "direct:a" ==> {
         aggregate (_.in[String].substring(0, 7), new UseLatestAggregationStrategy()) completionSize 100 to "mock:a" }

       //START SNIPPET: block
       "direct:b" ==> {
         aggregate(_.in[String].substring(0,7), new UseLatestAggregationStrategy()).completionSize(100) {
           to ("mock:b")
         }
       }
       //END SNIPPET: block

      "direct:c" ==> {
        val aggregator = (oldEx: Exchange, newEx: Exchange) => oldEx match {
          case null => newEx.in[String]
          case _ => oldEx.in[String] + newEx.in[String]
        }
        aggregate ("constant", aggregator) completionSize 2 to "mock:c"
      }

      "direct:d" ==> {
        val aggregator = (oldEx: Exchange, newEx: Exchange) => oldEx match {
          case null => newEx
          case _ => anExchange(newEx.getContext).withBody(oldEx.in[String] + newEx.in[String]).build
        }
        aggregate ("constant", aggregator) completionSize 2 to "mock:d"
      }
    }

}