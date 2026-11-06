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
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultMessage;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ldap.AuthenticationException;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.LdapQuery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
class SpringLdapProducerTest extends CamelTestSupport {

    @Mock
    private SpringLdapEndpoint ldapEndpoint;

    @Mock
    private LdapTemplate ldapTemplate;

    private SpringLdapProducer ldapProducer;

    @Override
    protected void doPostSetup() {
        when(ldapEndpoint.getLdapTemplate()).thenReturn(ldapTemplate);
        ldapProducer = new SpringLdapProducer(ldapEndpoint);
    }

    @Test
    public void testEmptyExchange() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        assertThrows(UnsupportedOperationException.class,
                () -> ldapProducer.process(exchange));
    }

    @Test
    public void testWrongBodyType() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        Message in = new DefaultMessage(context);
        in.setBody("");

        exchange.setIn(in);
        assertThrows(UnsupportedOperationException.class,
                () -> ldapProducer.process(exchange));
    }

    @Test
    void testStringBodyNamesTheExpectedMap() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("cn=Alice,ou=people,dc=example,dc=org");

        when(ldapEndpoint.getOperation()).thenReturn(LdapOperation.SEARCH);

        InvalidPayloadException e = assertThrows(InvalidPayloadException.class,
                () -> ldapProducer.process(exchange));
        assertEquals(Map.class, e.getType());
    }

    @Test
    void testNoBodyNamesTheExpectedMap() {
        Exchange exchange = new DefaultExchange(context);

        when(ldapEndpoint.getOperation()).thenReturn(LdapOperation.UNBIND);

        InvalidPayloadException e = assertThrows(InvalidPayloadException.class,
                () -> ldapProducer.process(exchange));
        assertEquals(Map.class, e.getType());
    }

    @Test
    void testFunctionDrivenWithoutFunction() {
        Exchange exchange = new DefaultExchange(context);
        Message in = new DefaultMessage(context);

        Map<String, Object> body = new HashMap<>();
        body.put(SpringLdapProducer.REQUEST, "cn=dn");

        when(ldapEndpoint.getOperation()).thenReturn(LdapOperation.FUNCTION_DRIVEN);

        UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class,
                () -> processBody(exchange, in, body));
        assertTrue(e.getMessage().contains("'function'"), e.getMessage());
    }

    @Test
    public void testNoDN() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        Message in = new DefaultMessage(context);

        Map<String, Object> body = new HashMap<>();

        assertThrows(UnsupportedOperationException.class,
                () -> processBody(exchange, in, body));
    }

    @Test
    void testNoDNForFunctionDrivenOperation() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        Message in = new DefaultMessage(context);

        @SuppressWarnings("unchecked")
        BiFunction<LdapOperations, Object, ?> function = mock(BiFunction.class);

        Map<String, Object> body = new HashMap<>();
        body.put(SpringLdapProducer.FUNCTION, function);

        when(ldapEndpoint.getOperation()).thenReturn(LdapOperation.FUNCTION_DRIVEN);

        processBody(exchange, in, body);
        verify(function).apply(eq(ldapTemplate), isNull());
    }

    private void processBody(Exchange exchange, Message message, Map<String, Object> body) throws Exception {
        message.setBody(body);
        exchange.setIn(message);
        ldapProducer.process(exchange);
    }

    @Test
    public void testEmptyDN() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        Message in = new DefaultMessage(context);

        Map<String, Object> body = new HashMap<>();
        body.put(SpringLdapProducer.DN, "");

        assertThrows(UnsupportedOperationException.class,
                () -> processBody(exchange, in, body));
    }

    @Test
    public void testNullDN() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        Message in = new DefaultMessage(context);

        Map<String, Object> body = new HashMap<>();
        body.put(SpringLdapProducer.DN, null);

        assertThrows(UnsupportedOperationException.class,
                () -> processBody(exchange, in, body));
    }

    @Test
    public void testNullOperation() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        Message in = new DefaultMessage(context);

        Map<String, Object> body = new HashMap<>();
        body.put(SpringLdapProducer.DN, " ");

        assertThrows(UnsupportedOperationException.class,
                () -> processBody(exchange, in, body));
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
        verify(ldapTemplate).search(eq(dn), eq(filter), eq(scope), ArgumentMatchers.<AttributesMapper<String>> any());
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
    void testAuthenticateWithEmptyPassword() {
        Map<String, Object> body = new HashMap<>();
        body.put(SpringLdapProducer.DN, "cn=dn");
        body.put(SpringLdapProducer.FILTER, "filter");
        body.put(SpringLdapProducer.PASSWORD, "");

        assertAuthenticateFailsWithoutCallingLdap(body);
    }

    @Test
    void testAuthenticateWithoutPassword() {
        Map<String, Object> body = new HashMap<>();
        body.put(SpringLdapProducer.DN, "cn=dn");
        body.put(SpringLdapProducer.FILTER, "filter");

        assertAuthenticateFailsWithoutCallingLdap(body);
    }

    private void assertAuthenticateFailsWithoutCallingLdap(Map<String, Object> body) {
        Exchange exchange = new DefaultExchange(context);
        Message in = new DefaultMessage(context);

        when(ldapEndpoint.getOperation()).thenReturn(LdapOperation.AUTHENTICATE);

        AuthenticationException e = assertThrows(AuthenticationException.class,
                () -> processBody(exchange, in, body));
        assertTrue(e.getMessage().contains(SpringLdapProducer.PASSWORD));
        verify(ldapTemplate, never()).authenticate(any(LdapQuery.class), anyString());
    }

    @Test
    public void testModifyAttributes() throws Exception {
        String dn = "cn=dn";
        ModificationItem[] modificationItems
                = new ModificationItem[] { new ModificationItem(DirContext.ADD_ATTRIBUTE, new BasicAttribute("key", "value")) };

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
        body.put(SpringLdapProducer.FUNCTION, (BiFunction<LdapOperations, String, Void>) (l, q) -> {
            l.lookup(q);
            return null;
        });

        when(ldapEndpoint.getOperation()).thenReturn(LdapOperation.FUNCTION_DRIVEN);

        processBody(exchange, in, body);
        verify(ldapTemplate).lookup(eq(dn));
    }

}
