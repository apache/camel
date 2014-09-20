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
package org.apache.camel.component.netty4;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.util.ObjectHelper;
import org.junit.Test;

public class NettyCustomCodecTest extends BaseNettyTest {

    private String uri = "netty4:tcp://localhost:{{port}}?disconnect=true&sync=false"
        + "&allowDefaultCodec=false&decoders=#myCustomDecoder,#myCustomDecoder2&encoder=#myCustomEncoder";

    // use reaadble bytes
    private byte[] data = new byte[]{65, 66, 67, 68, 69, 70, 71, 72, 73, 0, 0};

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myCustomDecoder", MyCustomCodec.createMyCustomDecoder());
        jndi.bind("myCustomDecoder2", MyCustomCodec.createMyCustomDecoder2());
        jndi.bind("myCustomEncoder", MyCustomCodec.createMyCustomEncoder());
        return jndi;
    }

    @Test
    public void testCustomCodec() throws Exception {
        getMockEndpoint("mock:input").expectedMessageCount(1);

        template.sendBody(uri, data);

        assertMockEndpointsSatisfied();

        byte[] mockData = getMockEndpoint("mock:input").getReceivedExchanges().get(0).getIn().getBody(byte[].class);
        ObjectHelper.equalByteArray(data, mockData);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(uri)
                    .to("log:input")
                    .to("mock:input");
            }
        };
    }
}
