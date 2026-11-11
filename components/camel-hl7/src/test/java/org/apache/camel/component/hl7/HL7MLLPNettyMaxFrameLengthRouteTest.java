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
package org.apache.camel.component.hl7;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.handler.codec.TooLongFrameException;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ExceptionHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Netty HL7 listener with a maxFrameLength refuses a longer message and keeps serving other connections.
 */
class HL7MLLPNettyMaxFrameLengthRouteTest extends HL7TestSupport {

    private static final String MESSAGE = "MSH|^~\\&|MYSENDER|MYRECEIVER|MYAPPLICATION||200612211200||QRY^A19|1234|P|2.4";

    private final CountDownLatch handled = new CountDownLatch(1);
    private final AtomicReference<Throwable> handledException = new AtomicReference<>();

    @BindToRegistry("hl7decoder")
    HL7MLLPNettyDecoderFactory addDecoder() {
        HL7MLLPNettyDecoderFactory decoder = new HL7MLLPNettyDecoderFactory();
        decoder.setMaxFrameLength(1024);
        return decoder;
    }

    @BindToRegistry("hl7encoder")
    HL7MLLPNettyEncoderFactory addEncoder() {
        return new HL7MLLPNettyEncoderFactory();
    }

    @BindToRegistry("recordingHandler")
    ExceptionHandler addExceptionHandler() {
        return new ExceptionHandler() {
            @Override
            public void handleException(Throwable exception) {
                handleException(null, null, exception);
            }

            @Override
            public void handleException(String message, Throwable exception) {
                handleException(message, null, exception);
            }

            @Override
            public void handleException(String message, Exchange exchange, Throwable exception) {
                handledException.compareAndSet(null, exception);
                handled.countDown();
            }
        };
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("netty:tcp://127.0.0.1:" + getPort()
                     + "?sync=true&decoders=#hl7decoder&encoders=#hl7encoder&exceptionHandler=#recordingHandler")
                        .to("mock:result")
                        .setBody(constant("ACK"));
            }
        };
    }

    @Test
    void messageOverTheLimitClosesItsConnectionAndOtherMessagesAreServed() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(MESSAGE);

        try (Socket socket = new Socket("127.0.0.1", getPort())) {
            socket.setSoTimeout(10000);
            OutputStream out = socket.getOutputStream();
            // a message of 2000 bytes without the end bytes: the listener does not wait for them
            out.write(0x0b);
            out.write("A".repeat(2000).getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            assertEquals(-1, readUntilClosed(socket.getInputStream()));
        }

        assertTrue(handled.await(10, TimeUnit.SECONDS), "the exception handler was not called");
        assertInstanceOf(TooLongFrameException.class, handledException.get());

        String reply = template.requestBody(
                "netty:tcp://127.0.0.1:" + getPort() + "?sync=true&decoders=#hl7decoder&encoders=#hl7encoder", MESSAGE,
                String.class);
        assertEquals("ACK", reply);

        mock.assertIsSatisfied();
    }

    private static int readUntilClosed(InputStream in) throws IOException {
        try {
            return in.read();
        } catch (SocketException e) {
            // connection reset by the listener
            return -1;
        }
    }
}
