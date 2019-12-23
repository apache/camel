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
package org.apache.camel.component.jetty.rest;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.apache.camel.model.rest.RestParamType;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Does not run well on CI due test uses JMX mbeans")
public class RestApiJettyTest extends BaseJettyTest {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Test
    public void testApi() throws Exception {
        String out = template.requestBody("jetty:http://localhost:{{port}}/api-doc", null, String.class);
        assertNotNull(out);

        assertTrue(out.contains("\"version\" : \"1.2.3\""));
        assertTrue(out.contains("\"title\" : \"The hello rest thing\""));
        assertTrue(out.contains("\"host\" : \"localhost:" + getPort() + "\""));
        assertTrue(out.contains("\"/hello/bye/{name}\""));
        assertTrue(out.contains("\"/hello/hi/{name}\""));
        assertTrue(out.contains("\"summary\" : \"To update the greeting message\""));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().component("jetty").host("localhost").port(getPort()).apiContextPath("/api-doc").apiProperty("cors", "true")
                    .apiProperty("api.title", "The hello rest thing").apiProperty("api.version", "1.2.3");

                rest("/hello").consumes("application/json").produces("application/json").get("/hi/{name}").description("Saying hi").param().name("name").type(RestParamType.path)
                    .dataType("string").description("Who is it").endParam().to("log:hi").get("/bye/{name}").description("Saying bye").param().name("name").type(RestParamType.path)
                    .dataType("string").description("Who is it").endParam().responseMessage().code(200).message("A reply message").endResponseMessage().to("log:bye").post("/bye")
                    .description("To update the greeting message").consumes("application/xml").produces("application/xml").param().name("greeting").type(RestParamType.body)
                    .dataType("string").description("Message to use as greeting").endParam().to("log:bye");
            }
        };
    }

}
