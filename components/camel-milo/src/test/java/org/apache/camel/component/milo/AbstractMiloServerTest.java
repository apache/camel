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
package org.apache.camel.component.milo;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.function.Consumer;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.milo.server.MiloServerComponent;
import org.apache.camel.component.mock.AssertionClause;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.opentest4j.AssertionFailedError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractMiloServerTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractMiloServerTest.class);

    private int serverPort;

    @Override
    protected void doPreSetup() throws Exception {
        this.serverPort = AvailablePortFinder.getNextAvailable();
    }

    public int getServerPort() {
        return this.serverPort;
    }

    protected boolean isAddServer() {
        return true;
    }

    /**
     * Replace the port placeholder with the dynamic server port
     *
     * @param  uri the URI to process
     * @return     the result, may be {@code null} if the input is {@code null}
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

    public static <T> void testBody(
            final AssertionClause clause, final Class<T> bodyClass,
            final Consumer<T> valueConsumer) {
        clause.predicate(exchange -> {
            final T body = exchange.getMessage().getBody(bodyClass);
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
        if (isAddServer()) {
            final MiloServerComponent server = context.getComponent("milo-server", MiloServerComponent.class);
            configureMiloServer(server);
        }
    }

    protected void configureMiloServer(final MiloServerComponent server) throws Exception {
        server.setBindAddresses("localhost");
        server.setPort(this.serverPort);
        server.setUserAuthenticationCredentials("foo:bar,foo2:bar2");
        server.setUsernameSecurityPolicyUri(SecurityPolicy.None);
        server.setSecurityPoliciesById("None");
        server.setEnableAnonymousAuthentication(true);
    }

    /**
     * Create a default key store for testing
     *
     * @return always returns a key store
     */
    protected KeyStoreLoader.Result loadDefaultTestKey() {
        try {

            final KeyStoreLoader loader = new KeyStoreLoader();
            loader.setUrl("file:src/test/resources/keystore");
            loader.setKeyStorePassword("testtest");

            loader.setKeyPassword("test");
            return loader.load();
        } catch (final GeneralSecurityException | IOException e) {
            throw new RuntimeCamelException(e);
        }

    }

    /**
     * Return true, if java version (defined by method getRequiredJavaVersion()) is satisfied. Works for java versions
     * 9+
     */
    boolean isJavaVersionSatisfied(int requiredVersion) {
        String version = System.getProperty("java.version");
        if (!version.startsWith("1.")) {
            int dot = version.indexOf('.');
            if (dot != -1) {
                version = version.substring(0, dot);
            }
            if (version.equalsIgnoreCase("16-ea")) {
                return true;
            } else if (Integer.parseInt(version) >= requiredVersion) {
                return true;
            }
        }
        return false;
    }

    protected Predicate assertPredicate(Consumer<Exchange> consumer) {

        return exchange -> {
            try {
                consumer.accept(exchange);
                return true;
            } catch (AssertionFailedError error) {
                LOG.error("Assertion error: " + error.getMessage(), error);
                return false;
            }
        };
    }

}
