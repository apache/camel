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

import org.junit.Test
import builder.RouteBuilder

/**
 * Test case for message load balancer
 */
class LoadBalancerTest extends ScalaTestSupport {
  
  @Test
  def testLoadBalancer() {
    "mock:a" expect { _.received("message 1", "message 4") } 
    "mock:b" expect { _.received("message 2", "message 5") }
    "mock:c" expect { _.received("message 3", "message 6") }
    for (id <- 1 to 6) "direct:a" ! "message " + id   
    "mock:a" assert()
    "mock:b" assert()
    "mock:c" assert()
  }
    
  val builder =
    new RouteBuilder {
       //START SNIPPET: loadbalance
       "direct:a" ==> {
         loadbalance roundrobin {
           to ("mock:a")
           to ("mock:b")
           to ("mock:c")
         }
       }
       //END SNIPPET: loadbalance
    }

}
