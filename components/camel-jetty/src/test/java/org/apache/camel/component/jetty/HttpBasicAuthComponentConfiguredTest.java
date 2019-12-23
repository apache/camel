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
package org.apache.camel.component.jetty;

import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;

import javax.servlet.http.HttpServletRequest;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.HttpComponent;
import org.apache.camel.http.common.HttpConfiguration;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.util.security.Constraint;
import org.junit.Test;

public class HttpBasicAuthComponentConfiguredTest extends BaseJettyTest {

    @BindToRegistry("myAuthHandler")
    public SecurityHandler getSecurityHandler() throws IOException {
        Constraint constraint = new Constraint(Constraint.__BASIC_AUTH, "user");
        constraint.setAuthenticate(true);

        ConstraintMapping cm = new ConstraintMapping();
        cm.setPathSpec("/*");
        cm.setConstraint(constraint);

        ConstraintSecurityHandler sh = new ConstraintSecurityHandler();
        sh.setAuthenticator(new BasicAuthenticator());
        sh.setConstraintMappings(Arrays.asList(new ConstraintMapping[] {cm}));

        HashLoginService loginService = new HashLoginService("MyRealm", "src/test/resources/myRealm.properties");
        sh.setLoginService(loginService);
        sh.setConstraintMappings(Arrays.asList(new ConstraintMapping[] {cm}));

        return sh;
    }

    @Test
    public void testHttpBasicAuth() throws Exception {
        String out = template.requestBody("http://localhost:{{port}}/test", "Hello World", String.class);
        assertEquals("Bye World", out);

        out = template.requestBody("http://localhost:{{port}}/anotherTest", "Hello World", String.class);
        assertEquals("See you later", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                HttpConfiguration config = new HttpConfiguration();
                config.setAuthMethod("Basic");
                config.setAuthUsername("donald");
                config.setAuthPassword("duck");

                HttpComponent http = context.getComponent("http", HttpComponent.class);
                http.setHttpConfiguration(config);

                from("jetty://http://localhost:{{port}}/test?handlers=myAuthHandler").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        HttpServletRequest req = exchange.getIn().getBody(HttpServletRequest.class);
                        assertNotNull(req);
                        Principal user = req.getUserPrincipal();
                        assertNotNull(user);
                        assertEquals("donald", user.getName());
                    }
                }).transform(constant("Bye World"));

                from("jetty://http://localhost:{{port}}/anotherTest?handlers=myAuthHandler").transform(constant("See you later"));
            }
        };
    }
}
