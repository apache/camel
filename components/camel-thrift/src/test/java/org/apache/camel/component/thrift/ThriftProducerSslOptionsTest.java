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

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.thrift.generated.Calculator;
import org.apache.camel.component.thrift.impl.CalculatorSyncServerImpl;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.NamedGroupsParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TSSLTransportFactory;
import org.apache.thrift.transport.TServerSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SSL producer builds its TLS connection from TSSLTransportParameters, which cannot carry the named groups of
 * sslParameters. It says so with a WARN when it starts, instead of dropping them in silence.
 */
public class ThriftProducerSslOptionsTest extends CamelTestSupport {

    private static final String TRUST_STORE_SOURCE = "file:src/test/resources/certs/truststore.jks";
    private static final String KEY_STORE_SOURCE = "file:src/test/resources/certs/keystore.jks";
    private static final String SECURITY_STORE_PASSWORD = "camelinaction";
    private static final int THRIFT_CLIENT_TIMEOUT = 2000;

    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final Logger logger = (Logger) LogManager.getLogger(ThriftProducer.class);
    private final String thread = Thread.currentThread().getName();
    private final AbstractAppender appender
            = new AbstractAppender("ThriftProducerSslOptionsTest", null, null, true, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent event) {
                    // only the events of this test, as other tests may run at the same time
                    if (event.getLevel().isMoreSpecificThan(Level.WARN) && thread.equals(event.getThreadName())) {
                        warnings.add(event.getMessage().getFormattedMessage());
                    }
                }
            };

    private TServerSocket serverTransport;
    private TServer server;
    private int thriftTestPort;

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void doPreSetup() throws Exception {
        // the producer starts with the CamelContext, so capture its log before
        // an appender added while another test still starts log4j would be dropped with the old configuration
        await().atMost(30, TimeUnit.SECONDS).until(logger.getContext()::isStarted);
        appender.start();
        logger.addAppender(appender);

        TSSLTransportFactory.TSSLTransportParameters sslParams = new TSSLTransportFactory.TSSLTransportParameters();
        sslParams.setKeyStore(KEY_STORE_SOURCE, SECURITY_STORE_PASSWORD);
        serverTransport = TSSLTransportFactory.getServerSocket(0, THRIFT_CLIENT_TIMEOUT,
                InetAddress.getByName("localhost"), sslParams);
        thriftTestPort = serverTransport.getServerSocket().getLocalPort();
        TThreadPoolServer.Args args = new TThreadPoolServer.Args(serverTransport);
        args.processor(new Calculator.Processor(new CalculatorSyncServerImpl()));
        server = new TThreadPoolServer(args);
        new Thread(server::serve, "ThriftProducerSslOptionsTest-server").start();
    }

    @Override
    protected void cleanupResources() throws Exception {
        if (server != null) {
            server.stop();
            serverTransport.close();
        }
    }

    @AfterEach
    void stopCapture() {
        logger.removeAppender(appender);
        appender.stop();
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        KeyStoreParameters keyStoreParams = new KeyStoreParameters();
        keyStoreParams.setResource(TRUST_STORE_SOURCE);
        keyStoreParams.setPassword(SECURITY_STORE_PASSWORD);
        TrustManagersParameters trustManagerParams = new TrustManagersParameters();
        trustManagerParams.setKeyStore(keyStoreParams);

        NamedGroupsParameters namedGroups = new NamedGroupsParameters();
        namedGroups.setNamedGroup(List.of("x25519", "secp256r1"));

        SSLContextParameters sslParameters = new SSLContextParameters();
        sslParameters.setTrustManagers(trustManagerParams);
        sslParameters.setNamedGroups(namedGroups);

        registry.bind("sslParams", sslParameters);
    }

    @Test
    public void testNamedGroupsAreReportedOnceAtStart() {
        Object first = template.requestBody("direct:thrift-secured-add", List.of(1, 2));
        Object second = template.requestBody("direct:thrift-secured-add", List.of(3, 4));

        // the producer still connects, with the trust store of sslParameters
        assertEquals(3, first);
        assertEquals(7, second);
        assertEquals(1, warnings.size(), "a single warning: " + warnings);
        assertTrue(warnings.get(0).contains("namedGroups"), warnings.get(0));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:thrift-secured-add")
                        .to("thrift://localhost:" + thriftTestPort
                            + "/org.apache.camel.component.thrift.generated.Calculator?"
                            + "method=add&negotiationType=SSL&sslParameters=#sslParams&synchronous=true");
            }
        };
    }
}
