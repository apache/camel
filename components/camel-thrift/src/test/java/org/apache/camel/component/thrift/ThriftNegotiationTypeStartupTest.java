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
package org.apache.camel.component.thrift;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A negotiation type that the consumer or the producer cannot apply makes it fail to start.
 */
class ThriftNegotiationTypeStartupTest {

    private static final String SERVICE = "org.apache.camel.component.thrift.generated.Calculator";

    @Test
    void sslConsumerRequiresSynchronousMode() throws Exception {
        String message = startFailure(
                "thrift://localhost:0/" + SERVICE + "?negotiationType=SSL&sslParameters=#sslParams", "mock:result");
        assertTrue(message.contains("synchronous"), message);
    }

    @Test
    void saslConsumerIsRejected() throws Exception {
        String message = startFailure(
                "thrift://localhost:0/" + SERVICE + "?negotiationType=SASL&synchronous=true", "mock:result");
        assertTrue(message.contains("SASL"), message);
    }

    @Test
    void saslProducerIsRejected() throws Exception {
        String message = startFailure("direct:start",
                "thrift://localhost:1101/" + SERVICE + "?method=ping&negotiationType=SASL&synchronous=true");
        assertTrue(message.contains("SASL"), message);
    }

    private static String startFailure(String from, String to) throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("sslParams", sslParameters());
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from(from).to(to);
                }
            });

            Exception e = assertThrows(Exception.class, context::start);
            Throwable cause = e;
            while (cause != null && !(cause instanceof IllegalArgumentException)) {
                cause = cause.getCause();
            }
            assertNotNull(cause, "Expected an IllegalArgumentException, got: " + e);
            return cause.getMessage();
        }
    }

    private static SSLContextParameters sslParameters() {
        KeyStoreParameters keyStore = new KeyStoreParameters();
        keyStore.setResource("file:src/test/resources/certs/keystore.jks");
        keyStore.setPassword("camelinaction");
        KeyManagersParameters keyManagers = new KeyManagersParameters();
        keyManagers.setKeyStore(keyStore);
        keyManagers.setKeyPassword("camelinaction");
        SSLContextParameters sslParameters = new SSLContextParameters();
        sslParameters.setKeyManagers(keyManagers);
        return sslParameters;
    }
}
