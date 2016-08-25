/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.jetty.rest.producer;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.apache.camel.swagger.component.SwaggerComponent;
import org.junit.Test;

public class JettyRestProducerGetQueryParamTest extends BaseJettyTest {

    @Test
    public void testSwaggerGet() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye Donald Duck");

        template.sendBodyAndHeader("direct:start", null, "name", "Donald Duck");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                SwaggerComponent sc = new SwaggerComponent();
                sc.setComponentName("jetty");
                context.addComponent("swagger", sc);

                String host = "localhost:" + getPort();

                from("direct:start")
                        .toF("swagger:get:bye?host=%s&apiDoc=%s", host, "hello-api.json")
                        .to("mock:result");

                from("jetty:http://localhost:{{port}}/api/bye/?matchOnUriPrefix=true")
                    .transform().simple("Bye ${header.name}");
            }
        };
    }

}
