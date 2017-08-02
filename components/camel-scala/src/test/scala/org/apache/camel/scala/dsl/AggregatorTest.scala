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
import org.apache.camel.builder.ExchangeBuilder.anExchange
import org.apache.camel.processor.BodyInAggregatingStrategy
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy
import org.apache.camel.scala.dsl.builder.RouteBuilder
import org.junit.Test

/**
 * Test case for message aggregator
 */
class AggregatorTest extends ScalaTestSupport {

  val count = 100

  @Test
  def testSimpleAggregator() {
    "mock:aggregated" expect {
      _.received("message " + count)
    }
    test {
      for (i <- 1 to count) {
        "direct:completion-size" ! ("message " + i)
      }
    }
  }

  @Test
  def testBlockAggregator() {
    "mock:aggregated" expect {
      _.received("message " + count)
    }
    test {
      for (i <- 1 to count) {
        "direct:completion-size-block" ! ("message " + i)
      }
    }
  }

  @Test
  def testWrappingFunctionalAggregator() {
    "mock:aggregated" expect {
      _.received("foobar")
    }
    test {
      "direct:functional-aggregator-wrapped" ! "foo"
      "direct:functional-aggregator-wrapped" ! "bar"
    }
  }

  @Test
  def testFunctionalAggregator() {
    "mock:aggregated" expect {
      _.received("foobar")
    }
    test {
      "direct:functional-aggregator" ! "foo"
      "direct:functional-aggregator" ! "bar"
    }
  }

  @Test
  def testAggregateSimplePredicate() {
    "mock:aggregated" expect {
      _.received("A+B+C")
    }
    test {
      "direct:predicate" ! "A"
      "direct:predicate" ! "B"
      "direct:predicate" ! "C"
    }
  }

  @Test
  def testAggregatePredicateWithBlock() {
    "mock:aggregated" expect {
      _.received("A+B+C")
    }
    test {
      "direct:predicate-block" ! "A"
      "direct:predicate-block" ! "B"
      "direct:predicate-block" ! "C"
    }
  }

  val builder =
    new RouteBuilder {
      "direct:completion-size" ==> {
        aggregate(_.in[String].substring(0, 7), new UseLatestAggregationStrategy()) completionSize count to "mock:aggregated"
      }

      "direct:completion-size-block" ==> {
        aggregate(_.in[String].substring(0, 7), new UseLatestAggregationStrategy()).completionSize(count) {
          to("mock:aggregated")
        }
      }

      "direct:functional-aggregator-wrapped" ==> {
        val aggregator = (oldEx: Exchange, newEx: Exchange) => oldEx match {
          case null => newEx.in[String]
          case _ => oldEx.in[String] + newEx.in[String]
        }
        aggregate("constant", aggregator) completionSize 2 to "mock:aggregated"
      }

      "direct:functional-aggregator" ==> {
        val aggregator = (oldEx: Exchange, newEx: Exchange) => oldEx match {
          case null => newEx
          case _ => anExchange(newEx.getContext).withBody(oldEx.in[String] + newEx.in[String]).build
        }
        aggregate("constant", aggregator) completionSize 2 to "mock:aggregated"
      }

      "direct:predicate" ==> {
        aggregate("constant", new BodyInAggregatingStrategy()) completionPredicate (_.in[String].contains("A+B+C")) to "mock:aggregated"
      }

      "direct:predicate-block" ==> {
        aggregate("constant", new BodyInAggregatingStrategy()).completionPredicate(_.in[String].contains("A+B+C")) {
          to("mock:aggregated")
        }
      }
    }
}
