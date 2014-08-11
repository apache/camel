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
package org.apache.camel.example.rest;

import com.wordnik.swagger.config.SwaggerConfig;
import com.wordnik.swagger.model.ApiListing;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.swagger.RestSwaggerReader;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestDefinition;
import scala.Option;

/**
 * Define REST services using the Camel REST DSL
 */
public class UserRouteBuilder extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        // configure we want to use servlet as the component for the rest DSL
        // and we enable json binding mode
        restConfiguration().component("servlet").bindingMode(RestBindingMode.json)
            // and output using pretty print
            .dataFormatProperty("prettyPrint", "true")
            // setup context path and port number that Apache Tomcat will deploy
            // this application with, as we use the servlet component, then we
            // need to aid Camel to tell it these details so Camel knows the url
            // to the REST services.
            // Notice: This is optional, but needed if the RestRegistry should
            // enlist accurate information. You can access the RestRegistry
            // from JMX at runtime
            .contextPath("camel-example-servlet-rest-tomcat/rest").port(8080);

        // this user REST service is json only
        rest("/user").description("User rest service")
            .consumes("application/json").produces("application/json")

            .get("/{id}").description("Find user by id").outType(User.class)
                .to("bean:userService?method=getUser(${header.id})")

            .put().description("Updates or create a user").type(User.class)
                .to("bean:userService?method=updateUser")

            .get("/findAll").description("Find all users").outTypeList(User.class)
                .to("bean:userService?method=listUsers");
    }

}
