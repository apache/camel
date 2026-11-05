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

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.exception.LdapEntryAlreadyExistsException;
import org.apache.directory.api.ldap.model.exception.LdapNoSuchObjectException;
import org.apache.directory.api.ldap.model.exception.LdapOperationException;
import org.apache.directory.api.ldap.model.message.ResultCodeEnum;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.ldap.client.api.LdapConnection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The producer closes the connection after each message. A connection bean that is looked up again (not a prototype)
 * must be bound again for the next message, a failed change gives the LDAP result code and the DN, and an empty body
 * gives an empty body. The connection is a fake that behaves like an LDAP connection to a server that refuses anonymous
 * changes, so this test needs no LDAP server.
 */
class LdifConnectionTest extends CamelTestSupport {

    private final FakeConnection fake = new FakeConnection();

    @BindToRegistry("conn")
    private final LdapConnection conn = (LdapConnection) Proxy.newProxyInstance(
            LdapConnection.class.getClassLoader(), new Class<?>[] { LdapConnection.class },
            (proxy, method, args) -> fake.invoke(proxy, method.getName(), args));

    @Test
    void everyMessageRunsWithTheBindOfTheConnection() {
        assertEquals(List.of("success"), send(add("uid=a,dc=example,dc=org")));
        assertEquals(List.of("success"), send(add("uid=b,dc=example,dc=org")));
        assertEquals(List.of("success"), send(delete("uid=a,dc=example,dc=org")));

        assertEquals(Set.of("uid=b,dc=example,dc=org"), fake.entries);
        assertEquals(3, fake.closed, "the connection is closed after each message");
        assertFalse(fake.connected);
    }

    @Test
    void failureStatusHasTheResultCodeAndTheDn() {
        send(add("uid=a,dc=example,dc=org"));

        assertEquals(List.of("entryAlreadyExists (68) on uid=a,dc=example,dc=org"), send(add("uid=a,dc=example,dc=org")));
        assertEquals(List.of("noSuchObject (32) on uid=x,dc=example,dc=org: no such entry"),
                send(delete("uid=x,dc=example,dc=org")));
    }

    @Test
    void emptyBodyGivesEmptyBody() {
        assertEquals("", template.requestBody("direct:ldif", ""));
        assertEquals("", template.requestBody("direct:ldif", (Object) null));
        assertEquals(0, fake.closed, "the connection is not used");
    }

    @Test
    void connectionIsClosedWhenTheChangeFails() {
        Exchange out = template.request("direct:ldif", e -> e.getIn().setBody(add("uid=boom,dc=example,dc=org")));

        assertInstanceOf(IllegalStateException.class, out.getException());
        assertEquals(1, fake.closed);
    }

    @Test
    void missingConnectionBean() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:missing", add("uid=a,dc=example,dc=org")));

        NoSuchBeanException cause = assertInstanceOf(NoSuchBeanException.class, e.getCause());
        assertTrue(cause.getMessage().contains("missing"), cause.getMessage());
    }

    private List<?> send(String ldif) {
        return template.requestBody("direct:ldif", ldif, List.class);
    }

    private static String add(String dn) {
        return "version: 1\n"
               + "dn: " + dn + "\n"
               + "changetype: add\n"
               + "objectClass: top\n"
               + "objectClass: person\n"
               + "cn: test\n"
               + "sn: test\n";
    }

    private static String delete(String dn) {
        return "version: 1\n"
               + "dn: " + dn + "\n"
               + "changetype: delete\n";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:ldif").to("ldif:conn");
                from("direct:missing").to("ldif:missing");
            }
        };
    }

    /**
     * Behaves like LdapNetworkConnection to a server that refuses anonymous changes: the bean starts bound (as made by
     * DefaultLdapConnectionFactory), close() drops the bind, a change on a closed connection reconnects without a bind,
     * and the server sends no message for an entry that already exists.
     */
    private static final class FakeConnection {
        private final Set<String> entries = new HashSet<>();
        private boolean connected = true;
        private boolean authenticated = true;
        private int closed;

        Object invoke(Object proxy, String method, Object[] args) throws Exception {
            switch (method) {
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                case "toString":
                    return "LdapConnection fake";
                case "isConnected":
                    return connected;
                case "isAuthenticated":
                    return connected && authenticated;
                case "bind":
                    connected = true;
                    authenticated = true;
                    return null;
                case "close":
                    closed++;
                    connected = false;
                    authenticated = false;
                    return null;
                case "add":
                    change();
                    String dn = ((Entry) args[0]).getDn().getName();
                    if (dn.startsWith("uid=boom")) {
                        throw new IllegalStateException("boom");
                    }
                    if (!entries.add(dn)) {
                        throw new LdapEntryAlreadyExistsException("");
                    }
                    return null;
                case "delete":
                    change();
                    if (!entries.remove(((Dn) args[0]).getName())) {
                        throw new LdapNoSuchObjectException("no such entry");
                    }
                    return null;
                default:
                    throw new UnsupportedOperationException(method);
            }
        }

        private void change() throws LdapOperationException {
            if (!connected) {
                // the client reconnects on its own, without a bind
                connected = true;
            }
            if (!authenticated) {
                throw new LdapOperationException(ResultCodeEnum.STRONG_AUTH_REQUIRED, "modifications require authentication");
            }
        }
    }
}
