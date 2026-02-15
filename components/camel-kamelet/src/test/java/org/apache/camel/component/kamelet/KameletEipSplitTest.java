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

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class KameletEipSplitTest extends CamelTestSupport {

    @Test
    public void testSplit() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("A@B@C");
        getMockEndpoint("mock:line").expectedBodiesReceived("A", "B", "C");

        template.sendBody("direct:start", "A@B@C");

        MockEndpoint.assertIsSatisfied(context);
    }

    // **********************************************
    //
    // test set-up
    //
    // **********************************************

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("split").templateParameter("myExpression")
                        .from("kamelet:source")
                        .split().simple("{{myExpression}}")
                            .to("mock:line")
                        .end()
                        .to("kamelet:sink");

                from("direct:start")
                        .kamelet("split?myExpression=${body.split('@')}")
                        .to("mock:result");
            }
        };
    }
}
