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
package org.apache.camel.component.netty;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * @version 
 */
public class NettyTcpWithInOutUsingPlainSocketTest extends BaseNettyTest {

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

    private String sendAndReceive(String input) throws IOException {
        byte buf[] = new byte[128];

        Socket soc = new Socket();
        soc.connect(new InetSocketAddress("localhost", getPort()));

        // Send message using plain Socket to test if this works
        OutputStream os = null;
        InputStream is = null;
        try {
            os = soc.getOutputStream();
            // must append the line delimiter
            os.write((input + "\n").getBytes());

            is = soc.getInputStream();
            int len = is.read(buf);
            if (len == -1) {
                // no data received
                return null;
            }
        } finally {
            if (is != null) {
                is.close();
            }
            if (os != null) {
                os.close();
            }
            soc.close();
        }

        // convert the buffer to chars
        StringBuilder sb = new StringBuilder();
        for (byte b : buf) {
            char ch = (char) b;
            if (ch == '\n' || ch == 0) {
                // newline denotes end of text (added in the end in the processor below)
                break;
            } else {
                sb.append(ch);
            }
        }

        return sb.toString();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("netty:tcp://localhost:{{port}}?textline=true&sync=true").process(new Processor() {
                    public void process(Exchange e) {
                        String in = e.getIn().getBody(String.class);
                        if ("force-null-out-body".equals(in)) {
                            // forcing a null out body
                            e.getOut().setBody(null);
                        } else if ("force-exception".equals(in)) {
                            // clear out before throwing exception
                            e.getOut().setBody(null);
                            throw new IllegalArgumentException("Forced exception");
                        } else {
                            e.getOut().setBody("Hello " + in);
                        }
                    }
                });
            }
        };
    }

}
