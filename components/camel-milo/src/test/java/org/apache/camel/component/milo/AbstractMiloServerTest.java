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
package org.apache.camel.component.milo;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.function.Consumer;

import org.apache.camel.CamelContext;
import org.apache.camel.component.milo.server.MiloServerComponent;
import org.apache.camel.component.mock.AssertionClause;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;

public abstract class AbstractMiloServerTest extends CamelTestSupport {

    private int serverPort;

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();
        this.serverPort = Ports.pickServerPort();
    }

    public int getServerPort() {
        return this.serverPort;
    }

    /**
     * Replace the port placeholder with the dynamic server port
     * 
     * @param uri the URI to process
     * @return the result, may be {@code null} if the input is {@code null}
     */
    protected String resolve(String uri) {
        if (uri == null) {
            return uri;
        }

        return uri.replace("@@port@@", Integer.toString(this.serverPort));
    }

    public static void testBody(final AssertionClause clause, final Consumer<DataValue> valueConsumer) {
        testBody(clause, DataValue.class, valueConsumer);
    }

    public static <T> void testBody(final AssertionClause clause, final Class<T> bodyClass, final Consumer<T> valueConsumer) {
        clause.predicate(exchange -> {
            final T body = exchange.getIn().getBody(bodyClass);
            valueConsumer.accept(body);
            return true;
        });
    }

    public static Consumer<DataValue> assertGoodValue(final Object expectedValue) {
        return value -> {
            assertNotNull(value);
            assertEquals(expectedValue, value.getValue().getValue());
            assertTrue(value.getStatusCode().isGood());
            assertFalse(value.getStatusCode().isBad());
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = super.createCamelContext();
        configureContext(context);
        return context;
    }

    protected void configureContext(final CamelContext context) throws Exception {
        final MiloServerComponent server = context.getComponent("milo-server", MiloServerComponent.class);
        configureMiloServer(server);
    }

    protected void configureMiloServer(final MiloServerComponent server) throws Exception {
        server.setBindAddresses("localhost");
        server.setBindPort(this.serverPort);
        server.setUserAuthenticationCredentials("foo:bar,foo2:bar2");
    }

    /**
     * Create a default key store for testing
     *
     * @return always returns a key store
     */
    protected KeyStoreLoader.Result loadDefaultTestKey() {
        try {

            final KeyStoreLoader loader = new KeyStoreLoader();
            loader.setUrl("file:src/test/resources/cert/cert.p12");
            loader.setKeyStorePassword("pwd1");
            loader.setKeyPassword("pwd1");
            return loader.load();
        } catch (final GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }

    }

}
