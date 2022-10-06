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
package org.apache.camel.component.undertow.spi;

import io.undertow.servlet.handlers.ServletRequestContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Test for case that UndertowSecurityProvider.requireServletContext returns false. ServletContext can not be present in
 * httpExchange.
 */
public class ProviderWithoutServletTest extends AbstractProviderServletTest {

    public static class MockSecurityProvider extends AbstractProviderServletTest.MockSecurityProvider {

        @Override
        public boolean requireServletContext() {
            return false;
        }

        @Override
        void assertServletContext(ServletRequestContext servletRequestContext) {
            assertNull(servletRequestContext);
        }
    }

    @BeforeAll
    public static void initProvider() throws Exception {
        createSecurtyProviderConfigurationFile(MockSecurityProvider.class);
    }

    @Test
    public void test() throws Exception {
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_METHOD, "GET");

        String out = template.requestBody("undertow:http://localhost:{{port}}/foo", null, String.class);

        assertEquals("user", out);

        MockEndpoint.assertIsSatisfied(context);
    }

}
