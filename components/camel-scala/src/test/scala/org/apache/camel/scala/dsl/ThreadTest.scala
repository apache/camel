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
 
import scala.dsl.builder.RouteBuilder
import _root_.scala.collection.mutable.HashSet
import junit.framework.Assert._

/**
 * Test for threads support in Scala DSL
 */
class ThreadTest extends ScalaTestSupport {
  
  val threads = new HashSet[Thread]()
  
  override def setUp() = {
    threads.clear
    super.setUp
  }

  def testSimpleThreads() = doTestThreads("direct:a", "mock:a")
  def testBlockThreads() = doTestThreads("direct:b", "mock:b")
  
  def doTestThreads(from: String, mock: String) = {
    mock expect {_.count = 5}
    test {
      from ! (1, 2, 3, 4, 5)
    }
    assertEquals(3, threads.size)
  }
  
  val builder = new RouteBuilder {
     //START SNIPPET: simple
     "direct:a" thread(3) process(counter) to("mock:a")
     //END SNIPPET: simple
     
     //START SNIPPET: block
     "direct:b" thread(3) apply {
       process(counter)
       to("mock:b")
     }
     //END SNIPPET: block
     
     def counter(exchange: org.apache.camel.Exchange) {
       threads += Thread.currentThread
     }
  }
}


