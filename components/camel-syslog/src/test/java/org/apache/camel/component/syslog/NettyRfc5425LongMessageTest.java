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

import io.netty.buffer.ByteBuf;
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

public class NettyRfc5425LongMessageTest extends CamelTestSupport {

    private static String uri;
    private static int serverPort;
    private static final String MESSAGE = "<34>1 2003-10-11T22:14:15.003Z mymachine.example.com su - ID47 - "
        + "Lorem ipsum dolor sit amet, tempor democritum vix ad, est partiendo laboramus ei. "
        + "Munere laudem commune vis ad, et qui altera singulis. Ut assum deleniti sit, vix constituto assueverit appellantur at, et meis voluptua usu. "
        + "Quem imperdiet in ius, mei ex dictas mandamus, ut pri tation appetere oportere. Et est harum dictas. \n Omnis quaestio mel te, ex duo autem molestie. "
        + "Ei sed dico minim, nominavi facilisis evertitur quo an, te adipiscing contentiones his. Cum partem deseruisse at, ne iuvaret mediocritatem pro. "
        + "Ex prima utinam convenire usu, volumus legendos nec et, natum putant quo ne. Invidunt necessitatibus at ius, ne eum wisi dicat mediocrem. "
        + "\n Cu usu odio labores sententiae. Ex eos duis singulis necessitatibus, dico omittam vix at. Sit iudico option detracto an, sit no modus exerci oportere. "
        + "Vix dicta munere at, no vis feugiat omnesque convenire. Duo at quod illum dolor, nec amet tantas iisque no, mei quod graece volutpat ea.\n "
        + "Ornatus legendos theophrastus id mei. Cum alia assum abhorreant et, nam indoctum intellegebat ei. Unum constituto quo cu. "
        + "Vero tritani sit ei, ea commodo menandri usu, ponderum hendrerit voluptatibus sed te. "
        + "\n Semper aliquid fabulas ei mel. Vix ei nullam malorum bonorum, movet nemore scaevola cu vel. "
        + "Quo ut esse dictas incorrupte, ex denique splendide nec, mei dicit doming omnium no. Nulla putent nec id, vis vide ignota eligendi in.";

    @BindToRegistry("decoder")
    private Rfc5425FrameDecoder decoder = new Rfc5425FrameDecoder();
    @BindToRegistry("encoder")
    private Rfc5425Encoder encoder = new Rfc5425Encoder();    
    
    @BeforeClass
    public static void initPort() {
        serverPort = AvailablePortFinder.getNextAvailable();
        uri = "netty:tcp://localhost:" + serverPort + "?sync=false&allowDefaultCodec=false&decoders=#decoder&encoders=#encoder";
    }

    @Test
    public void testSendingCamel() throws Exception {

        MockEndpoint mock = getMockEndpoint("mock:syslogReceiver");
        MockEndpoint mock2 = getMockEndpoint("mock:syslogReceiver2");
        mock.expectedMessageCount(1);
        mock2.expectedMessageCount(1);
        mock2.expectedBodiesReceived(MESSAGE);

        template.sendBody("direct:start", MESSAGE.getBytes("UTF8"));

        assertMockEndpointsSatisfied();
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
                // Here we need to turn the request body into ByteBuf
                from("direct:start").convertBodyTo(ByteBuf.class).to(uri);
            }
        };
    }
}
