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
package org.apache.camel.component.ldap;

import java.lang.reflect.Proxy;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchResult;
import javax.naming.spi.InitialContextFactory;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests how the producer gets the DirContext from the registry and closes it, with a fake DirContext.
 */
public class LdapProducerDirContextTest extends CamelTestSupport {

    private static final AtomicInteger CREATED = new AtomicInteger();
    private static final AtomicInteger CLOSED = new AtomicInteger();

    @BeforeEach
    void resetCounters() {
        CREATED.set(0);
        CLOSED.set(0);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("ldap:ldapserver?base=ou=system");
                from("direct:missing").to("ldap:noSuchBean?base=ou=system");
            }
        };
    }

    @Test
    void hashtableBeanCreatesAndClosesContextForEachExchange() {
        context.getRegistry().bind("ldapserver", environment());

        assertEquals(1, search().size());
        assertEquals(1, search().size());

        assertEquals(2, CREATED.get());
        assertEquals(2, CLOSED.get());
    }

    @Test
    void singletonInitialDirContextBeanFailsClearlyAfterItWasClosed() throws Exception {
        context.getRegistry().bind("ldapserver", new InitialDirContext(environment()));

        assertEquals(1, search().size());
        assertEquals(1, CLOSED.get());

        CamelExecutionException e = assertThrows(CamelExecutionException.class, this::search);
        IllegalStateException cause = assertInstanceOf(IllegalStateException.class, e.getCause());
        assertTrue(cause.getMessage().contains("The DirContext bean: ldapserver was closed"), cause.getMessage());
        assertTrue(cause.getMessage().contains("prototype scope"), cause.getMessage());
    }

    @Test
    void dirContextBeanThatIsNotAnInitialContextIsUsedForEachExchange() {
        // a DirContext such as com.sun.jndi.ldap.LdapCtx connects again after it is closed
        context.getRegistry().bind("ldapserver", fakeDirContext());

        assertEquals(1, search().size());
        assertEquals(1, search().size());

        assertEquals(2, CLOSED.get());
    }

    @Test
    void missingBeanFailsWithNoSuchBeanException() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:missing", "(objectClass=*)"));
        NoSuchBeanException cause = assertInstanceOf(NoSuchBeanException.class, e.getCause());
        assertEquals("noSuchBean", cause.getName());
    }

    private List<?> search() {
        return template.requestBody("direct:start", "(objectClass=*)", List.class);
    }

    private static Hashtable<String, String> environment() {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, FakeContextFactory.class.getName());
        return env;
    }

    private static DirContext fakeDirContext() {
        CREATED.incrementAndGet();
        return (DirContext) Proxy.newProxyInstance(LdapProducerDirContextTest.class.getClassLoader(),
                new Class<?>[] { DirContext.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "search":
                            return new ListEnumeration(
                                    List.of(new SearchResult("uid=test", null, new BasicAttributes())));
                        case "close":
                            CLOSED.incrementAndGet();
                            return null;
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        case "toString":
                            return "FakeDirContext";
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    public static class FakeContextFactory implements InitialContextFactory {

        @Override
        public Context getInitialContext(Hashtable<?, ?> environment) {
            return fakeDirContext();
        }
    }

    private static final class ListEnumeration implements NamingEnumeration<SearchResult> {

        private final Iterator<SearchResult> it;

        ListEnumeration(List<SearchResult> list) {
            this.it = list.iterator();
        }

        @Override
        public SearchResult next() {
            return it.next();
        }

        @Override
        public boolean hasMore() {
            return it.hasNext();
        }

        @Override
        public void close() {
            // noop
        }

        @Override
        public boolean hasMoreElements() {
            return it.hasNext();
        }

        @Override
        public SearchResult nextElement() {
            return it.next();
        }
    }
}
