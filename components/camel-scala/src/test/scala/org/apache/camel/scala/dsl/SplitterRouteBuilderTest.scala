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
package org.apache.camel
package scala.dsl
 
import builder.RouteBuilder
import org.w3c.dom.Document
import org.junit.Test

/**
 * Test case for Splitter
 */
class SplitterRouteBuilderTest extends ScalaTestSupport {

  @Test
  def testSimpleSplitter() {
    "mock:a" expect { _.count = 3}
    "direct:a" ! <persons><person id="1"/><person id="2"/><person id="3"/></persons>
    "mock:a" assert()
  }
  
  @Test
  def testBlockSplitter() {
    "mock:b" expect { _.count = 3}
    "mock:c" expect { _.count = 3}
    "direct:b" ! <persons><person id="1"/><person id="2"/><person id="3"/></persons>
    "mock:b" assert()
    "mock:c" assert()
  }

  val builder =
    new RouteBuilder {
       //START SNIPPET: simple
       "direct:a" as(classOf[Document]) split(xpath("/persons/person")) to "mock:a"
       //END SNIPPET: simple
       
       //START SNIPPET: block
       "direct:b" ==> {
         as(classOf[Document])
         split(xpath("/persons/person")) {
           to("mock:b")
           to("mock:c")
         }
       }
       //END SNIPPET: block
    }

}
