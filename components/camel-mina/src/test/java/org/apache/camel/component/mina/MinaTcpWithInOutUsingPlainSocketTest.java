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
package org.apache.camel.component.mina;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.IOHelper;
import org.junit.Test;

/**
 * To test camel-mina component using a TCP client that communicates using TCP socket communication.
 */
public class MinaTcpWithInOutUsingPlainSocketTest extends BaseMinaTest {

    @Test
    public void testSendAndReceiveOnce() throws Exception {
        String response = sendAndReceive("World");

        assertNotNull("Nothing received from Mina", response);
        assertEquals("Hello World", response);
    }

    @Test
    public void testSendAndReceiveTwice() throws Exception {
        String london = sendAndReceive("London");
        String paris = sendAndReceive("Paris");

        assertNotNull("Nothing received from Mina", london);
        assertNotNull("Nothing received from Mina", paris);
        assertEquals("Hello London", london);
        assertEquals("Hello Paris", paris);
    }

    @Test
    public void testReceiveNoResponseSinceOutBodyIsNull() throws Exception {
        String out = sendAndReceive("force-null-out-body");
        assertNull("no data should be recieved", out);
    }

    @Test
    public void testReceiveNoResponseSinceOutBodyIsNullTwice() throws Exception {
        String out = sendAndReceive("force-null-out-body");
        assertNull("no data should be recieved", out);

        out = sendAndReceive("force-null-out-body");
        assertNull("no data should be recieved", out);
    }

    @Test
    public void testExchangeFailedOutShouldBeNull() throws Exception {
        String out = sendAndReceive("force-exception");
        assertTrue("out should not be the same as in when the exchange has failed", !"force-exception".equals(out));
        assertEquals("should get the exception here", out, "java.lang.IllegalArgumentException: Forced exception");
    }

    @Test
    public void testExchangeWithInOnly() throws IOException {
        String out = sendAndReceive("force-set-in-body");
        assertEquals("Get a wrong response message", "Update the in message!", out);
    }

    private String sendAndReceive(String input) throws IOException {
        return sendAndReceive(input, getPort());
    }

    private String sendAndReceive(String input, int port) throws IOException {
        byte buf[] = new byte[128];

        Socket soc = new Socket();
        soc.connect(new InetSocketAddress("localhost", port));

        // Send message using plain Socket to test if this works
        OutputStream os = null;
        InputStream is = null;
        try {
            os = soc.getOutputStream();
            // must append newline at the end to flag end of textline to camel-mina
            os.write((input + LS).getBytes());

            is = soc.getInputStream();
            int len = is.read(buf);
            if (len == -1) {
                // no data received
                return null;
            }
        } finally {
            IOHelper.close(is, os);
            soc.close();
        }

        // convert the buffer to chars
        StringBuilder sb = new StringBuilder();
        for (byte b : buf) {
            char ch = (char) b;
            if (LS.indexOf(ch) > -1) {
                // newline denotes end of text (added in the end in the processor below)
                break;
            } else {
                sb.append(ch);
            }
        }

        return sb.toString();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            public void configure() {
                from(String.format("mina:tcp://localhost:%1$s?textline=true&sync=true", getPort()))
                    .process(e -> {
                        String in = e.getIn().getBody(String.class);
                        if ("force-null-out-body".equals(in)) {
                            // forcing a null out body
                            e.getMessage().setBody(null);
                        } else if ("force-exception".equals(in)) {
                            // clear out before throwing exception
                            e.getMessage().setBody(null);
                            throw new IllegalArgumentException("Forced exception");
                        } else if ("force-set-in-body".equals(in)) {
                            e.getIn().setBody("Update the in message!");
                        } else {
                            e.getMessage().setBody("Hello " + in);
                        }
                    });
            }
        };
    }
}
