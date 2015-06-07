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
package org.apache.camel.component.netty;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.jboss.netty.handler.timeout.ReadTimeoutException;
import org.junit.Test;

public class NettyCustomCodecRequestTimeoutTest extends BaseNettyTest {

    private String uri = "netty:tcp://localhost:{{port}}?sync=true"
        + "&decoders=#myCustomDecoder,#myCustomDecoder2&encoder=#myCustomEncoder&requestTimeout=1000";

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
    public void testRequestTimeout() throws Exception {
        try {
            template.requestBody("direct:start", data, String.class);
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            ReadTimeoutException cause = assertIsInstanceOf(ReadTimeoutException.class, e.getCause());
            assertNotNull(cause);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to(uri)
                    .to("log:result");

                from(uri)
                    .to("log:before")
                    .delayer(2000)
                    .to("log:after");
            }
        };
    }
}
