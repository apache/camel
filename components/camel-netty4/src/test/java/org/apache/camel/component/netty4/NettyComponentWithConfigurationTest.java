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

import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version 
 */
public class NettyComponentWithConfigurationTest extends CamelTestSupport {

    @Test
    public void testNettyComponentWithConfiguration() throws Exception {
        NettyComponent comp = context.getComponent("netty4", NettyComponent.class);

        NettyConfiguration cfg = new NettyConfiguration();

        comp.setConfiguration(cfg);
        assertSame(cfg, comp.getConfiguration());

        NettyEndpoint e1 = (NettyEndpoint) comp.createEndpoint("netty4://tcp://localhost:4455");
        NettyEndpoint e2 = (NettyEndpoint) comp.createEndpoint("netty4://tcp://localhost:5566?sync=false&needClientAuth=true");

        // should not be same
        assertNotSame(e1, e2);
        assertNotSame(e1.getConfiguration(), e2.getConfiguration());
        
        assertEquals(0, e2.getConfiguration().getReceiveBufferSizePredictor());
        e2.getConfiguration().setReceiveBufferSizePredictor(1024);
        assertEquals(1024, e2.getConfiguration().getReceiveBufferSizePredictor());

        e2.getConfiguration().setPort(5566);

        assertEquals(true, e1.getConfiguration().isSync());
        assertEquals(false, e1.getConfiguration().isNeedClientAuth());
        assertEquals(false, e2.getConfiguration().isSync());
        assertEquals(true, e2.getConfiguration().isNeedClientAuth());
        assertEquals(4455, e1.getConfiguration().getPort());
        assertEquals(5566, e2.getConfiguration().getPort());
    }

    @Test
    public void testNettyComponentUdpWithConfiguration() throws Exception {
        NettyComponent comp = context.getComponent("netty4", NettyComponent.class);

        NettyConfiguration cfg = new NettyConfiguration();

        comp.setConfiguration(cfg);
        assertSame(cfg, comp.getConfiguration());

        NettyEndpoint e1 = (NettyEndpoint) comp.createEndpoint("netty4://udp://localhost:8601?sync=false");
        NettyEndpoint e2 = (NettyEndpoint) comp.createEndpoint("netty4://udp://localhost:8602?sync=false&udpConnectionlessSending=true");

        // should not be same
        assertNotSame(e1, e2);
        assertNotSame(e1.getConfiguration(), e2.getConfiguration());

        // both endpoints are sync=false
        assertEquals(false, e1.getConfiguration().isSync());
        assertEquals(false, e2.getConfiguration().isSync());
        // if not set it should be false
        assertEquals(false, e1.getConfiguration().isUdpConnectionlessSending());
        assertEquals(true, e2.getConfiguration().isUdpConnectionlessSending());

        assertEquals(8601, e1.getConfiguration().getPort());
        assertEquals(8602, e2.getConfiguration().getPort());
    }

}
