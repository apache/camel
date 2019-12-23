/*
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
package org.apache.camel.example.cdi.rest.servlet;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.camel.CamelContext;
import org.apache.camel.Header;
import org.apache.camel.builder.RouteBuilder;

public class Application {

    @ApplicationScoped
    public static class HelloRoute extends RouteBuilder {

        @Override
        public void configure() {
            rest("/say/")
                .produces("text/plain")
                .get("hello")
                    .route()
                    .transform().constant("Hello World!")
                    .endRest()
                .get("hello/{name}")
                    .route()
                    .bean("hello")
                    .log("${body}");
        }
    }

    @Named("hello")
    public static class Hello {

        @Inject
        CamelContext context;

        public String hello(@Header("name") String name) {
            return "Hello " + name + ", I'm " + context + "!";
        }
    }
}
