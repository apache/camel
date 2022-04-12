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
package org.apache.camel.component.ldif;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.test.infra.openldap.services.OpenldapService;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.ldap.client.api.DefaultLdapConnectionFactory;
import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.ldap.client.api.LdapConnectionConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class LdifRouteIT extends LdifTestSupport {
    // Constants
    private static final String LDAP_CONN_NAME = "conn";
    private static final String ENDPOINT_LDIF = "ldif:" + LDAP_CONN_NAME;
    private static final String ENDPOINT_START = "direct:start";
    private static final String ENDPOINT_SETUP_START = "direct:setup";
    private static final SearchControls SEARCH_CONTROLS
            = new SearchControls(SearchControls.SUBTREE_SCOPE, 0, 0, null, true, true);

    // Properties
    private CamelContext camel;
    private ProducerTemplate template;
    private LdapContext ldapContext;

    private static LdapContext getWiredContext(OpenldapService ldapServer) throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, "ldap://" + ldapServer.getHost() + ":" + ldapServer.getPort());
        env.put(Context.SECURITY_PRINCIPAL, "cn=admin,dc=example,dc=org");
        env.put(Context.SECURITY_CREDENTIALS, "admin");
        env.put(Context.SECURITY_AUTHENTICATION, "simple");

        return new InitialLdapContext(env, null);
    }

    @BeforeEach
    public void setup() throws Exception {
        // Create the LDAPConnection
        ldapContext = getWiredContext(service);

        SimpleRegistry reg = getSimpleRegistry();
        camel = new DefaultCamelContext(reg);
        template = camel.createProducerTemplate();
    }

    private SimpleRegistry getSimpleRegistry() throws LdapException {
        final LdapConnectionConfig ldapConnectionConfig = new LdapConnectionConfig();
        ldapConnectionConfig.setLdapHost(service.getHost());
        ldapConnectionConfig.setLdapPort(service.getPort());
        ldapConnectionConfig.setName("cn=admin,dc=example,dc=org");
        ldapConnectionConfig.setCredentials("admin");
        ldapConnectionConfig.setUseSsl(false);
        ldapConnectionConfig.setUseTls(false);

        LdapConnection ldapConnection = new DefaultLdapConnectionFactory(ldapConnectionConfig).newLdapConnection();

        SimpleRegistry reg = new SimpleRegistry();
        reg.bind(LDAP_CONN_NAME, ldapConnection);
        return reg;
    }

    @AfterEach
    public void tearDown() {
        if (camel != null) {
            camel.stop();
        }
    }

    @Test
    public void addOne() throws Exception {
        camel.addRoutes(createRouteBuilder(ENDPOINT_LDIF));
        camel.start();

        Endpoint endpoint = camel.getEndpoint(ENDPOINT_START);
        Exchange exchange = endpoint.createExchange();

        // then we set the LDAP filter on the in body
        URL loc = this.getClass().getResource("/org/apache/camel/component/ldif/AddOne.ldif");
        exchange.getIn().setBody(loc.toString());

        // now we send the exchange to the endpoint, and receives the response
        // from Camel
        Exchange out = template.send(endpoint, exchange);

        // Check the results
        List<String> ldifResults = defaultLdapModuleOutAssertions(out);
        assertThat(ldifResults, notNullValue());
        assertThat(ldifResults.size(), equalTo(2)); // Container and user
        assertThat(ldifResults.get(0), equalTo("success"));
        assertThat(ldifResults.get(1), equalTo("success"));

        // Check LDAP
        SearchResult sr;
        NamingEnumeration<SearchResult> searchResults = ldapContext.search("dc=example,dc=org", "(uid=test*)", SEARCH_CONTROLS);
        assertNotNull(searchResults);

        checkDN("uid=test1", searchResults);
    }

    @Test
    public void deleteOne() throws Exception {
        setupData("/org/apache/camel/component/ldif/DeleteOneSetup.ldif");

        camel.addRoutes(createRouteBuilder(ENDPOINT_LDIF));
        camel.start();

        Endpoint endpoint = camel.getEndpoint(ENDPOINT_START);
        Exchange exchange = endpoint.createExchange();

        // then we set the LDAP filter on the in body
        URL loc = this.getClass().getResource("/org/apache/camel/component/ldif/DeleteOne.ldif");
        exchange.getIn().setBody(loc.toString());

        // now we send the exchange to the endpoint, and receives the response
        // from Camel
        Exchange out = template.send(endpoint, exchange);

        // Check the results
        List<String> ldifResults = defaultLdapModuleOutAssertions(out);
        assertThat(ldifResults, notNullValue());
        assertThat(ldifResults.size(), equalTo(1));
        assertThat(ldifResults.get(0), equalTo("success"));

        // Check LDAP
        NamingEnumeration<SearchResult> searchResults = ldapContext.search("dc=example,dc=org", "(uid=test*)", SEARCH_CONTROLS);
        // test2
        while (searchResults.hasMore()) {
            assertThat(searchResults.next().getName(), not(containsString("test2")));
        }
    }

    @Test
    public void addDuplicate() throws Exception {
        setupData("/org/apache/camel/component/ldif/AddDuplicateSetup.ldif");

        camel.addRoutes(createRouteBuilder(ENDPOINT_LDIF));
        camel.start();

        Endpoint endpoint = camel.getEndpoint(ENDPOINT_START);
        Exchange exchange = endpoint.createExchange();

        // then we set the LDAP filter on the in body
        URL loc = this.getClass().getResource("/org/apache/camel/component/ldif/AddDuplicate.ldif");
        exchange.getIn().setBody(loc.toString());

        // now we send the exchange to the endpoint, and receives the response
        // from Camel
        Exchange out = template.send(endpoint, exchange);

        // Check the results
        List<String> ldifResults = defaultLdapModuleOutAssertions(out);
        assertThat(ldifResults, notNullValue());
        assertThat(ldifResults.size(), equalTo(1));
        assertThat(ldifResults.get(0), not(equalTo("success")));
    }

    @Test
    public void modify() throws Exception {
        setupData("/org/apache/camel/component/ldif/ModifySetup.ldif");

        camel.addRoutes(createRouteBuilder(ENDPOINT_LDIF));
        camel.start();

        Endpoint endpoint = camel.getEndpoint(ENDPOINT_START);
        Exchange exchange = endpoint.createExchange();

        // then we set the LDAP filter on the in body
        URL loc = this.getClass().getResource("/org/apache/camel/component/ldif/Modify.ldif");
        exchange.getIn().setBody(loc.toString());

        // now we send the exchange to the endpoint, and receives the response
        // from Camel
        Exchange out = template.send(endpoint, exchange);

        // Check the results
        List<String> ldifResults = defaultLdapModuleOutAssertions(out);
        assertThat(ldifResults, notNullValue());
        assertThat(ldifResults.size(), equalTo(1));
        assertThat(ldifResults.get(0), equalTo("success"));

        // Check LDAP
        SearchResult sr;
        NamingEnumeration<SearchResult> searchResults = ldapContext.search("dc=example,dc=org", "(uid=test*)", SEARCH_CONTROLS);
        assertNotNull(searchResults);

        boolean uidFound = false;
        while (searchResults.hasMore()) {
            sr = searchResults.next();
            if (sr.getName().contains("uid=test4")) {
                uidFound = true;

                // Check the attributes of the search result
                Attributes attribs = sr.getAttributes();
                assertNotNull(attribs);
                Attribute attrib = attribs.get("sn");
                assertNotNull(attribs);
                assertThat(1, equalTo(attrib.size()));
                assertThat("5", equalTo(attrib.get(0).toString()));
            }
        }

        assertThat("uid=test4 not found", uidFound, equalTo(true));
    }

    @Test
    public void modRdn() throws Exception {
        setupData("/org/apache/camel/component/ldif/ModRdnSetup.ldif");

        camel.addRoutes(createRouteBuilder(ENDPOINT_LDIF));
        camel.start();

        Endpoint endpoint = camel.getEndpoint(ENDPOINT_START);
        Exchange exchange = endpoint.createExchange();

        // then we set the LDAP filter on the in body
        URL loc = this.getClass().getResource("/org/apache/camel/component/ldif/ModRdn.ldif");
        exchange.getIn().setBody(loc.toString());

        // now we send the exchange to the endpoint, and receives the response
        // from Camel
        Exchange out = template.send(endpoint, exchange);

        // Check the results
        List<String> ldifResults = defaultLdapModuleOutAssertions(out);
        assertThat(ldifResults, notNullValue());
        assertThat(ldifResults.size(), equalTo(1));
        assertThat(ldifResults.get(0), equalTo("success"));

        // Check LDAP
        NamingEnumeration<SearchResult> searchResults = ldapContext.search("dc=example,dc=org", "(uid=test*)", SEARCH_CONTROLS);
        assertNotNull(searchResults);

        checkDN("uid=test6", searchResults);
    }

    @Test
    public void modDn() throws Exception {
        setupData("/org/apache/camel/component/ldif/ModDnSetup.ldif");

        camel.addRoutes(createRouteBuilder(ENDPOINT_LDIF));
        camel.start();

        Endpoint endpoint = camel.getEndpoint(ENDPOINT_START);
        Exchange exchange = endpoint.createExchange();

        // then we set the LDAP filter on the in body
        URL loc = this.getClass().getResource("/org/apache/camel/component/ldif/ModDn.ldif");
        exchange.getIn().setBody(loc.toString());

        // now we send the exchange to the endpoint, and receives the response
        // from Camel
        Exchange out = template.send(endpoint, exchange);

        // Check the results
        List<String> ldifResults = defaultLdapModuleOutAssertions(out);
        assertThat(ldifResults, notNullValue());
        assertThat(ldifResults.size(), equalTo(1));
        assertThat(ldifResults.get(0), equalTo("success"));

        // Check LDAP
        NamingEnumeration<SearchResult> searchResults = ldapContext.search("dc=example,dc=org", "(uid=test*)", SEARCH_CONTROLS);
        assertNotNull(searchResults);

        checkDN("uid=test7", searchResults);
    }

    @SuppressWarnings("unchecked")
    private List<String> defaultLdapModuleOutAssertions(Exchange out) {
        // assertions of the response
        assertNotNull(out);
        assertNotNull(out.getMessage());
        List<String> data = out.getMessage().getBody(List.class);
        assertNotNull(data, "out body could not be converted to a List - was: " + out.getMessage().getBody());
        return data;
    }

    protected RouteBuilder createRouteBuilder(final String ldapEndpointUrl) {
        return new RouteBuilder() {
            // START SNIPPET: route
            @Override
            public void configure() {
                from(ENDPOINT_START).to(ldapEndpointUrl);
            }
            // END SNIPPET: route
        };
    }

    /**
     * Read the contents of a URL into a String
     *
     * @param  in
     * @return
     * @throws IOException
     */
    private String readUrl(URL in) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(in.openStream()));
        StringBuilder sb = new StringBuilder();
        String s;

        while (null != (s = br.readLine())) {
            sb.append(s);
            sb.append('\n');
        }
        return sb.toString();
    }

    private void setupData(String loc) throws Exception {
        SimpleRegistry reg = getSimpleRegistry();
        CamelContext setupCamel = new DefaultCamelContext(reg);
        ProducerTemplate setupTemplate = setupCamel.createProducerTemplate();

        setupCamel.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(ENDPOINT_SETUP_START).to(ENDPOINT_LDIF);
            }
        });
        setupCamel.start();

        Endpoint endpoint = setupCamel.getEndpoint(ENDPOINT_SETUP_START);
        Exchange exchange = endpoint.createExchange();

        URL setupLoc = this.getClass().getResource(loc);
        exchange.getIn().setBody(readUrl(setupLoc));
        Exchange setupOut = setupTemplate.send(endpoint, exchange);
        List<String> setupResults = defaultLdapModuleOutAssertions(setupOut);

        setupResults.forEach(result -> assertThat(result, anyOf(equalTo("success"), equalTo(""))));

        setupCamel.stop();
    }

    private void checkDN(String dn, NamingEnumeration<SearchResult> searchResults) throws NamingException {
        List<String> resultNames = new ArrayList<>();
        while (searchResults.hasMore()) {
            resultNames.add(searchResults.next().getName());
        }

        assertThat(resultNames, hasItem(containsString(dn)));
    }
}
