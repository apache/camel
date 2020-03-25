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
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.StatusCodes;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.apache.camel.component.undertow.UndertowComponent;
import org.junit.BeforeClass;

/**
 * Abstract parent for test involving securityProvider.
 */
public abstract class  AbstractSecurityProviderTest extends BaseUndertowTest {

    static final String PRINCIPAL_PARAMETER = "principal_parameter";

    MockSecurityConfiguration securityConfiguration;

    public static final class MockSecurityProvider implements UndertowSecurityProvider {

        private static final AttachmentKey<String> PRINCIPAL_KEY = AttachmentKey.create(String.class);

        private MockSecurityConfiguration configuration;

        @Override
        public void addHeader(BiConsumer<String, Object> consumer, HttpServerExchange httpExchange) throws Exception {
            String principal = httpExchange.getAttachment(PRINCIPAL_KEY);
            if (principal != null) {
                consumer.accept(PRINCIPAL_PARAMETER, principal);
            }
        }

        @Override
        public int authenticate(HttpServerExchange httpExchange, List<String> allowedRoles) throws Exception {
            if (configuration.getRoleToAssign() != null && allowedRoles != null && allowedRoles.contains(configuration.getRoleToAssign())) {
                httpExchange.putAttachment(PRINCIPAL_KEY, configuration.getRoleToAssign());
                return StatusCodes.OK;
            }
            return StatusCodes.FORBIDDEN;
        }

        @Override
        public boolean acceptConfiguration(Object configuration, String endpointUri) throws Exception {
            if (configuration instanceof MockSecurityConfiguration) {
                this.configuration = (MockSecurityConfiguration) configuration;
                return this.configuration.isAccept();
            }
            return false;
        }

        @Override
        public HttpHandler wrapHttpHandler(HttpHandler httpHandler) throws Exception {
            if (configuration.getWrapHttpHandler() != null) {
                return configuration.getWrapHttpHandler().apply(httpHandler);
            }
            return httpHandler;
        }
    }

    public static final class MockSecurityConfiguration {

        private String roleToAssign;

        private boolean accept = true;

        private Function<HttpHandler, HttpHandler> wrapHttpHandler;

        public String getRoleToAssign() {
            return roleToAssign;
        }

        public void setRoleToAssign(String roleToAssign) {
            this.roleToAssign = roleToAssign;
        }

        public boolean isAccept() {
            return accept;
        }

        public void setAccept(boolean accept) {
            this.accept = accept;
        }

        public Function<HttpHandler, HttpHandler> getWrapHttpHandler() {
            return wrapHttpHandler;
        }

        public void setWrapHttpHandler(Function<HttpHandler, HttpHandler> wrapHttpHandler) {
            this.wrapHttpHandler = wrapHttpHandler;
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext =  super.createCamelContext();
        UndertowComponent component = camelContext.getComponent("undertow", UndertowComponent.class);

        securityConfiguration = new MockSecurityConfiguration();
        component.setSecurityConfiguration(securityConfiguration);
        return camelContext;
    }

    @BeforeClass
    public static void createSecurtyProviderConfigurationFile() throws Exception {
        URL location = MockSecurityProvider.class.getProtectionDomain().getCodeSource().getLocation();
        File file = new File(location.getPath() + "META-INF/services/" + UndertowSecurityProvider.class.getName());
        file.getParentFile().mkdirs();

        Writer output = new FileWriter(file);
        output.write(MockSecurityProvider.class.getName());
        output.close();

        file.deleteOnExit();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("undertow:http://localhost:{{port}}/foo?allowedRoles=user")
                        .to("mock:input")
                        .transform(simple("${in.header." + PRINCIPAL_PARAMETER + "}"));
            }
        };
    }

}
