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
package org.apache.camel.component.rest;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.CollectionFormat;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestParamType;

public class FromRestIdAndDescriptionTest extends FromRestGetTest {

    public void testFromRestModel() throws Exception {
        super.testFromRestModel();

        RestDefinition rest = context.getRestDefinitions().get(0);
        assertEquals("hello", rest.getId());
        assertEquals("Hello Service", rest.getDescriptionText());

        assertEquals("get-say", rest.getVerbs().get(0).getId());
        assertEquals("Says hello to you", rest.getVerbs().get(0).getDescriptionText());

        RestDefinition rest2 = context.getRestDefinitions().get(1);
        assertEquals("bye", rest2.getId());
        assertEquals("Bye Service", rest2.getDescriptionText());
        assertEquals("en", rest2.getDescription().getLang());

        assertEquals("Says bye to you", rest2.getVerbs().get(0).getDescriptionText());
        assertEquals("Updates the bye message", rest2.getVerbs().get(1).getDescriptionText());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().host("localhost");
                rest("/say/hello").id("hello").description("Hello Service")
                        .get().id("get-say").description("Says hello to you").to("direct:hello");

                rest("/say/bye").description("bye", "Bye Service", "en")
                        .get().description("Says bye to you").consumes("application/json")
                        .param().type(RestParamType.header).description("header param description1").dataType("integer").allowableValues("1", "2", "3", "4")
                        .defaultValue("1").name("header_count").required(true)
                        .endParam().
                        param().type(RestParamType.query).description("header param description2").dataType("string").allowableValues("a", "b", "c", "d")
                        .defaultValue("b").collectionFormat(CollectionFormat.multi).name("header_letter").required(false)
                        .endParam()
                        .responseMessage().code(300).message("test msg").responseModel(Integer.class)
                            .header("rate").description("Rate limit").dataType("integer").endHeader()
                        .endResponseMessage()
                        .responseMessage().code("error").message("does not work").endResponseMessage()
                        .to("direct:bye")
                        .post().description("Updates the bye message").to("mock:update");

                from("direct:hello")
                        .transform().constant("Hello World");

                from("direct:bye")
                        .transform().constant("Bye World");
            }
        };
    }
}
