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

import java.util.List;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

/**
 * Test that using variable receive in kamelets does not cause original message body to be lost after calling the
 * kamelet.
 */
public class KameletVariableReceiveTest extends CamelTestSupport {

    private final Object body = List.of("1", "2");

    @Test
    public void testVariableReceive() throws Exception {
        getMockEndpoint("mock:line").expectedMessageCount(1);
        getMockEndpoint("mock:line").message(0).variable("myVar").isEqualTo("[1, 2]");
        getMockEndpoint("mock:result").expectedBodiesReceived(body);

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // this kamelet only manipulates variables
                routeTemplate("demo")
                        .fromV("kamelet:source", "myVar")
                        .convertVariableTo("myVar", String.class)
                        .to("mock:line")
                        .removeVariable("myVar");

                from("direct:start").routeId("test")
                        .setBody(constant(body))
                        .to("kamelet:demo")
                        .log("${body}")
                        .to("mock:result");
            }
        };
    }
}
