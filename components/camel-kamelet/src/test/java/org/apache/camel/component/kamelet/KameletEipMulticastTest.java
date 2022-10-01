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
package org.apache.camel.component.kamelet;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.MulticastDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class KameletEipMulticastTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testOne() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("echo")
                        .from("kamelet:source")
                        .setBody(body().append(body()));

                routeTemplate("reverse")
                        .from("kamelet:source")
                        .setBody(this::reverse);

                from("direct:start").routeId("start")
                    .multicast()
                        .kamelet("echo")
                        .kamelet("reverse") // this becomes output on previous kamelet
                    .end()
                    .to("mock:result");
            }

            private Object reverse(Exchange exchange) {
                StringBuilder sb = new StringBuilder(exchange.getMessage().getBody(String.class));
                sb.reverse();
                return sb.toString();
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("CBACBA");

        template.sendBody("direct:start", "ABC");

        MockEndpoint.assertIsSatisfied(context);

        RouteDefinition rd = context.getRouteDefinition("start");
        MulticastDefinition md = ProcessorDefinitionHelper.findFirstTypeInOutputs(rd.getOutputs(), MulticastDefinition.class);
        Assertions.assertEquals(1, md.getOutputs().size());
    }

    @Test
    public void testTwo() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("echo")
                        .from("kamelet:source")
                        .setBody(body().append(body()));

                routeTemplate("reverse")
                        .from("kamelet:source")
                        .setBody(this::reverse);

                from("direct:start").routeId("start")
                    .multicast()
                        .kamelet("echo").end()
                        .kamelet("reverse").end()
                        .end()
                        .to("mock:result");
            }

            private Object reverse(Exchange exchange) {
                StringBuilder sb = new StringBuilder(exchange.getMessage().getBody(String.class));
                sb.reverse();
                return sb.toString();
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("CBA", "FED");

        template.sendBody("direct:start", "ABC");
        template.sendBody("direct:start", "DEF");

        MockEndpoint.assertIsSatisfied(context);

        RouteDefinition rd = context.getRouteDefinition("start");
        MulticastDefinition md = ProcessorDefinitionHelper.findFirstTypeInOutputs(rd.getOutputs(), MulticastDefinition.class);
        Assertions.assertEquals(2, md.getOutputs().size());
    }

}
