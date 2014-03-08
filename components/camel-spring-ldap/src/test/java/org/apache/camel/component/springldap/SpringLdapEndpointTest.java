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
package org.apache.camel.component.springldap;

import javax.naming.directory.SearchControls;

import org.apache.camel.Processor;

import org.junit.Test;

import org.mockito.Mockito;

import org.springframework.ldap.core.LdapTemplate;

import static org.junit.Assert.assertEquals;

public class SpringLdapEndpointTest {
    private LdapTemplate ldapTemplate = Mockito.mock(LdapTemplate.class);

    @Test(expected = UnsupportedOperationException.class)
    public void testCreateConsumer() throws Exception {
        SpringLdapEndpoint endpoint = new SpringLdapEndpoint("some name", ldapTemplate);
        endpoint.createConsumer(Mockito.mock(Processor.class));
    }

    @Test
    public void testOneLevelScope() {
        SpringLdapEndpoint endpoint = new SpringLdapEndpoint("some name", ldapTemplate);

        endpoint.setScope("onelevel");
        assertEquals(SearchControls.ONELEVEL_SCOPE, endpoint.getScope());
    }

    @Test
    public void testObjectScope() {
        SpringLdapEndpoint endpoint = new SpringLdapEndpoint("some name", ldapTemplate);

        endpoint.setScope("object");
        assertEquals(SearchControls.OBJECT_SCOPE, endpoint.getScope());
    }

    @Test
    public void testSubtreeScope() {
        SpringLdapEndpoint endpoint = new SpringLdapEndpoint("some name", ldapTemplate);

        endpoint.setScope("subtree");
        assertEquals(SearchControls.SUBTREE_SCOPE, endpoint.getScope());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testUnsupportedScope() {
        SpringLdapEndpoint endpoint = new SpringLdapEndpoint("some name", ldapTemplate);

        endpoint.setScope("some other scope");
    }

    @Test
    public void testBindOperation() {
        SpringLdapEndpoint endpoint = new SpringLdapEndpoint("some name", ldapTemplate);

        endpoint.setOperation("BinD");
        assertEquals(LdapOperation.BIND, endpoint.getOperation());
    }

    @Test
    public void testSearchOperation() {
        SpringLdapEndpoint endpoint = new SpringLdapEndpoint("some name", ldapTemplate);

        endpoint.setOperation("SeaRCH");
        assertEquals(LdapOperation.SEARCH, endpoint.getOperation());
    }

    @Test
    public void testUnbindOperation() {
        SpringLdapEndpoint endpoint = new SpringLdapEndpoint("some name", ldapTemplate);

        endpoint.setOperation("UnBinD");
        assertEquals(LdapOperation.UNBIND, endpoint.getOperation());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testUnsupportedOperation() {
        SpringLdapEndpoint endpoint = new SpringLdapEndpoint("some name", ldapTemplate);

        endpoint.setOperation("BinDD");
    }
}
