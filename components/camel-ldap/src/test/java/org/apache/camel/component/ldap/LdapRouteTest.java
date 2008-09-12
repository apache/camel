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

import javax.naming.directory.SearchResult;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;


public class LdapRouteTest extends LdapTestSupport {

    public void testLdapRoute() throws Exception {
        // START SNIPPET: invoke
        Endpoint endpoint = context.getEndpoint("direct:start");
        Exchange exchange = endpoint.createExchange();
        // then we set the LDAP filter on the in body
        exchange.getIn().setBody("(!(ou=test1))");

        // now we send the exchange to the endpoint, and receives the response from Camel
        Exchange out = template.send(endpoint, exchange);

        // assertions of the response
        assertNotNull(out);
        assertNotNull(out.getOut());
        Collection<SearchResult> data = out.getOut().getBody(Collection.class);
        assertNotNull("out body could not be converted to a Collection - was: " + out.getOut().getBody(), data);

        assertFalse(contains("uid=test1,ou=test,ou=system", data));
        assertTrue(contains("uid=test2,ou=test,ou=system", data));
        assertTrue(contains("uid=testNoOU,ou=test,ou=system", data));
        assertTrue(contains("uid=tcruise,ou=actors,ou=system", data));
        // START SNIPPET: invoke
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        // START SNIPPET: register
        JndiRegistry reg = super.createRegistry();
        reg.bind("localhost:" + port, getWiredContext());
        return reg;
        // END SNIPPET: register
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            // START SNIPPET: route
            public void configure() throws Exception {
                from("direct:start").to("ldap:localhost:" + port + "?base=ou=system");
            }
            // END SNIPPET: route
        };
    }
}
