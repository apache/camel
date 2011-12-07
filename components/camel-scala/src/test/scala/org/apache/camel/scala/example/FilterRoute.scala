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
package org.apache.camel.scala.example

import org.apache.camel.scala.dsl.builder.RouteBuilder

/**
 * A Camel route, defined using the Scala DSL. The route filters unwanted messages using Scala predicates.
 */
// START SNIPPET: e1
class FilterRoute {

  // define any method which creates a new org.apache.camel.scala.dsl.builder.RouteBuilder instance
  // and within the scope of RouteBuilder we have the Scala DSL at our disposal
  def createMyFilterRoute = new RouteBuilder {
    // and here we can use the Scala DSL to define the routes
    from("direct:start")
      // in the filter, we can use the scala closures in the predicate
      // here we check the IN message having a header with the key gold equals to true
      .filter(_.in("gold") == "true")
        .to("mock:gold")
  }

}
// END SNIPPET: e1
