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
package org.apache.camel.component.ldap;

import java.util.Collection;
import java.util.Hashtable;
import javax.naming.directory.SearchResult;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.directory.api.util.Network;
import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.core.annotations.ApplyLdifFiles;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(FrameworkRunner.class)
@CreateLdapServer(transports = {@CreateTransport(protocol = "LDAP")})
@ApplyLdifFiles("org/apache/camel/component/ldap/LdapRouteTest.ldif")
public class LdapRouteTwoTest extends AbstractLdapTestUnit {

    private CamelContext camel;
    private ProducerTemplate template;
    private int port;

    @Before
    public void setup() throws Exception {
        // you can assign port number in the @CreateTransport annotation
        port = super.getLdapServer().getPort();

        // use ENV variables
        Hashtable<String, String> env = new Hashtable();
        env.put("java.naming.factory.initial", "com.sun.jndi.ldap.LdapCtxFactory");
        env.put("java.naming.provider.url", Network.ldapLoopbackUrl(ldapServer.getPort()));
        env.put("java.naming.security.principal", "uid=admin,ou=system");
        env.put("java.naming.security.credentials", "secret");
        env.put("java.naming.security.authentication", "simple");

        SimpleRegistry reg = new SimpleRegistry();
        reg.put("localhost:" + port, env);
        camel = new DefaultCamelContext(reg);
        template = camel.createProducerTemplate();
    }

    @After
    public void tearDown() throws Exception {
        camel.stop();
    }
    
    @Test
    public void testLdapRouteStandardTwo() throws Exception {
        camel.addRoutes(createRouteBuilder("ldap:localhost:" + port + "?base=ou=system"));
        camel.start();

        Endpoint endpoint = camel.getEndpoint("direct:start");
        Exchange exchange = endpoint.createExchange();
        // then we set the LDAP filter on the in body
        exchange.getIn().setBody("(!(ou=test1))");

        // now we send the exchange to the endpoint, and receives the response from Camel
        Exchange out = template.send(endpoint, exchange);
        Collection<SearchResult> searchResults = defaultLdapModuleOutAssertions(out);

        assertFalse(contains("uid=test1,ou=test,ou=system", searchResults));
        assertTrue(contains("uid=test2,ou=test,ou=system", searchResults));
        assertTrue(contains("uid=testNoOU,ou=test,ou=system", searchResults));
        assertTrue(contains("uid=tcruise,ou=actors,ou=system", searchResults));

        // call again
        endpoint = camel.getEndpoint("direct:start");
        exchange = endpoint.createExchange();
        // then we set the LDAP filter on the in body
        exchange.getIn().setBody("(!(ou=test1))");

        // now we send the exchange to the endpoint, and receives the response from Camel
        out = template.send(endpoint, exchange);
        searchResults = defaultLdapModuleOutAssertions(out);

        assertFalse(contains("uid=test1,ou=test,ou=system", searchResults));
        assertTrue(contains("uid=test2,ou=test,ou=system", searchResults));
        assertTrue(contains("uid=testNoOU,ou=test,ou=system", searchResults));
        assertTrue(contains("uid=tcruise,ou=actors,ou=system", searchResults));
    }

    @SuppressWarnings("unchecked")
    private Collection<SearchResult> defaultLdapModuleOutAssertions(Exchange out) {
        // assertions of the response
        assertNotNull(out);
        assertNotNull(out.getOut());
        Collection<SearchResult> data = out.getOut().getBody(Collection.class);
        assertNotNull("out body could not be converted to a Collection - was: " + out.getOut().getBody(), data);
        return data;
    }

    protected boolean contains(String dn, Collection<SearchResult> results) {
        for (SearchResult result : results) {
            if (result.getNameInNamespace().equals(dn)) {
                return true;
            }
        }

        return false;
    }

    protected RouteBuilder createRouteBuilder(final String ldapEndpointUrl) throws Exception {
        return new RouteBuilder() {
            // START SNIPPET: route
            public void configure() throws Exception {
                from("direct:start").to(ldapEndpointUrl);
            }
            // END SNIPPET: route
        };
    }
}
