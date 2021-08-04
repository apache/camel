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
package org.apache.camel.builder;

import org.apache.camel.ContextTestSupport;
import org.junit.jupiter.api.Test;

public class RouteTemplateEnvClashTest extends ContextTestSupport {

    @Test
    public void testEnvClash() throws Exception {
        // there is a ENV variable when testing with name: FOO_SERVICE_HOST (see pom.xml)
        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("foo-service-host", "mykamelet")
                .routeId("myRoute")
                .add();

        getMockEndpoint("mock:mykamelet:4444").expectedMessageCount(1);

        template.sendBody("direct:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo-service-host").templateParameter("foo-service-port", "4444")
                        .from("direct:foo")
                        .to("mock:{{foo-service-host}}:{{foo-service-port}}");
            }
        };
    }
}
