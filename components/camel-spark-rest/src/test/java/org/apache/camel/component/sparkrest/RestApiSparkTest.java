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
package org.apache.camel.component.sparkrest;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestParamType;
import org.junit.Test;

public class RestApiSparkTest extends BaseSparkTest {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Test
    public void testApi() throws Exception {
        String out = template.requestBody("http://localhost:" + getPort() + "/api-doc", null, String.class);
        assertNotNull(out);
        assertTrue(out.contains("\"version\" : \"1.2.3\""));
        assertTrue(out.contains("\"title\" : \"The hello rest thing\""));
        assertTrue(out.contains("\"/hello/bye/{name}\""));
        assertTrue(out.contains("\"/hello/hi/{name}\""));
        assertTrue(out.contains("\"summary\" : \"To update the greeting message\""));

        // regular REST service should still work
        out = template.requestBody("http://localhost:" + getPort() + "/hello/hi/world", null, String.class);
        assertEquals("Hello World", out);

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().component("spark-rest").host("localhost").port(getPort()).apiContextPath("/api-doc")
                        .apiProperty("cors", "true").apiProperty("api.title", "The hello rest thing").apiProperty("api.version", "1.2.3");

                rest("/hello").consumes("application/json").produces("application/json")
                    .get("/hi/{name}").description("Saying hi")
                        .param().name("name").type(RestParamType.path).dataType("string").description("Who is it").endParam()
                        .to("direct:hello")
                    .get("/bye/{name}").description("Saying bye")
                        .param().name("name").type(RestParamType.path).dataType("string").description("Who is it").endParam()
                        .responseMessage().code(200).message("A reply message").endResponseMessage()
                        .to("log:bye")
                    .post("/bye").description("To update the greeting message").consumes("application/xml").produces("application/xml")
                        .param().name("greeting").type(RestParamType.body).dataType("string").description("Message to use as greeting").endParam()
                        .to("log:bye");

                from("direct:hello")
                        .transform().constant("Hello World");

            }
        };
    }

}
