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
package org.apache.camel.component.rest;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.CollectionFormat;
import org.apache.camel.model.rest.RestParamType;

public class FromRestExplicitComponentTest extends FromRestGetTest {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // configure to use dummy-rest
                restConfiguration().component("dummy-rest").host("localhost");

                rest("/say/hello").get().to("direct:hello");

                rest("dummy-rest").path("/say/bye").get().consumes("application/json").param().type(RestParamType.header).description("header param description1")
                    .dataType("integer").allowableValues("1", "2", "3", "4").defaultValue("1").name("header_count").required(true).endParam().param().type(RestParamType.query)
                    .description("header param description2").dataType("string").allowableValues("a", "b", "c", "d").defaultValue("b").collectionFormat(CollectionFormat.multi)
                    .name("header_letter").required(false).endParam().responseMessage().code(300).message("test msg").responseModel(Integer.class).header("rate")
                    .description("Rate limit").dataType("integer").endHeader().endResponseMessage().responseMessage().code("error").message("does not work").endResponseMessage()
                    .to("direct:bye").post().to("mock:update");

                from("direct:hello").transform().constant("Hello World");

                from("direct:bye").transform().constant("Bye World");

            }
        };
    }
}
