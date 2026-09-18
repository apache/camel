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
package org.apache.camel.language.python3;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@DisabledIfSystemProperty(named = "os.arch", matches = "(?i)(s390x|ppc64le)")
class Python3JavaDslTest extends CamelTestSupport {

    @Test
    void python3SetBodyAndResultType() throws Exception {
        getMockEndpoint("mock:body").expectedBodiesReceived(6);
        getMockEndpoint("mock:typed").expectedBodiesReceived(9);
        getMockEndpoint("mock:typed").message(0).body().isInstanceOf(Integer.class);

        template.sendBody("direct:body", 3);
        template.sendBody("direct:typed", 3);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void python3FilterAndWhen() throws Exception {
        getMockEndpoint("mock:filtered").expectedBodiesReceived(44);
        getMockEndpoint("mock:hello").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:other").expectedBodiesReceived("Bye");

        template.sendBody("direct:filter", 44);
        template.sendBody("direct:filter", 10);
        template.sendBody("direct:choice", "Hello");
        template.sendBody("direct:choice", "Bye");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:body")
                        .setBody().python3("body * 2")
                        .to("mock:body");
                from("direct:typed")
                        .setBody().python3("body * 3", Integer.class)
                        .to("mock:typed");
                from("direct:filter")
                        .filter().python3("body > 20")
                        .to("mock:filtered");
                from("direct:choice")
                        .choice()
                        .when().python3("body == 'Hello'").to("mock:hello")
                        .otherwise().to("mock:other");
            }
        };
    }
}
