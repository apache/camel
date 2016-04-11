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

import org.apache.camel.processor.PolicyPerRouteTest
import org.apache.camel.scala.dsl.builder.{RouteBuilder, RouteBuilderSupport}

class SPolicyPerRouteTest extends PolicyPerRouteTest with RouteBuilderSupport {

  override def createRouteBuilder = new RouteBuilder {
    from("direct:start")
      .policy("foo").to("mock:foo").to("mock:bar").to("mock:result")

    from("direct:send")
      .to("direct:start")
      .to("mock:response")

}

}
