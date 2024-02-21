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
package org.apache.camel.language.csimple.joor;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class CSimpleTest extends CamelTestSupport {

    @Test
    public void testCSimple() throws Exception {
        getMockEndpoint("mock:high").expectedBodiesReceived("24", "20");
        getMockEndpoint("mock:med").expectedBodiesReceived("9", "6");
        getMockEndpoint("mock:low").expectedBodiesReceived("1", "2");

        template.sendBody("direct:start", 9);
        template.sendBody("direct:start", 1);
        template.sendBody("direct:start", 24);
        template.sendBody("direct:start", 2);
        template.sendBody("direct:start", 6);
        template.sendBody("direct:start", 20);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .choice()
                        .when(csimple("${bodyAs(int)} > 10")).to("mock:high")
                        .when(csimple("${bodyAs(int)} > 5")).to("mock:med")
                        .otherwise().to("mock:low");
            }
        };
    }
}
