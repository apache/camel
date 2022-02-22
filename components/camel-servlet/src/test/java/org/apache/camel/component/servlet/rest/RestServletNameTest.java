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

import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.servlet.CamelHttpTransportServlet;
import org.apache.camel.component.servlet.ServletCamelRouterTestSupport;
import org.apache.camel.component.servlet.ServletRestHttpBinding;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RestServletNameTest extends ServletCamelRouterTestSupport {

    @BindToRegistry("myBinding")
    private ServletRestHttpBinding restHttpBinding = new ServletRestHttpBinding();

    @Override
    protected DeploymentInfo getDeploymentInfo() {
        return Servlets.deployment()
                .setClassLoader(getClass().getClassLoader())
                .setContextPath(CONTEXT)
                .setDeploymentName(getClass().getName())
                .addServlet(Servlets.servlet("myServlet", CamelHttpTransportServlet.class)
                        .addMapping("/services/*"));
    }

    @Test
    public void testServletProducerGet() throws Exception {
        WebRequest req = new GetMethodWebRequest(contextUrl + "/services/users/123/basic");
        WebResponse response = query(req, false);

        assertEquals(200, response.getResponseCode());

        assertEquals("123;Donald Duck", response.getText());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // configure to use servlet on localhost
                restConfiguration().component("servlet").host("localhost").componentProperty("servletName", "myServlet");

                // use the rest DSL to define the rest services
                rest("/users/").get("{id}/basic").to("direct:basic");

                from("direct:basic").to("mock:input").process(exchange -> {
                    String id = exchange.getIn().getHeader("id", String.class);
                    exchange.getMessage().setBody(id + ";Donald Duck");
                });

            }
        };
    }

}
