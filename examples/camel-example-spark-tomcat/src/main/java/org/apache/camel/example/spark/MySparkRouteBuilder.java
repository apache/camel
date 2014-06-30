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

import org.apache.camel.component.spark.SparkRouteBuilder;

/**
 * Define REST services using {@link SparkRouteBuilder}.
 */
public class MySparkRouteBuilder extends SparkRouteBuilder {

    @Override
    public void configure() throws Exception {
        // this is a very simple Camel route, but we can do more routing
        // as SparkRouteBuilder extends the regular RouteBuilder from camel-core
        // which means we can do any kind of Camel Java DSL routing here

        // we only have a GET service, but we have PUT, POST, and all the other REST verbs we can use

        get("hello/:me", "text/plain")
            .transform().simple("Hello ${header.me}");

        get("hello/:me", "application/json")
            .transform().simple("{ \"message\": \"Hello ${header.me}\" }");

        get("hello/:me", "text/xml")
            .transform().simple("<message>Hello ${header.me}</message>");
    }

}
