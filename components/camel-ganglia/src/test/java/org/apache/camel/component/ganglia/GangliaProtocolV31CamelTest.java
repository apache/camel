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

import info.ganglia.gmetric4j.xdr.v31x.Ganglia_metadata_msg;
import info.ganglia.gmetric4j.xdr.v31x.Ganglia_value_msg;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.acplt.oncrpc.OncRpcException;
import org.acplt.oncrpc.XdrAble;
import org.acplt.oncrpc.XdrBufferDecodingStream;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static info.ganglia.gmetric4j.gmetric.GMetricSlope.NEGATIVE;
import static info.ganglia.gmetric4j.gmetric.GMetricType.FLOAT;
import static info.ganglia.gmetric4j.xdr.v31x.Ganglia_msg_formats.gmetadata_full;
import static info.ganglia.gmetric4j.xdr.v31x.Ganglia_msg_formats.gmetric_string;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@code GangliaProtocolV31CamelTest} is not shipped with an embedded gmond
 * agent. The gmond agent is mocked with the help of camel-netty codecs and a
 * mock endpoint. As underlying UDP packets are not guaranteed to be delivered,
 * loose assertions are performed.
 */
public class GangliaProtocolV31CamelTest extends CamelGangliaTestSupport {

    @BindToRegistry("protocolV31Decoder")
    private ProtocolV31Decoder protocolV31Decoder = new ProtocolV31Decoder();

    @EndpointInject("mock:gmond")
    private MockEndpoint mockGmond;

    private String getTestUri() {
        return "ganglia:localhost:" + getTestPort() + "?mode=UNICAST";
    }

    @Test
    public void sendDefaultConfigurationShouldSucceed() throws Exception {

        mockGmond.setMinimumExpectedMessageCount(0);
        mockGmond.setAssertPeriod(100L);
        mockGmond.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Ganglia_metadata_msg metadataMessage = exchange.getIn().getBody(Ganglia_metadata_msg.class);
                if (metadataMessage != null) {
                    assertEquals(DEFAULT_METRIC_NAME, metadataMessage.gfull.metric.name);
                    assertEquals(DEFAULT_TYPE.getGangliaType(), metadataMessage.gfull.metric.type);
                    assertEquals(DEFAULT_SLOPE.getGangliaSlope(), metadataMessage.gfull.metric.slope);
                    assertEquals(DEFAULT_UNITS, metadataMessage.gfull.metric.units);
                    assertEquals(DEFAULT_TMAX, metadataMessage.gfull.metric.tmax);
                    assertEquals(DEFAULT_DMAX, metadataMessage.gfull.metric.dmax);
                } else {
                    Ganglia_value_msg valueMessage = exchange.getIn().getBody(Ganglia_value_msg.class);
                    if (valueMessage != null) {
                        assertEquals("28.0", valueMessage.gstr.str);
                        assertEquals("%s", valueMessage.gstr.fmt);
                    } else {
                        fail("The gmond mock should only receive non-null metadata or value messages");
                    }
                }
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
                Ganglia_metadata_msg metadataMessage = exchange.getIn().getBody(Ganglia_metadata_msg.class);
                if (metadataMessage != null) {
                    assertEquals("depth", metadataMessage.gfull.metric.name);
                    assertEquals(FLOAT.getGangliaType(), metadataMessage.gfull.metric.type);
                    assertEquals(NEGATIVE.getGangliaSlope(), metadataMessage.gfull.metric.slope);
                    assertEquals("cm", metadataMessage.gfull.metric.units);
                    assertEquals(100, metadataMessage.gfull.metric.tmax);
                    assertEquals(10, metadataMessage.gfull.metric.dmax);
                } else {
                    Ganglia_value_msg valueMessage = exchange.getIn().getBody(Ganglia_value_msg.class);
                    if (valueMessage != null) {
                        assertEquals("-3.0", valueMessage.gstr.str);
                        assertEquals("%s", valueMessage.gstr.fmt);
                    } else {
                        fail("The gmond mock should only receive non-null metadata or value messages");
                    }
                }
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

    @Test
    public void sendWrongMetricTypeShouldThrow() throws Exception {
        assertThrows(CamelExecutionException.class, () -> {
            template.sendBodyAndHeader(getTestUri(), "28.0", METRIC_TYPE, "NotAGMetricType");
        });
    }

    @Test
    public void sendWrongMetricSlopeShouldThrow() throws Exception {
        assertThrows(CamelExecutionException.class, () -> {
            template.sendBodyAndHeader(getTestUri(), "28.0", METRIC_SLOPE, "NotAGMetricSlope");
        });
    }

    @Test
    public void sendWrongMetricTMaxShouldThrow() throws Exception {
        assertThrows(CamelExecutionException.class, () -> {
            template.sendBodyAndHeader(getTestUri(), "28.0", METRIC_TMAX, new Object());
        });
    }

    @Test
    public void sendWrongMetricDMaxShouldThrow() throws Exception {
        assertThrows(CamelExecutionException.class, () -> {
            template.sendBodyAndHeader(getTestUri(), "28.0", METRIC_DMAX, new Object());
        });
    }

    @Test
    public void sendWithWrongTypeShouldThrow() throws Exception {
        assertThrows(ResolveEndpointFailedException.class, () -> {
            template.sendBody(getTestUri() + "&type=wrong", "");
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("netty:udp://localhost:" + getTestPort() + "/?decoders=#protocolV31Decoder").to(mockGmond);
            }
        };
    }

    @Sharable
    public static class ProtocolV31Decoder extends MessageToMessageDecoder<DatagramPacket> {
        @Override
        protected void decode(ChannelHandlerContext ctx, DatagramPacket packet, List<Object> out) throws OncRpcException, IOException {
            byte[] bytes = new byte[packet.content().readableBytes()];
            packet.content().readBytes(bytes);

            // Determine what kind of object the datagram is handling
            XdrBufferDecodingStream xbds = new XdrBufferDecodingStream(bytes);
            xbds.beginDecoding();
            int id = xbds.xdrDecodeInt() & 0xbf;
            xbds.endDecoding();
            XdrAble outMsg = null;
            if (id == gmetadata_full) {
                outMsg = new Ganglia_metadata_msg();
            } else if (id == gmetric_string) {
                outMsg = new Ganglia_value_msg();
            } else {
                fail("During those tests, the gmond mock should only receive metadata or string messages");
            }

            // Unmarshall the incoming datagram
            xbds = new XdrBufferDecodingStream(bytes);
            xbds.beginDecoding();
            outMsg.xdrDecode(xbds);
            xbds.endDecoding();
            out.add(outMsg);
        }
    }
}
