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

import org.apache.camel.main.Main

/**
 * A Camel Application
 */
class MainApp {


    static void main(String... args)  {
        def camelContext = new DefaultCamelContext()

        println("printing something in the console MainApp")
        camelContext.addRoutes( new RouteBuilder() {

            @Override
            void configure() {
                println("Printing some thing before queue")
                from("Test").to("Test")
                println("printing something after queue")
            }
        })
         //camelContext.start()

       // Thread.sleep(10000)
       // camelContext.stop()

    }

}

