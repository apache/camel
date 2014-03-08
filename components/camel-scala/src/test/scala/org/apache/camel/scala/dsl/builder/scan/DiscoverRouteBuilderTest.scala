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
package org.apache.camel.scala.dsl.builder.scan

import org.apache.camel.CamelContext
import org.apache.camel.component.mock.MockEndpoint
import org.springframework.context.support.ClassPathXmlApplicationContext
import org.junit.Assert
import org.junit.Assert._
import org.junit.Test

/**
 * Test to make sure that Scala DSL RouteBuilders can be discovered from the classpath
 */
class DiscoverRouteBuilderTest extends Assert {
  
  @Test
  def testDiscovery() {
    val spring = new ClassPathXmlApplicationContext("org/apache/camel/scala/dsl/builder/scan/scan-camel-context.xml")
    val camel = spring.getBean("myCamel").asInstanceOf[CamelContext]
    assertNotNull(camel)
    assertEquals(1, camel.getRoutes.size())
    
    // let us just send a simple message to make sure we discovered the correct RouteBuilder
    val template = camel.createProducerTemplate()
    template.sendBody("direct:scan", "request")
    val mock = camel.getEndpoint("mock:discovery").asInstanceOf[MockEndpoint]
    mock.expectedMessageCount(1)
    mock.assertIsSatisfied()
  }

}
