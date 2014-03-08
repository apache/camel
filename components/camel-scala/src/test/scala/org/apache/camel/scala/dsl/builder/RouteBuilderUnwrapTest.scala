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
package org.apache.camel.scala.dsl.builder
 
import org.apache.camel.scala.Wrapper
import org.apache.camel.scala.test.{Person,Adult}
import org.junit.Assert
import org.junit.Assert._
import org.junit.Test

class RouteBuilderUnwrapTest extends Assert {

  def builder = new RouteBuilder {
    
    val person = new PersonWrapper
    
    def testUnwrap() {
      //access the wrapper
      assertEquals("Apache Camel", person.vote)
      
      //unwrap when necessary
      assertTrue(person.canVote)
    }
    
  }

  @Test
  def testUnwrapWhenNecessary() {
    builder.testUnwrap()
  }
  
  class PersonWrapper extends Wrapper[Person] {
    
    val person = new Adult("Gert")
    val unwrap = person
    
    def vote = "Apache Camel"
    
  }
  
}
