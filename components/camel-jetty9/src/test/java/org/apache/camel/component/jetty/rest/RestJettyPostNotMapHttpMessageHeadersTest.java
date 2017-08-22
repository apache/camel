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
package org.apache.camel.component.jetty.rest;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.Test;

public class RestJettyPostNotMapHttpMessageHeadersTest extends BaseJettyTest {

    @Test
    public void testPostNotMapHttpMessageHeadersTest() throws Exception {
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Exchange.HTTP_METHOD, "POST");
        headers.put(Exchange.CONTENT_TYPE, "application/x-www-form-urlencoded");
        String out = template.requestBodyAndHeaders("http://localhost:" + getPort() + "/rest/test", "{\"msg\": \"TEST\"}", headers, String.class);
        assertEquals("\"OK\"", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // configure to use jetty on localhost with the given port
                //ensure we don't extract key=value pairs from form bodies 
                //(application/x-www-form-urlencoded)
                restConfiguration().component("jetty").host("localhost").port(getPort()).bindingMode(RestBindingMode.json)
                    .endpointProperty("mapHttpMessageBody", "false")
                    .endpointProperty("mapHttpMessageHeaders", "false");
                    
                // use the rest DSL to define the rest services
                rest("/rest")
                    .post("/test").produces("application/json")
                        .to("direct:test");
                from("direct:test").log("*** ${body}").removeHeaders("Content-Type*")
                    .setBody().simple("OK");
            }
        };
    }

}
