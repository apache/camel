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
package org.apache.camel.component.restlet;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestParamType;
import org.junit.Test;

import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

public class RestRestletApiTest extends RestletTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Test
    public void testApi() throws Exception {
        Exchange exchange = template.request("http://localhost:" + portNum + "/docs", null);
        assertThat(exchange.getOut().getHeader(HTTP_RESPONSE_CODE, Integer.class), is(200));
        String body = exchange.getOut().getBody(String.class);
        log.info("Received body: ", body);

        assertThat(body, containsString("\"version\" : \"1.2.3\""));
        assertThat(body, containsString("\"title\" : \"The hello rest thing\""));
        assertThat(body, containsString("\"/bye/{name}\""));
        assertThat(body, containsString("\"/hello/{name}\""));
        assertThat(body, containsString("\"summary\" : \"To update the greeting message\""));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // configure to use restlet on localhost with the given port
                restConfiguration()
                        .component("restlet").port(portNum)
                        .apiContextPath("/docs")
                        .apiProperty("cors", "true")
                        .apiProperty("api.title", "The hello rest thing")
                        .apiProperty("api.version", "1.2.3");

                rest("/hello").consumes("application/json").produces("application/json")
                        .get("/{name}").description("Saying hi")
                        .param().name("name").type(RestParamType.path).dataType("string").description("Who is it").endParam()
                        .to("log:hi");

                rest("/bye").consumes("application/json").produces("application/json")
                        .get("/{name}").description("Saying bye")
                        .param().name("name").type(RestParamType.path).dataType("string").description("Who is it").endParam()
                        .responseMessage().code(200).message("A reply message").endResponseMessage()
                        .to("log:bye")

                        .post().description("To update the greeting message")
                        .consumes("application/xml").produces("application/xml")
                        .param().name("greeting").type(RestParamType.body).dataType("string").description("Message to use as greeting").endParam()
                        .to("log:bye");
            }
        };
    }
}
