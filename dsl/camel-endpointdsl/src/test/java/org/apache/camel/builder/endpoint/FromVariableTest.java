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
package org.apache.camel.builder.endpoint;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class FromVariableTest extends BaseEndpointDslTest {

    @Test
    public void testOriginalBody() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("Bye ");
        getMockEndpoint("mock:result").expectedBodiesReceived("World");

        template.sendBody("direct:start", "World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new EndpointRouteBuilder() {
            public void configure() throws Exception {
                fromV(direct("start").advanced().synchronous(false), "myKey")
                        .transform().simple("Bye ${body}")
                        .to("mock:foo")
                        .setBody(simple("${variable:myKey}"))
                        .to("mock:result");
            }
        };
    }

}
