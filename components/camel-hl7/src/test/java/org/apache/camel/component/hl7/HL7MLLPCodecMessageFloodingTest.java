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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import ca.uhn.hl7v2.model.Message;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for the HL7MLLP Codec.
 */
public class HL7MLLPCodecMessageFloodingTest extends HL7TestSupport {

    @BindToRegistry("hl7codec")
    public HL7MLLPCodec addHl7MllpCodec() {
        HL7MLLPCodec codec = new HL7MLLPCodec();
        codec.setCharset("ISO-8859-1");
        codec.setConvertLFtoCR(false);
        return codec;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("mina:tcp://127.0.0.1:" + getPort() + "?sync=true&codec=#hl7codec").unmarshal().hl7().process(exchange -> {
                    Message input = exchange.getIn().getBody(Message.class);
                    Message response = input.generateACK();
                    exchange.getMessage().setBody(response);
                }).delay(50) // simulate some processing time
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testHL7MessageFlood() throws Exception {

        // Write and receive using plain sockets and in different threads
        Socket socket = new Socket("localhost", getPort());
        BufferedOutputStream outputStream = new BufferedOutputStream(new DataOutputStream(socket.getOutputStream()));
        final BufferedInputStream inputStream = new BufferedInputStream(new DataInputStream(socket.getInputStream()));

        int messageCount = 100;
        CountDownLatch latch = new CountDownLatch(messageCount);

        Thread t = new Thread(() -> {
            int response;
            StringBuilder s = new StringBuilder();
            try {
                int i = 0;
                boolean cont = true;
                while (cont && (response = inputStream.read()) >= 0) {
                    if (response == 28) {
                        response = inputStream.read(); // read second end
                                                      // byte
                        if (response == 13) {
                            // Responses must arrive in same order
                            cont = s.toString().contains(String.format("X%dX", i++));
                            s.setLength(0);
                            latch.countDown();
                        }
                    } else {
                        s.append((char) response);
                    }
                }
            } catch (IOException ignored) {
            }
        });
        t.start();

        String in = "MSH|^~\\&|MYSENDER|MYRECEIVER|MYAPPLICATION||200612211200||QRY^A19|X%dX|P|2.4\r"
                    + "QRD|200612211200|R|I|GetPatient|||1^RD|0101701234|DEM||";
        for (int i = 0; i < messageCount; i++) {
            String msg = String.format(in, i);
            outputStream.write(11);
            outputStream.flush();
            outputStream.write(msg.getBytes());
            outputStream.flush();
            outputStream.write(28);
            outputStream.write(13);
            outputStream.flush();
        }

        // Use Awaitility to wait for all messages to be processed,
        // with a generous timeout to avoid flakiness on slow CI machines
        await().atMost(60, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(0, latch.getCount(),
                        "Not all messages were processed"));

        outputStream.close();
        inputStream.close();
        socket.close();
    }

}
