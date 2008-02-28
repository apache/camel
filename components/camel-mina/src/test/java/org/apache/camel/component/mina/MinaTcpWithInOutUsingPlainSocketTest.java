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
package org.apache.camel.component.mina;

import junit.framework.TestCase;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.IOException;

/**
 * @version $Revision$
 */
public class MinaTcpWithInOutUsingPlainSocketTest extends TestCase {

    protected CamelContext container = new DefaultCamelContext();
    // use parameter sync=true to force InOut pattern of the MinaExchange 
    protected String uri = "mina:tcp://localhost:8888?textline=true&sync=true";

    public void testSendAndReceiveOnce() throws Exception {
        String response = sendAndReceive("World");

        assertNotNull("Nothing received from Mina", response);
        assertEquals("Hello World", response);
    }

    public void testSendAndReceiveTwice() throws Exception {
        String london = sendAndReceive("London");
        String paris = sendAndReceive("Paris");

        assertNotNull("Nothing received from Mina", london);
        assertNotNull("Nothing received from Mina", paris);
        assertEquals("Hello London", london);
        assertEquals("Hello Paris", paris);
    }

    private String sendAndReceive(String input) throws IOException {
        byte buf[] = new byte[128];

        Socket soc = new Socket();
        soc.connect(new InetSocketAddress("localhost", 8888));

        // Send message using plain Socket to test if this works
        OutputStream os = null;
        InputStream is = null;
        try {
            os = soc.getOutputStream();
            // must append newline at the end to flag end of textline to Camel-Mina
            os.write((input + "\n").getBytes());

            is = soc.getInputStream();
            is.read(buf);
        } finally {
            is.close();
            os.close();
            soc.close();
        }

        // convert the buffer to chars
        StringBuffer sb = new StringBuffer();
        for (byte b : buf) {
            char ch = (char) b;
            if (ch == '\n' || b == 0) {
                // newline denotes end of text (added in the end in the processor below)
                break;
            } else {
                sb.append(ch);
            }
        }

        return sb.toString();
    }

    @Override
    protected void setUp() throws Exception {
        container.addRoutes(createRouteBuilder());
        container.start();
    }


    @Override
    protected void tearDown() throws Exception {
        container.stop();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(uri).process(new Processor() {
                    public void process(Exchange e) {
                        String in = e.getIn().getBody(String.class);
                        // append newline at end to denote end of data for textline codec
                        e.getOut().setBody("Hello " + in + "\n");
                    }
                });
            }
        };
    }

}
