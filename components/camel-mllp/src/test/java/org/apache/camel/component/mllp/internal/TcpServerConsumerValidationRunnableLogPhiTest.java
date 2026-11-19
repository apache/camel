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
package org.apache.camel.component.mllp.internal;

import java.lang.reflect.Proxy;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mllp.MllpComponent;
import org.apache.camel.component.mllp.MllpEndpoint;
import org.apache.camel.component.mllp.MllpSocketException;
import org.apache.camel.component.mllp.MllpTcpServerConsumer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.stub.tcp.SocketStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.helpers.MessageFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The initial read of a client connection logs what it received when that is not a complete MLLP envelope. The data is
 * shown only when {@code logPhi=true}, as on the other paths of the component.
 */
public class TcpServerConsumerValidationRunnableLogPhiTest {

    private static final String PAYLOAD = "PID|1||SENTINEL-4711";

    /** How the initial read ends after it received the payload without a start of block. */
    enum InitialRead {
        RETURNS,
        SOCKET_EXCEPTION,
        TIMEOUT
    }

    private final List<String> logged = new CopyOnWriteArrayList<>();
    private CamelContext context;

    @BeforeEach
    public void setUp() {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    public void tearDown() {
        context.stop();
    }

    @ParameterizedTest
    @EnumSource(InitialRead.class)
    public void testDataIsNotLoggedByDefault(InitialRead initialRead) throws Exception {
        String logged = runInitialRead(new MllpComponent(), initialRead);

        assertFalse(logged.contains("SENTINEL-4711"), logged);
        assertTrue(logged.contains(Hl7Util.PHI_SUPPRESSED_REPLACEMENT_VALUE), logged);
    }

    @ParameterizedTest
    @EnumSource(InitialRead.class)
    public void testDataIsLoggedWithLogPhi(InitialRead initialRead) throws Exception {
        MllpComponent component = new MllpComponent();
        component.setLogPhi(true);

        String logged = runInitialRead(component, initialRead);

        assertTrue(logged.contains("SENTINEL-4711"), logged);
    }

    private String runInitialRead(MllpComponent component, InitialRead initialRead) throws Exception {
        context.addComponent("mllp", component);
        MllpEndpoint endpoint = context.getEndpoint("mllp://localhost:0", MllpEndpoint.class);
        MllpTcpServerConsumer consumer = (MllpTcpServerConsumer) endpoint.createConsumer(exchange -> {
        });
        ScriptedBuffer buffer = new ScriptedBuffer(endpoint, initialRead);

        TcpServerConsumerValidationRunnable runnable
                = new TcpServerConsumerValidationRunnable(consumer, new SocketStub(), buffer);
        // the tests of this module run in parallel in one JVM, so the log is captured on the runnable, not in log4j
        runnable.log = capturingLogger();
        runnable.run();

        assertEquals(1, logged.size(), logged.toString());
        assertTrue(buffer.isEmpty(), "the buffer is reset after it was logged");
        return logged.get(0);
    }

    /** A logger that keeps the INFO and WARN messages, formatted, and drops the others. */
    private Logger capturingLogger() {
        return (Logger) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Logger.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.startsWith("is")) {
                        return true;
                    }
                    if (("info".equals(name) || "warn".equals(name)) && args != null && args[0] instanceof String format) {
                        Object[] params = Arrays.copyOfRange(args, 1, args.length);
                        if (params.length == 1 && params[0] instanceof Object[] varargs) {
                            params = varargs;
                        }
                        logged.add(MessageFormatter.arrayFormat(format, params).getMessage());
                    }
                    return null;
                });
    }

    /** A buffer whose read appends the payload, without a start of block, and then ends as the test asks. */
    private static final class ScriptedBuffer extends MllpSocketBuffer {
        private final InitialRead initialRead;

        ScriptedBuffer(MllpEndpoint endpoint, InitialRead initialRead) {
            super(endpoint);
            this.initialRead = initialRead;
        }

        @Override
        public void readFrom(Socket socket, int receiveTimeout, int readTimeout)
                throws MllpSocketException, SocketTimeoutException {
            write(PAYLOAD.getBytes(StandardCharsets.US_ASCII));
            switch (initialRead) {
                case SOCKET_EXCEPTION -> throw new MllpSocketException("connection reset by the test");
                case TIMEOUT -> throw new SocketTimeoutException("read timed out in the test");
                default -> {
                    // returns with the data in the buffer
                }
            }
        }
    }
}
