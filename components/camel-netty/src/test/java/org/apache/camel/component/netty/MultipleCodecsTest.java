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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class MultipleCodecsTest extends BaseNettyTest {

    @BindToRegistry("length-decoder")
    private ChannelHandlerFactory lengthDecoder = ChannelHandlerFactories.newLengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4);

    @BindToRegistry("string-decoder")
    private StringDecoder stringDecoder = new StringDecoder();

    @BindToRegistry("length-decoder")
    private LengthFieldPrepender lengthEncoder = new LengthFieldPrepender(4);

    @BindToRegistry("string-encoder")
    private StringEncoder stringEncoder = new StringEncoder();

    @BindToRegistry("encoders")
    public List<ChannelHandler> addEncoders() throws Exception {

        List<ChannelHandler> encoders = new ArrayList<>();
        encoders.add(lengthEncoder);
        encoders.add(stringEncoder);

        return encoders;
    }

    @BindToRegistry("decoders")
    public List<ChannelHandler> addDecoders() throws Exception {

        List<ChannelHandler> decoders = new ArrayList<>();
        decoders.add(lengthDecoder);
        decoders.add(stringDecoder);

        return decoders;
    }

    @Test
    public void canSupplyMultipleCodecsToEndpointPipeline() throws Exception {
        String poem = new Poetry().getPoem();
        MockEndpoint mock = getMockEndpoint("mock:multiple-codec");
        mock.expectedBodiesReceived(poem);
        sendBody("direct:multiple-codec", poem);
        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: routes
                from("direct:multiple-codec").to("netty:tcp://localhost:{{port}}?encoders=#encoders&sync=false");

                from("netty:tcp://localhost:{{port}}?decoders=#length-decoder,#string-decoder&sync=false").to("mock:multiple-codec");
                // START SNIPPET: routes
            }
        };
    }
}
