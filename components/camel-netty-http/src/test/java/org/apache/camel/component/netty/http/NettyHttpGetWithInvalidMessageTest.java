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
package org.apache.camel.component.netty.http;

import java.util.ArrayList;
import java.util.List;

import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class NettyHttpGetWithInvalidMessageTest extends CamelTestSupport {
    private static final String REQUEST_STRING = "user: Willem\n"
        + "GET http://localhost:%s/test HTTP/1.1\n" + "another: value\n Host: localhost\n";
    private int port1;

    @BindToRegistry("string-decoder")
    private StringDecoder stringDecoder = new StringDecoder();

    @BindToRegistry("string-encoder")
    private StringEncoder stringEncoder = new StringEncoder();

    @BindToRegistry("encoders")
    public List<ChannelHandler> addEncoders() throws Exception {

        List<ChannelHandler> encoders = new ArrayList<>();
        encoders.add(stringEncoder);

        return encoders;
    }

    @BindToRegistry("decoders")
    public List<ChannelHandler> addDecoders() throws Exception {

        List<ChannelHandler> decoders = new ArrayList<>();
        decoders.add(stringDecoder);

        return decoders;
    }

    @Test
    public void testNettyHttpServer() throws Exception {
        invokeService(port1);
    }

    //@Test
    public void testJettyHttpServer() throws Exception {
        invokeService(port1);
    }

    private void invokeService(int port) {
        Exchange out = template.request("netty:tcp://localhost:" + port + "?encoders=#encoders&decoders=#decoders&sync=true", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(String.format(REQUEST_STRING, port));
            }
        });

        assertNotNull(out);
        String result = out.getMessage().getBody(String.class);
        assertNotNull(result);
        assertTrue("We should get the 404 response.", result.indexOf("404 Not Found") > 0);

    }



    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                port1 = AvailablePortFinder.getNextAvailable();

               // set up a netty http proxy
                from("netty-http:http://localhost:" + port1 + "/test")
                    .transform().simple("Bye ${header.user}.");

            }
        };
    }

}
