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
package org.apache.camel.component.bean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.bean.issues.PrivateClasses.HelloCamel;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

import static org.apache.camel.component.bean.issues.PrivateClasses.EXPECTED_OUTPUT;
import static org.apache.camel.component.bean.issues.PrivateClasses.METHOD_NAME;
import static org.apache.camel.component.bean.issues.PrivateClasses.newPackagePrivateHelloCamel;
import static org.apache.camel.component.bean.issues.PrivateClasses.newPrivateHelloCamel;

/**
 * Tests Bean binding for private & package-private classes where the target
 * method is accessible through an interface.
 */
public final class BeanPrivateClassWithInterfaceMethodTest extends ContextTestSupport {

    private static final String INPUT_BODY = "Whatever";
    private final HelloCamel packagePrivateImpl = newPackagePrivateHelloCamel();
    private final HelloCamel privateImpl = newPrivateHelloCamel();

    @Test
    public void testPackagePrivateClassBinding() throws InterruptedException {
        MockEndpoint mockResult = getMockEndpoint("mock:packagePrivateClassResult");
        mockResult.setExpectedMessageCount(1);
        mockResult.message(0).body().isEqualTo(EXPECTED_OUTPUT);

        template.sendBody("direct:testPackagePrivateClass", INPUT_BODY);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testPrivateClassBinding() throws InterruptedException {
        MockEndpoint mockResult = getMockEndpoint("mock:privateClassResult");
        mockResult.setExpectedMessageCount(1);
        mockResult.message(0).body().isEqualTo(EXPECTED_OUTPUT);

        template.sendBody("direct:testPrivateClass", INPUT_BODY);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:testPackagePrivateClass").bean(packagePrivateImpl, METHOD_NAME).to("mock:packagePrivateClassResult");

                from("direct:testPrivateClass").bean(privateImpl, METHOD_NAME).to("mock:privateClassResult");
            }
        };
    }

}
