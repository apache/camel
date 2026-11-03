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

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A MINA HL7 listener with a maxFrameLength refuses a longer message and keeps serving other connections.
 */
class HL7MLLPMinaMaxFrameLengthRouteTest extends HL7TestSupport {

    private static final String MESSAGE = "MSH|^~\\&|MYSENDER|MYRECEIVER|MYAPPLICATION||200612211200||QRY^A19|1234|P|2.4";

    @BindToRegistry("hl7codec")
    HL7MLLPCodec addCodec() {
        HL7MLLPCodec codec = new HL7MLLPCodec();
        codec.setCharset("iso-8859-1");
        codec.setMaxFrameLength(1024);
        return codec;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("mina:tcp://127.0.0.1:" + getPort() + "?sync=true&codec=#hl7codec")
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

        String reply = template.requestBody("mina:tcp://127.0.0.1:" + getPort() + "?sync=true&codec=#hl7codec", MESSAGE,
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
