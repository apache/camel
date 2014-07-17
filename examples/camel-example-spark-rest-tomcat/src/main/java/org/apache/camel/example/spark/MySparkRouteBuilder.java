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
package org.apache.camel.example.spark;

import org.apache.camel.builder.RouteBuilder;

/**
 * Define REST services using the Camel REST DSL
 */
public class MySparkRouteBuilder extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        // we only have a GET service, but we have PUT, POST, and all the other REST verbs we can use

       rest("hello/:me").component("spark-rest")
           .get().consumes("text/plain")
               .to("log:input")
               .transform().simple("Hello ${header.me}").endRest()
           .get().consumes("application/json")
               .to("log:input")
               .transform().simple("{ \"message\": \"Hello ${header.me}\" }").endRest()
           .get().consumes("text/xml")
               .to("log:input")
               .transform().simple("<message>Hello ${header.me}</message>");
    }

}
