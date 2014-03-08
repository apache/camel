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

import builder.{RouteBuilder, RouteBuilderSupport}
import org.apache.camel.processor.SortExpressionTest.MyReverseComparator
import org.apache.camel.processor.{SortBodyTest, SortExpressionTest}

/**
 * Scala DSL equivalent for the SortExpressionTest, using simple one-line Scala DSL syntax
 */
class SSortExpressionTest extends SortExpressionTest with RouteBuilderSupport {

  override def createRouteBuilder = new RouteBuilder {
    "direct:start" sort(_.in[String].split(",")) to("mock:result")
    "direct:reverse" sort(_.in[String].split(","), new MyReverseComparator()) to("mock:result")
  }

}

/**
 * Scala DSL equivalent for the SortBodyTest, using the Scala DSL block syntax
 */
class SSortBodyTest extends SortBodyTest with RouteBuilderSupport {

  override def createRouteBuilder = new RouteBuilder {
    "direct:start" ==> {
      sort(body)
      to("mock:result")
    }
  }

}