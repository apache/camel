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

package org.apache.camel.itest.osgi.syslog;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.syslog.SyslogDataFormat;
import org.apache.camel.component.syslog.SyslogMessage;
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.apache.camel.spi.DataFormat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;

import static org.ops4j.pax.exam.OptionUtils.combine;


@RunWith(PaxExam.class)
public class SyslogTest extends OSGiIntegrationTestSupport {

    private final int serverPort = 10514;
    private final int messageCount = 1;
    private final String message =
        "<165>Aug  4 05:34:00 mymachine myproc[10]: %% It's\n         time to make the do-nuts.  %%  Ingredients: Mix=OK, Jelly=OK #\n"
            + "         Devices: Mixer=OK, Jelly_Injector=OK, Frier=OK # Transport:\n" + "         Conveyer1=OK, Conveyer2=OK # %%";

    @Test
    public void testSendingRawUDP() throws IOException, InterruptedException {

        MockEndpoint mock = getMockEndpoint("mock:syslogReceiver");
        MockEndpoint mock2 = getMockEndpoint("mock:syslogReceiver2");
        mock.expectedMessageCount(1);
        mock2.expectedMessageCount(1);
        mock2.expectedBodiesReceived(message);

        DatagramSocket socket = new DatagramSocket();
        try {
            InetAddress address = InetAddress.getByName("127.0.0.1");
            for (int i = 0; i < messageCount; i++) {

                byte[] data = message.getBytes();

                DatagramPacket packet = new DatagramPacket(data, data.length, address, serverPort);
                socket.send(packet);
                Thread.sleep(100);
            }
        } finally {
            socket.close();
        }

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {

                //context.setTracing(true);
                DataFormat syslogDataFormat = new SyslogDataFormat();

                // we setup a Syslog  listener on a random port.
                from("mina:udp://127.0.0.1:" + serverPort).unmarshal(syslogDataFormat).process(new Processor() {
                    public void process(Exchange ex) {
                        assertTrue(ex.getIn().getBody() instanceof SyslogMessage);
                    }
                }).to("mock:syslogReceiver").
                    marshal(syslogDataFormat).to("mock:syslogReceiver2");
            }
        };
    }

    @Configuration
    public static Option[] configure() {
        Option[] options = combine(
            getDefaultCamelKarafOptions(),
            // using the features to install the other camel components             
            loadCamelFeatures("camel-mina", "camel-netty", "camel-syslog"));
        
        return options;
    }
    
}
