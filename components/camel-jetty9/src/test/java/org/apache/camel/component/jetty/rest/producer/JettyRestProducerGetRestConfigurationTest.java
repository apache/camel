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
import org.junit.Test;

public class JettyRestProducerGetRestConfigurationTest extends BaseJettyTest {

    @Test
    public void testRestGet() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Donald Duck");

        template.sendBodyAndHeader("direct:start", null, "name", "Donald Duck");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                String host = "localhost:" + getPort();

                restConfiguration().producerComponent("jetty").host(host);

                from("direct:start")
                        .to("rest:get:api:hello/hi/{name}")
                        .to("mock:result");

                from("jetty:http://localhost:{{port}}/api/hello/hi/?matchOnUriPrefix=true")
                    .transform().constant("Hello Donald Duck");
            }
        };
    }

}
