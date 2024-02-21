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

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.net.URL;
import java.util.List;
import java.util.function.BiConsumer;

import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.util.AttachmentKey;
import io.undertow.util.StatusCodes;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.apache.camel.component.undertow.UndertowComponent;

/**
 * Abstract parent for test verifying that UndertowSecurityProvider.requireServletContext really causes servletContext
 * to be created.
 */
public abstract class AbstractProviderServletTest extends BaseUndertowTest {

    private static final AttachmentKey<String> PRINCIPAL_NAME_KEY = AttachmentKey.create(String.class);

    public abstract static class MockSecurityProvider implements UndertowSecurityProvider {

        @Override
        public void addHeader(BiConsumer<String, Object> consumer, HttpServerExchange httpExchange) {
            String principalName = httpExchange.getAttachment(PRINCIPAL_NAME_KEY);
            consumer.accept(AbstractSecurityProviderTest.PRINCIPAL_PARAMETER, principalName);
        }

        @Override
        public int authenticate(HttpServerExchange httpExchange, List<String> allowedRoles) {
            ServletRequestContext servletRequestContext = httpExchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
            assertServletContext(servletRequestContext);

            //put some information into servletContext
            httpExchange.putAttachment(PRINCIPAL_NAME_KEY, "user");
            return StatusCodes.OK;
        }

        @Override
        public boolean acceptConfiguration(Object configuration, String endpointUri) {
            return true;
        }

        @Override
        public abstract boolean requireServletContext();

        abstract void assertServletContext(ServletRequestContext servletRequestContext);
    }

    static void createSecurtyProviderConfigurationFile(Class<? extends MockSecurityProvider> clazz) throws Exception {
        URL location = MockSecurityProvider.class.getProtectionDomain().getCodeSource().getLocation();
        File file = new File(location.getPath() + "META-INF/services/" + UndertowSecurityProvider.class.getName());
        file.getParentFile().mkdirs();

        Writer output = new FileWriter(file);
        output.write(clazz.getName());
        output.close();

        file.deleteOnExit();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        UndertowComponent component = camelContext.getComponent("undertow", UndertowComponent.class);

        //put mock object asconfiguration, it is not used
        component.setSecurityConfiguration(new Object());
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("undertow:http://localhost:{{port}}/foo?allowedRoles=user")
                        .to("mock:input")
                        .transform(simple("${in.header." + AbstractSecurityProviderTest.PRINCIPAL_PARAMETER + "}"));
            }
        };
    }

}
