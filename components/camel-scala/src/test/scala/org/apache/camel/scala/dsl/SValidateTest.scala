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
import org.apache.camel.processor.{ValidateRegExpTest, ValidateSimpleTest}
import org.apache.camel.processor.validation.PredicateValidationException
import org.apache.camel.CamelExecutionException
import org.apache.camel.TestSupport.assertIsInstanceOf

import junit.framework.TestCase.{fail, assertTrue}

/**
 * Scala DSL equivalent for the ValidateSimpleTest, using simple one-line Scala DSL syntax
 */
class SValidateSimpleTest extends ValidateSimpleTest with RouteBuilderSupport {

  // we need to override the test method because the validation exception looks slightly different in Scala
  override def testSendNotMatchingMessage() {
    resultEndpoint.expectedMessageCount(0)

    try {
      template.sendBody(startEndpoint, "1.1.2010")
      fail("CamelExecutionException expected")
    } catch {
      case e: CamelExecutionException => {
        // expected
        assertIsInstanceOf(classOf[PredicateValidationException], e.getCause)
        // as the Expression could be different between the DSL and simple language, here we just check part of the message
        assertTrue("Get a wrong exception message",
                   e.getCause.getMessage.startsWith("Validation failed for Predicate[org.apache.camel.scala.ScalaPredicate"))
      }
    }

    assertMockEndpointsSatisfied()
  }  

  override def createRouteBuilder = new RouteBuilder {
    "direct:start" validate(simple("${body} contains 'Camel'")) to("mock:result")
  }
}

class SValidateSimpleBuilderTest extends SValidateSimpleTest with RouteBuilderSupport {
  override def createRouteBuilder = new RouteBuilder {
    from("direct:start").validate(simple("${body} contains 'Camel'")).to("mock:result")
  }
}

/**
 * Scala DSL equivalent for the ValidateRegExpTest, using the Scala DSL block syntax
 */
class SValidateRegExpTest extends ValidateRegExpTest with RouteBuilderSupport {

  // we need to override the test method because the validation exception looks slightly different in Scala
  override def testSendNotMatchingMessage() {
    resultEndpoint.expectedMessageCount(0)

    try {
      template.sendBody(startEndpoint, "1.1.2010")
      fail("CamelExecutionException expected")
    } catch {
      case e: CamelExecutionException => {
        // expected
        assertIsInstanceOf(classOf[PredicateValidationException], e.getCause)
        // as the Expression could be different between the DSL and simple language, here we just check part of the message
        assertTrue("Get a wrong exception message",
                   e.getCause.getMessage.startsWith("Validation failed for Predicate[org.apache.camel.scala.ScalaPredicate"))
      }
    }

    assertMockEndpointsSatisfied()
  }

  override def createRouteBuilder = new RouteBuilder {

    "direct:start" ==> {
      validate(_.in[String].matches("^\\d{2}\\.\\d{2}\\.\\d{4}$"))
      to("mock:result")
    }
  }
}
