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
package org.apache.camel.component.netty;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class NettyUDPAsyncTest extends BaseNettyTest {

    private void sendFile(String uri) throws Exception {
        template.send(uri, new Processor() {
            public void process(Exchange exchange) throws Exception {
                byte[] buffer = exchange.getContext().getTypeConverter().mandatoryConvertTo(byte[].class, new File("src/test/resources/test.txt"));
                exchange.setProperty(Exchange.CHARSET_NAME, "ASCII");
                exchange.getIn().setBody(buffer);
            }
        });
    }

    @Test
    public void testUDPInOnlyWithNettyConsumer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().startsWith("Song Of A Dream".getBytes());

        sendFile("netty:udp://localhost:{{port}}?sync=false&udpByteArrayCodec=true");

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty:udp://localhost:{{port}}?sync=false")
                    .to("mock:result")
                    .to("log:Message");
            }
        };
    }

}
