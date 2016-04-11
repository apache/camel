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

import junit.framework.TestCase
import junit.framework.TestCase.assertEquals

import org.apache.camel.model._

/**
 * Test using the low level processor definition API from Scala.
 *
 * In previous versions of the use of generics in the model package we would get compile errors in Scala
 * when trying to do things like this
 */
class UsingModelTest extends TestCase {

  def testUsingModel() {
    val routes = new RoutesDefinition
    val route = routes.route
    route.from("seda:foo")
    val bean = new BeanDefinition("myBean", "someMethod")
    route.addOutput(bean)

    assertEquals("Route[[From[seda:foo]] -> [Bean[ref:myBean method:someMethod]]]", route.toString)
  }

}
