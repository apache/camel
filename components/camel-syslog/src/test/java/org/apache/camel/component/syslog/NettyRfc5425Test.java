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
package org.apache.camel.component.syslog;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.syslog.netty.Rfc5425Encoder;
import org.apache.camel.component.syslog.netty.Rfc5425FrameDecoder;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.BeforeClass;
import org.junit.Test;

public class NettyRfc5425Test extends CamelTestSupport {

    private static String uri;
    private static String uriClient;
    private static int serverPort;
    private final String rfc3164Message = "<165>Aug  4 05:34:00 mymachine myproc[10]: %% It's\n         time to make the do-nuts.  %%  Ingredients: Mix=OK, Jelly=OK #\n"
                                          + "         Devices: Mixer=OK, Jelly_Injector=OK, Frier=OK # Transport:\n" + "         Conveyer1=OK, Conveyer2=OK # %%";
    private final String rfc5424Message = "<34>1 2003-10-11T22:14:15.003Z mymachine.example.com su - ID47 - BOM'su root' failed for lonvick on /dev/pts/8";
    private final String rfc5424WithStructuredData = "<34>1 2003-10-11T22:14:15.003Z mymachine.example.com su - ID47 "
        + "[exampleSDID@32473 iut=\"3\" eventSource=\"Application\" eventID=\"1011\"] BOM'su root' failed for lonvick on /dev/pts/8";

    @BindToRegistry("decoder")
    private Rfc5425FrameDecoder decoder = new Rfc5425FrameDecoder();
    @BindToRegistry("encoder")
    private Rfc5425Encoder encoder = new Rfc5425Encoder(); 
    
    @BeforeClass
    public static void initPort() {
        serverPort = AvailablePortFinder.getNextAvailable();
        uri = "netty:tcp://localhost:" + serverPort + "?sync=false&allowDefaultCodec=false&decoders=#decoder&encoders=#encoder";
        uriClient = uri + "&useByteBuf=true";
    }

    @Test
    public void testSendingCamel() throws Exception {

        MockEndpoint mock = getMockEndpoint("mock:syslogReceiver");
        MockEndpoint mock2 = getMockEndpoint("mock:syslogReceiver2");
        mock.expectedMessageCount(2);
        mock2.expectedMessageCount(2);
        mock2.expectedBodiesReceived(rfc3164Message, rfc5424Message);

        template.sendBody(uriClient, rfc3164Message.getBytes("UTF8"));
        template.sendBody(uriClient, rfc5424Message.getBytes("UTF8"));

        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testStructuredData() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:syslogReceiver");
        mock.expectedMessageCount(1);
        
        template.sendBody("direct:checkStructuredData", rfc5424WithStructuredData);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        context().getRegistry(Registry.class).bind("rfc5426FrameDecoder", new Rfc5425FrameDecoder());

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setTracing(true);
                DataFormat syslogDataFormat = new SyslogDataFormat();

                // we setup a Syslog listener on a random port.
                from(uri).unmarshal(syslogDataFormat).process(new Processor() {
                    @Override
                    public void process(Exchange ex) {
                        assertTrue(ex.getIn().getBody() instanceof SyslogMessage);
                    }
                }).to("mock:syslogReceiver").marshal(syslogDataFormat).to("mock:syslogReceiver2");
                
                
                from("direct:checkStructuredData").unmarshal(syslogDataFormat).process(new Processor() {
                    @Override
                    public void process(Exchange ex) {
                        Object body = ex.getIn().getBody();
                        assertTrue(body instanceof Rfc5424SyslogMessage);
                        assertEquals("[exampleSDID@32473 iut=\"3\" eventSource=\"Application\" eventID=\"1011\"]", ((Rfc5424SyslogMessage)body).getStructuredData());
                    }
                }).to("mock:syslogReceiver");
            }
        };
    }
}
