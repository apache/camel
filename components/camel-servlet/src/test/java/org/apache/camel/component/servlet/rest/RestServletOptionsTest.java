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
package org.apache.camel.component.servlet.rest;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.servlet.ServletCamelRouterTestSupport;
import org.apache.camel.component.servlet.ServletRestHttpBinding;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RestServletOptionsTest extends ServletCamelRouterTestSupport {

    @BindToRegistry("myBinding")
    private ServletRestHttpBinding restHttpBinding = new ServletRestHttpBinding();

    @Test
    public void testServletOptions() throws Exception {
        WebRequest req = new OptionsMethodWebRequest(contextUrl + "/services/users/v1/customers");
        WebResponse response = query(req, false);

        assertEquals(200, response.getResponseCode());
        assertEquals("GET,OPTIONS", response.getHeaderField("ALLOW"));
        assertEquals("", response.getText());

        req = new OptionsMethodWebRequest(contextUrl + "/services/users/v1/id/123");
        response = query(req, false);

        assertEquals(200, response.getResponseCode());
        assertEquals("PUT,OPTIONS", response.getHeaderField("ALLOW"));
        assertEquals("", response.getText());
    }

    @Test
    public void testMultipleServletOptions() throws Exception {
        WebRequest req = new OptionsMethodWebRequest(contextUrl + "/services/users/v2/options");
        WebResponse response = query(req, false);

        assertEquals(200, response.getResponseCode());
        assertEquals("GET,POST,OPTIONS", response.getHeaderField("ALLOW"));
        assertEquals("", response.getText());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // configure to use servlet on localhost
                restConfiguration().component("servlet").host("localhost").endpointProperty("httpBinding", "#myBinding");

                // use the rest DSL to define the rest services
                rest("/users/").get("v1/customers").to("mock:customers").put("v1/id/{id}").to("mock:id").get("v2/options")
                        .to("mock:options").post("v2/options").to("mock:options");
            }
        };
    }

}
