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
package org.apache.camel.component.ganglia;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import info.ganglia.gmetric4j.xdr.v30x.Ganglia_message;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.acplt.oncrpc.OncRpcException;
import org.acplt.oncrpc.XdrBufferDecodingStream;
import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static info.ganglia.gmetric4j.gmetric.GMetricSlope.NEGATIVE;
import static info.ganglia.gmetric4j.gmetric.GMetricType.FLOAT;
import static org.apache.camel.component.ganglia.GangliaConfiguration.DEFAULT_DMAX;
import static org.apache.camel.component.ganglia.GangliaConfiguration.DEFAULT_METRIC_NAME;
import static org.apache.camel.component.ganglia.GangliaConfiguration.DEFAULT_SLOPE;
import static org.apache.camel.component.ganglia.GangliaConfiguration.DEFAULT_TMAX;
import static org.apache.camel.component.ganglia.GangliaConfiguration.DEFAULT_TYPE;
import static org.apache.camel.component.ganglia.GangliaConfiguration.DEFAULT_UNITS;
import static org.apache.camel.component.ganglia.GangliaConstants.GROUP_NAME;
import static org.apache.camel.component.ganglia.GangliaConstants.METRIC_DMAX;
import static org.apache.camel.component.ganglia.GangliaConstants.METRIC_NAME;
import static org.apache.camel.component.ganglia.GangliaConstants.METRIC_SLOPE;
import static org.apache.camel.component.ganglia.GangliaConstants.METRIC_TMAX;
import static org.apache.camel.component.ganglia.GangliaConstants.METRIC_TYPE;
import static org.apache.camel.component.ganglia.GangliaConstants.METRIC_UNITS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@code GangliaProtocolV30CamelTest} is not shipped with an embedded gmond
 * agent. The gmond agent is mocked with the help of camel-netty codecs and a
 * mock endpoint. As underlying UDP packets are not guaranteed to be delivered,
 * loose assertions are performed.
 */
public class GangliaProtocolV30CamelTest extends CamelGangliaTestSupport {

    @BindToRegistry("protocolV30Decoder")
    private ProtocolV30Decoder protocolV30Decoder = new ProtocolV30Decoder();

    @EndpointInject("mock:gmond")
    private MockEndpoint mockGmond;

    private String getTestUri() {
        return "ganglia:localhost:" + getTestPort() + "?mode=UNICAST&wireFormat31x=false";
    }

    @Test
    public void sendDefaultConfigurationShouldSucceed() throws Exception {

        mockGmond.setMinimumExpectedMessageCount(0);
        mockGmond.setAssertPeriod(100L);
        mockGmond.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Ganglia_message gangliaMessage = exchange.getIn().getBody(Ganglia_message.class);
                assertNotNull(gangliaMessage, "The gmond mock should only receive a non-null ganglia message");
                assertEquals(DEFAULT_METRIC_NAME, gangliaMessage.gmetric.name);
                assertEquals(DEFAULT_TYPE.getGangliaType(), gangliaMessage.gmetric.type);
                assertEquals(DEFAULT_SLOPE.getGangliaSlope(), gangliaMessage.gmetric.slope);
                assertEquals(DEFAULT_UNITS, gangliaMessage.gmetric.units);
                assertEquals(DEFAULT_TMAX, gangliaMessage.gmetric.tmax);
                assertEquals(DEFAULT_DMAX, gangliaMessage.gmetric.dmax);
                assertEquals("28.0", gangliaMessage.gmetric.value);
            }
        });

        template.sendBody(getTestUri(), "28.0");

        mockGmond.assertIsSatisfied();
    }

    @Test
    public void sendMessageHeadersShouldOverrideDefaultConfiguration() throws Exception {

        mockGmond.setMinimumExpectedMessageCount(0);
        mockGmond.setAssertPeriod(100L);
        mockGmond.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Ganglia_message gangliaMessage = exchange.getIn().getBody(Ganglia_message.class);
                assertNotNull(gangliaMessage, "The gmond mock should only receive a non-null ganglia message");
                assertEquals("depth", gangliaMessage.gmetric.name);
                assertEquals("float", gangliaMessage.gmetric.type);
                assertEquals(2, gangliaMessage.gmetric.slope);
                assertEquals("cm", gangliaMessage.gmetric.units);
                assertEquals(100, gangliaMessage.gmetric.tmax);
                assertEquals(10, gangliaMessage.gmetric.dmax);
                assertEquals("-3.0", gangliaMessage.gmetric.value);
            }
        });

        Map<String, Object> headers = new HashMap<>();
        headers.put(GROUP_NAME, "sea-mesure");
        headers.put(METRIC_NAME, "depth");
        headers.put(METRIC_TYPE, FLOAT);
        headers.put(METRIC_SLOPE, NEGATIVE);
        headers.put(METRIC_UNITS, "cm");
        headers.put(METRIC_TMAX, 100);
        headers.put(METRIC_DMAX, 10);
        template.sendBodyAndHeaders(getTestUri(), -3.0f, headers);

        mockGmond.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("netty:udp://localhost:" + getTestPort() + "/?decoders=#protocolV30Decoder").to(mockGmond);
            }
        };
    }

    @Sharable
    public static class ProtocolV30Decoder extends MessageToMessageDecoder<DatagramPacket> {
        @Override
        protected void decode(ChannelHandlerContext ctx, DatagramPacket packet, List<Object> out) throws OncRpcException, IOException {
            byte[] bytes = new byte[packet.content().readableBytes()];
            packet.content().readBytes(bytes);

            // Unmarshall the incoming datagram
            XdrBufferDecodingStream xbds = new XdrBufferDecodingStream(bytes);
            Ganglia_message outMsg = new Ganglia_message();
            xbds.beginDecoding();
            outMsg.xdrDecode(xbds);
            xbds.endDecoding();
            out.add(outMsg);
        }
    }
}
