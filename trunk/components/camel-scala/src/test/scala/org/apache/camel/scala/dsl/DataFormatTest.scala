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
package org.apache.camel.scala
package dsl

import org.junit.Test
import builder.RouteBuilder
import test.Adult

/**
 * Test case for working with data formats
 */
class DataFormatTest extends ScalaTestSupport {
 
  @Test
  def testDataFormat() {
    val person = new Adult("Captain Nemo")
    "mock:a" expect { _.received(person) } 
    test {
      "direct:a" ! person
    }
  }
    
  val builder =
    new RouteBuilder {
       //START SNIPPET: dataformat
       "direct:a" marshal(serialization) to "direct:serial"
       
       "direct:serial" ==> {
         unmarshal(serialization)
         to ("mock:a") 
       }
       //END SNIPPET: dataformat
    }

}
