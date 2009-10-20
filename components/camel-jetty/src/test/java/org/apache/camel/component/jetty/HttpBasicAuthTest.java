/**
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

import javax.servlet.http.HttpServletRequest;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.mortbay.jetty.security.Constraint;
import org.mortbay.jetty.security.ConstraintMapping;
import org.mortbay.jetty.security.HashUserRealm;
import org.mortbay.jetty.security.SecurityHandler;

/**
 * @version $Revision$
 */
public class HttpBasicAuthTest extends CamelTestSupport {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myAuthHandler", getSecurityHandler());
        return jndi;
    }

    private SecurityHandler getSecurityHandler() throws IOException {
        Constraint constraint = new Constraint();
        constraint.setName(Constraint.__BASIC_AUTH);

        constraint.setRoles(new String[]{"user"});
        constraint.setAuthenticate(true);

        ConstraintMapping cm = new ConstraintMapping();
        cm.setConstraint(constraint);
        cm.setPathSpec("/*");

        SecurityHandler sh = new SecurityHandler();
        HashUserRealm realm = new HashUserRealm("MyRealm", "src/test/resources/myRealm.properties");
        sh.setUserRealm(realm);
        sh.setConstraintMappings(new ConstraintMapping[]{cm});

        return sh;
    }

    @Test
    public void testHttpBaiscAuth() throws Exception {
        String out = template.requestBody("http://localhost:9080/test?username=donald&password=duck", "Hello World", String.class);
        assertEquals("Bye World", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jetty://http://localhost:9080/test?handlers=myAuthHandler")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            HttpServletRequest req = exchange.getIn().getBody(HttpServletRequest.class);
                            assertNotNull(req);
                            Principal user = req.getUserPrincipal();
                            assertNotNull(user);
                            assertEquals("donald", user.getName());
                        }
                    })
                    .transform(constant("Bye World"));
            }
        };
    }
}
