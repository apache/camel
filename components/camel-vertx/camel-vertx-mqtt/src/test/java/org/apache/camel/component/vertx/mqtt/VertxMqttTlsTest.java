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
package org.apache.camel.component.vertx.mqtt;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import io.vertx.core.net.PfxOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TLS against a broker on 127.0.0.1. Every refusal has a positive control that differs from it in one thing only.
 */
class VertxMqttTlsTest {

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final String PASSWORD = "changeit";
    // A refused handshake fails the start instead of retrying, so the exchange carries the failure
    private static final String NO_RETRY = "&automaticReconnect=false&connectTimeout=5000";

    private Vertx vertx;
    private TestBroker broker;
    private CamelContext context;
    private ProducerTemplate template;

    @BeforeEach
    void start() {
        vertx = Vertx.vertx();
        context = new DefaultCamelContext();
        context.getRegistry().bind("vertx", vertx);
        context.getRegistry().bind("trustBroker", parameters(false));
        context.getRegistry().bind("trustBrokerWithClientCertificate", parameters(true));
        context.start();
        template = context.createProducerTemplate();
    }

    @AfterEach
    void stop() throws Exception {
        template.close();
        context.stop();
        if (broker != null) {
            broker.close();
        }
        vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    private static String file(String name) {
        return VertxMqttTlsTest.class.getResource("/tls/" + name).getPath();
    }

    private static PfxOptions pfx(String name) {
        return new PfxOptions().setPath(file(name)).setPassword(PASSWORD);
    }

    private static KeyStoreParameters keyStore(String name) {
        final KeyStoreParameters keyStore = new KeyStoreParameters();
        keyStore.setResource("classpath:tls/" + name);
        keyStore.setType("PKCS12");
        keyStore.setPassword(PASSWORD);
        return keyStore;
    }

    private static SSLContextParameters parameters(boolean clientCertificate) {
        final SSLContextParameters parameters = new SSLContextParameters();
        final TrustManagersParameters trust = new TrustManagersParameters();
        trust.setKeyStore(keyStore("client-trust.p12"));
        parameters.setTrustManagers(trust);
        if (clientCertificate) {
            final KeyManagersParameters keys = new KeyManagersParameters();
            keys.setKeyStore(keyStore("client.p12"));
            keys.setKeyPassword(PASSWORD);
            parameters.setKeyManagers(keys);
        }
        return parameters;
    }

    /**
     * @return the failure, from the exchange or from the start of the producer, null if the message was sent
     */
    private Throwable publish(String options) {
        try {
            final Exchange done = template.send("vertx-mqtt:a?brokerUrl=" + broker.url() + NO_RETRY + options,
                    exchange -> exchange.getMessage().setBody("x"));
            return done.getException();
        } catch (RuntimeException e) {
            return e;
        }
    }

    /**
     * A refusal must be the TLS handshake, not a configuration that could not be read.
     */
    private static void assertRefusedByTheHandshake(Throwable failure, String why) {
        assertNotNull(failure, why);
        Throwable cause = failure;
        while (cause.getCause() != null && !(cause instanceof IllegalStateException)) {
            cause = cause.getCause();
        }
        assertTrue(cause.getMessage().startsWith("Cannot connect to the MQTT broker"), cause.toString());
    }

    @Test
    void aTrustedCertificateForTheHostConnects() throws Exception {
        broker = TestBroker.start(vertx, pfx("broker.p12"));
        assertNull(publish("&sslContextParameters=#trustBroker"));
        await().atMost(WAIT).until(() -> !broker.received().isEmpty());
    }

    @Test
    void aTrustedCertificateForAnotherHostIsRefused() throws Exception {
        broker = TestBroker.start(vertx, pfx("broker-other-name.p12"));
        assertRefusedByTheHandshake(publish("&sslContextParameters=#trustBroker"),
                "the certificate names other.example.com, not 127.0.0.1");
        assertTrue(broker.received().isEmpty());
    }

    @Test
    void anUntrustedCertificateIsRefused() throws Exception {
        broker = TestBroker.start(vertx, pfx("broker.p12"));
        assertRefusedByTheHandshake(publish(""), "the trust store of the JVM does not hold the test certificate");
        assertTrue(broker.received().isEmpty());
    }

    @Test
    void aClientCertificateFromTheKeyManagers() throws Exception {
        broker = TestBroker.start(vertx, pfx("broker.p12"), pfx("broker-trust.p12"));
        assertNull(publish("&sslContextParameters=#trustBrokerWithClientCertificate"));
        await().atMost(WAIT).until(() -> !broker.received().isEmpty());
    }

    @Test
    void withoutAClientCertificateTheBrokerRefuses() throws Exception {
        broker = TestBroker.start(vertx, pfx("broker.p12"), pfx("broker-trust.p12"));
        assertRefusedByTheHandshake(publish("&sslContextParameters=#trustBroker"), "no client certificate");
        assertTrue(broker.received().isEmpty());
    }

    @Test
    void theGlobalSslContextParameters() throws Exception {
        broker = TestBroker.start(vertx, pfx("broker.p12"));
        context.setSSLContextParameters(parameters(false));
        context.getComponent(VertxMqttConstants.SCHEME, VertxMqttComponent.class)
                .setUseGlobalSslContextParameters(true);
        assertNull(publish(""));
        await().atMost(WAIT).until(() -> !broker.received().isEmpty());
    }
}
