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

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.LdapQuery;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SpringLdapProducerTest extends CamelTestSupport {

    @Mock
    private SpringLdapEndpoint ldapEndpoint;

    @Mock
    private LdapTemplate ldapTemplate;

    private SpringLdapProducer ldapProducer;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        when(ldapEndpoint.getLdapTemplate()).thenReturn(ldapTemplate);
        ldapProducer = new SpringLdapProducer(ldapEndpoint);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testEmptyExchange() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        ldapProducer.process(exchange);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testWrongBodyType() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        Message in = new DefaultMessage(context);
        in.setBody("");

        exchange.setIn(in);
        ldapProducer.process(exchange);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testNoDN() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        Message in = new DefaultMessage(context);

        Map<String, Object> body = new HashMap<>();

        processBody(exchange, in, body);
    }

    @Test
    public void testNoDNForFunctionDrivenOperation() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        Message in = new DefaultMessage(context);

        Map<String, Object> body = new HashMap<>();
        body.put(SpringLdapProducer.FUNCTION, mock(BiFunction.class));

        when(ldapEndpoint.getOperation()).thenReturn(LdapOperation.FUNCTION_DRIVEN);

        processBody(exchange, in, body);
    }

    private void processBody(Exchange exchange, Message message, Map<String, Object> body) throws Exception {
        message.setBody(body);
        exchange.setIn(message);
        ldapProducer.process(exchange);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testEmptyDN() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        Message in = new DefaultMessage(context);

        Map<String, Object> body = new HashMap<>();
        body.put(SpringLdapProducer.DN, "");

        processBody(exchange, in, body);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testNullDN() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        Message in = new DefaultMessage(context);

        Map<String, Object> body = new HashMap<>();
        body.put(SpringLdapProducer.DN, null);

        processBody(exchange, in, body);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testNullOperation() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        Message in = new DefaultMessage(context);

        Map<String, Object> body = new HashMap<>();
        body.put(SpringLdapProducer.DN, " ");

        processBody(exchange, in, body);
    }

    @Test
    public void testSearch() throws Exception {
        String dn = "some dn";
        String filter = "filter";
        Integer scope = SearchControls.SUBTREE_SCOPE;

        Exchange exchange = new DefaultExchange(context);
        Message in = new DefaultMessage(context);

        Map<String, Object> body = new HashMap<>();
        body.put(SpringLdapProducer.DN, dn);
        body.put(SpringLdapProducer.FILTER, filter);

        when(ldapEndpoint.getOperation()).thenReturn(LdapOperation.SEARCH);
        when(ldapEndpoint.scopeValue()).thenReturn(scope);

        processBody(exchange, in, body);
        verify(ldapTemplate).search(eq(dn), eq(filter), eq(scope), ArgumentMatchers.<AttributesMapper<String>>any());
    }

    @Test
    public void testBind() throws Exception {
        String dn = "some dn";
        BasicAttributes attributes = new BasicAttributes();

        Exchange exchange = new DefaultExchange(context);
        Message in = new DefaultMessage(context);

        Map<String, Object> body = new HashMap<>();
        body.put(SpringLdapProducer.DN, dn);
        body.put(SpringLdapProducer.ATTRIBUTES, attributes);

        when(ldapEndpoint.getOperation()).thenReturn(LdapOperation.BIND);

        processBody(exchange, in, body);
        verify(ldapTemplate).bind(eq(dn), isNull(), eq(attributes));
    }

    @Test
    public void testUnbind() throws Exception {
        String dn = "some dn";

        Exchange exchange = new DefaultExchange(context);
        Message in = new DefaultMessage(context);

        Map<String, Object> body = new HashMap<>();
        body.put(SpringLdapProducer.DN, dn);

        when(ldapEndpoint.getOperation()).thenReturn(LdapOperation.UNBIND);

        processBody(exchange, in, body);
        verify(ldapTemplate).unbind(eq(dn));
    }

    @Test
    public void testAuthenticate() throws Exception {
        String dn = "cn=dn";
        String filter = "filter";
        String password = "password";

        Exchange exchange = new DefaultExchange(context);
        Message in = new DefaultMessage(context);

        Map<String, Object> body = new HashMap<>();
        body.put(SpringLdapProducer.DN, dn);
        body.put(SpringLdapProducer.FILTER, filter);
        body.put(SpringLdapProducer.PASSWORD, password);

        when(ldapEndpoint.getOperation()).thenReturn(LdapOperation.AUTHENTICATE);

        processBody(exchange, in, body);
        verify(ldapTemplate).authenticate(ArgumentMatchers.any(LdapQuery.class), eq(password));
    }

    @Test
    public void testModifyAttributes() throws Exception {
        String dn = "cn=dn";
        ModificationItem[] modificationItems = new ModificationItem[] {new ModificationItem(DirContext.ADD_ATTRIBUTE, new BasicAttribute("key", "value"))};

        Exchange exchange = new DefaultExchange(context);
        Message in = new DefaultMessage(context);

        Map<String, Object> body = new HashMap<>();
        body.put(SpringLdapProducer.DN, dn);
        body.put(SpringLdapProducer.MODIFICATION_ITEMS, modificationItems);

        when(ldapEndpoint.getOperation()).thenReturn(LdapOperation.MODIFY_ATTRIBUTES);

        processBody(exchange, in, body);
        verify(ldapTemplate).modifyAttributes(eq(dn), eq(modificationItems));
    }

    @Test
    public void testFunctionDriven() throws Exception {
        String dn = "cn=dn";

        Exchange exchange = new DefaultExchange(context);
        Message in = new DefaultMessage(context);

        Map<String, Object> body = new HashMap<>();
        body.put(SpringLdapProducer.DN, dn);
        body.put(SpringLdapProducer.REQUEST, dn);
        body.put(SpringLdapProducer.FUNCTION, (BiFunction<LdapOperations, String, Void>)(l, q) -> {
            l.lookup(q);
            return null;
        });

        when(ldapEndpoint.getOperation()).thenReturn(LdapOperation.FUNCTION_DRIVEN);

        processBody(exchange, in, body);
        verify(ldapTemplate).lookup(eq(dn));
    }

}
