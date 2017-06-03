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

import org.apache.camel.impl.{JndiRegistry, SimpleRegistry, CompositeRegistry}
import org.apache.camel.scala.dsl.builder.{ScalaRouteBuilder, RouteBuilder}
import org.junit.Test
import org.springframework.transaction.support.{DefaultTransactionStatus, AbstractPlatformTransactionManager}
import org.springframework.transaction.{TransactionDefinition, TransactionStatus, PlatformTransactionManager}

class TransactedTest extends ScalaTestSupport {

  @throws(classOf[Exception])
  override def createRegistry: JndiRegistry = {
    val registry = super.createRegistry
    // Just setup a dummy platform transaction manager for testing
    registry.bind("transactionManager", new AbstractPlatformTransactionManager() {
      override def doCommit(status: DefaultTransactionStatus): Unit = {}

      override def doBegin(transaction: scala.Any, definition: TransactionDefinition): Unit = {}

      override def doRollback(status: DefaultTransactionStatus): Unit = {}

      override def doGetTransaction(): AnyRef = {
        new Object()
      }
    })
    registry
  }

  @Test
  def testScalaRouteBuilder() {
    getMockEndpoint("mock:result").expectedMessageCount(1)

    template().sendBody("direct:start", "Hello World")

    assertMockEndpointsSatisfied()
  }

  // must use lazy as we want to evaluate this after CamelContext has been created
  override lazy val builder = {

    new ScalaRouteBuilder(context()) {

      "direct:start" ==> {
        routeId("myRoute")
        transacted
        to("mock:foo")
        to("mock:result")
      }
    }
  }

}

