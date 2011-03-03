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
package ${package}

import org.apache.camel.Exchange
import org.apache.camel.scala.dsl.builder.RouteBuilder

/**
 * A Camel Router using the Scala DSL
 */
class MyRouteBuilder extends RouteBuilder {

   //an example for the simple DSL syntax...
   "timer://foo?fixedRate=true&delay=0&period=10s" setbody("simple test") to "log:simple"

   // an example of a Processor method
   val myProcessorMethod = (exchange: Exchange) => {
     exchange.getIn.setBody("block test")
   }
   
   // ...and another one using Scala blocks
   "timer://foo?fixedRate=true&delay=5s&period=10s" ==> {
      process(myProcessorMethod)
      to("log:block")
   }
}
