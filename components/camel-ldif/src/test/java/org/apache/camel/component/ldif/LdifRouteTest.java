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
import java.util.List;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.core.annotations.ApplyLdifFiles;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.apache.directory.server.integ.ServerIntegrationUtils.getWiredConnection;
import static org.apache.directory.server.integ.ServerIntegrationUtils.getWiredContext;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

@RunWith(FrameworkRunner.class)
@CreateLdapServer(transports = {@CreateTransport(protocol = "LDAP")})
public class LdifRouteTest extends AbstractLdapTestUnit {
    // Constants
    private static final String LDAP_CONN_NAME = "conn";
    private static final String ENDPOINT_LDIF = "ldif:" + LDAP_CONN_NAME;
    private static final String ENDPOINT_START = "direct:start";
    private static final SearchControls SEARCH_CONTROLS = new SearchControls(SearchControls.SUBTREE_SCOPE, 0, 0, null, true, true);

    // Properties
    private CamelContext camel;
    private ProducerTemplate template;
    private LdapContext ldapContext;
    
    @Before
    public void setup() throws Exception {
        // Create the LDAPConnection
        ldapContext = getWiredContext(ldapServer);

        SimpleRegistry reg = new SimpleRegistry();
        reg.bind(LDAP_CONN_NAME, getWiredConnection(ldapServer));
        camel = new DefaultCamelContext(reg);
        template = camel.createProducerTemplate();
    }

    @After
    public void tearDown() throws Exception {
        camel.stop();
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
        NamingEnumeration<SearchResult> searchResults = ldapContext.search("", "(uid=test*)", SEARCH_CONTROLS);
        assertNotNull(searchResults);
        sr = searchResults.next();
        assertNotNull(sr);
        assertThat("uid=test1,ou=test,ou=system", equalTo(sr.getName()));
        assertThat(false, equalTo(searchResults.hasMore()));
    }
    
    @Test
    public void addOneInline() throws Exception {
        camel.addRoutes(createRouteBuilder(ENDPOINT_LDIF));
        camel.start();

        Endpoint endpoint = camel.getEndpoint(ENDPOINT_START);
        Exchange exchange = endpoint.createExchange();

        // then we set the LDAP filter on the in body
        URL loc = this.getClass().getResource("/org/apache/camel/component/ldif/AddOne.ldif");
        exchange.getIn().setBody(readUrl(loc));

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
        NamingEnumeration<SearchResult> searchResults = ldapContext.search("", "(uid=test*)", SEARCH_CONTROLS);
        assertNotNull(searchResults);
        sr = searchResults.next();
        assertNotNull(sr);
        assertThat("uid=test1,ou=test,ou=system", equalTo(sr.getName()));
        assertThat(false, equalTo(searchResults.hasMore()));
    }


    @Test
    @ApplyLdifFiles({"org/apache/camel/component/ldif/DeleteOneSetup.ldif"})
    public void deleteOne() throws Exception {
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
        NamingEnumeration<SearchResult> searchResults = ldapContext.search("", "(uid=test*)", SEARCH_CONTROLS);
        assertThat(false, equalTo(searchResults.hasMore()));
    }

    @Test
    @ApplyLdifFiles({"org/apache/camel/component/ldif/AddDuplicateSetup.ldif"})
    public void addDuplicate() throws Exception {
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
    @ApplyLdifFiles({"org/apache/camel/component/ldif/ModifySetup.ldif"})
    public void modify() throws Exception {
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
        NamingEnumeration<SearchResult> searchResults = ldapContext.search("", "(uid=test*)", SEARCH_CONTROLS);
        assertNotNull(searchResults);
        sr = searchResults.next();
        assertNotNull(sr);
        assertThat("uid=test4,ou=test,ou=system", equalTo(sr.getName()));

        // Check the attributes of the search result
        Attributes attribs = sr.getAttributes();
        assertNotNull(attribs);
        Attribute attrib = attribs.get("sn");
        assertNotNull(attribs);
        assertThat(1, equalTo(attrib.size()));
        assertThat("5", equalTo(attrib.get(0).toString()));

        // Check no more results
        assertThat(false, equalTo(searchResults.hasMore()));
    }

    @Test
    @ApplyLdifFiles({"org/apache/camel/component/ldif/ModRdnSetup.ldif"})
    public void modRdn() throws Exception {
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
        SearchResult sr;
        NamingEnumeration<SearchResult> searchResults = ldapContext.search("", "(uid=test*)", SEARCH_CONTROLS);
        assertNotNull(searchResults);
        sr = searchResults.next();
        assertNotNull(sr);

        // Check the DN
        assertThat("uid=test6,ou=test,ou=system", equalTo(sr.getName()));

        // Check no more results
        assertThat(false, equalTo(searchResults.hasMore()));
    }

    @Test
    @ApplyLdifFiles({"org/apache/camel/component/ldif/ModDnSetup.ldif"})
    public void modDn() throws Exception {
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
        SearchResult sr;
        NamingEnumeration<SearchResult> searchResults = ldapContext.search("", "(uid=test*)", SEARCH_CONTROLS);
        assertNotNull(searchResults);
        sr = searchResults.next();
        assertNotNull(sr);

        // Check the DN
        assertThat("uid=test7,ou=testnew,ou=system", equalTo(sr.getName()));

        // Check no more results
        assertThat(false, equalTo(searchResults.hasMore()));
    }

    @SuppressWarnings("unchecked")
    private List<String> defaultLdapModuleOutAssertions(Exchange out) {
        // assertions of the response
        assertNotNull(out);
        assertNotNull(out.getMessage());
        List<String> data = out.getMessage().getBody(List.class);
        assertNotNull("out body could not be converted to a List - was: " + out.getMessage().getBody(), data);
        return data;
    }

    protected RouteBuilder createRouteBuilder(final String ldapEndpointUrl) throws Exception {
        return new RouteBuilder() {
            // START SNIPPET: route
            @Override
            public void configure() throws Exception {
                from(ENDPOINT_START).to(ldapEndpointUrl);
            }
            // END SNIPPET: route
        };
    }
    
    /**
     * Read the contents of a URL into a String
     * @param in
     * @return
     * @throws IOException
     */
    private String readUrl(URL in) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(in.openStream()));
        StringBuffer buf = new StringBuffer();
        String s;
        
        while (null != (s = br.readLine())) {
            buf.append(s);
            buf.append('\n');
        }
        return buf.toString();
    }
}
