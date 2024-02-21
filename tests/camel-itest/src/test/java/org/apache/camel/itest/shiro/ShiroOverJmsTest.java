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
package org.apache.camel.itest.shiro;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.shiro.security.ShiroSecurityConstants;
import org.apache.camel.component.shiro.security.ShiroSecurityPolicy;
import org.apache.camel.itest.utils.extensions.JmsServiceExtension;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class ShiroOverJmsTest extends CamelTestSupport {
    @RegisterExtension
    public static JmsServiceExtension jmsServiceExtension = JmsServiceExtension.createExtension();

    private byte[] passPhrase = {
            (byte) 0x08, (byte) 0x09, (byte) 0x0A, (byte) 0x0B,
            (byte) 0x0C, (byte) 0x0D, (byte) 0x0E, (byte) 0x0F,
            (byte) 0x10, (byte) 0x11, (byte) 0x12, (byte) 0x13,
            (byte) 0x14, (byte) 0x15, (byte) 0x16, (byte) 0x17 };

    @Test
    void testShiroOverJms() throws Exception {
        getMockEndpoint("mock:ShiroOverJmsTestError").expectedMessageCount(0);
        getMockEndpoint("mock:ShiroOverJmsTestFoo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:ShiroOverJmsTestResult").expectedBodiesReceived("Bye World");

        Map<String, Object> headers = new HashMap<>();
        headers.put(ShiroSecurityConstants.SHIRO_SECURITY_USERNAME, "ringo");
        headers.put(ShiroSecurityConstants.SHIRO_SECURITY_PASSWORD, "starr");
        template.requestBodyAndHeaders("direct:ShiroOverJmsTestStart", "Hello World", headers);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        // add ActiveMQ with embedded broker
        JmsComponent amq = jmsServiceExtension.getComponent();

        amq.setCamelContext(context);

        registry.bind("jms", amq);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                final ShiroSecurityPolicy securityPolicy
                        = new ShiroSecurityPolicy("src/test/resources/securityconfig.ini", passPhrase);
                securityPolicy.setBase64(true);

                errorHandler(deadLetterChannel("mock:ShiroOverJmsTestError"));

                from("direct:ShiroOverJmsTestStart")
                        .policy(securityPolicy)
                        .to("jms:queue:ShiroOverJmsTestFoo")
                        .to("mock:ShiroOverJmsTestResult");

                from("jms:queue:ShiroOverJmsTestFoo")
                        .to("log:foo?showHeaders=true")
                        .policy(securityPolicy)
                        .to("mock:ShiroOverJmsTestFoo")
                        .transform().constant("Bye World");
            }
        };
    }
}
