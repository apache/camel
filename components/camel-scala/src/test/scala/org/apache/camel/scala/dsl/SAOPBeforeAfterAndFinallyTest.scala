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
import org.apache.camel.processor.{AOPAfterFinallyTest, AOPAroundTest, AOPAfterTest, AOPBeforeTest}

/**
 * Scala DSL equivalent for the org.apache.camel.processor.AOPBeforeTest
 */
class SAOPBeforeTest extends AOPBeforeTest with RouteBuilderSupport {

  override def createRouteBuilder = new RouteBuilder {
    "direct:start" ==> {
      aop.before("mock:before") {
        setbody("Bye World")
        to("mock:result")
      }
    }
  }
}

/**
 * Scala DSL equivalent for the org.apache.camel.processor.AOPAroundTest
 */
class SAOPAroundTest extends AOPAroundTest with RouteBuilderSupport {

  override def createRouteBuilder = new RouteBuilder {
    "direct:start" ==> {
      aop.around("mock:before", "mock:after") {
        setbody("Bye World")
        to("mock:result")
      }
    }
  }
}

/**
 * Scala DSL equivalent for the org.apache.camel.processor.AOPAfterTest
 */
class SAOPAfterTest extends AOPAfterTest with RouteBuilderSupport {
  override def createRouteBuilder = new RouteBuilder {
    "direct:start" ==> {
       aop.after("mock:after") {
         setbody("Bye World")
         to("mock:result")
       }
    }
  }
}

/**
 * Scala DSL equivalent for the org.apache.camel.processor.AOPAfterFinallyTest
 */
class SAOPAfterFinallyTest extends AOPAfterFinallyTest with RouteBuilderSupport {
  override def createRouteBuilder = new RouteBuilder {
    "direct:start" ==> {
       aop.afterFinally("mock:after") {
         choice {
           when (_.in == "Hello World") {
             setbody("Bye World")
           } otherwise {
             setbody("Kabom the World")
             throwException(new IllegalArgumentException("Damn"))
           }
         }
         to ("mock:result")
       }
    }
  }
}
