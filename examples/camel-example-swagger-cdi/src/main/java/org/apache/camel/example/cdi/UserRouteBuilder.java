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
package org.apache.camel.example.cdi;

import javax.enterprise.context.ApplicationScoped;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;

import static org.apache.camel.model.rest.RestParamType.body;
import static org.apache.camel.model.rest.RestParamType.path;

/**
 * Define REST services using the Camel REST DSL
 */
@ApplicationScoped
public class UserRouteBuilder extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        // configure we want to use undertow as the component for the rest DSL
        // and we enable json binding mode
        restConfiguration().component("undertow")
            // use json binding mode so Camel automatic binds json <--> pojo
            .bindingMode(RestBindingMode.json)
            // and output using pretty print
            .dataFormatProperty("prettyPrint", "true")
            // setup context path on localhost and port number that netty will use
            .contextPath("/").host("localhost").port(8080)
            // add swagger api-doc out of the box
            .apiContextPath("/api-doc")
                .apiProperty("api.title", "User API").apiProperty("api.version", "1.2.3")
                // and enable CORS
                .apiProperty("cors", "true");

        // this user REST service is json only
        rest("/user").description("User rest service")
            .consumes("application/json").produces("application/json")

            .get("/{id}").description("Find user by id").outType(User.class)
                .param().name("id").type(path).description("The id of the user to get").dataType("integer").endParam()
                .responseMessage().code(200).message("The user").endResponseMessage()
                .to("bean:userService?method=getUser(${header.id})")

            .put().description("Updates or create a user").type(User.class)
                .param().name("body").type(body).description("The user to update or create").endParam()
                .responseMessage().code(200).message("User created or updated").endResponseMessage()
                .to("bean:userService?method=updateUser")

            .get("/findAll").description("Find all users").outType(User[].class)
                .responseMessage().code(200).message("All users").endResponseMessage()
                .to("bean:userService?method=listUsers");
    }

}
