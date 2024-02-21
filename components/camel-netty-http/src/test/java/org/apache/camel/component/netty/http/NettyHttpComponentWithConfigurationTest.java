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

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class NettyHttpComponentWithConfigurationTest extends CamelTestSupport {

    @Test
    public void testNettyComponentWithConfiguration() throws Exception {
        NettyHttpComponent comp = context.getComponent("netty-http", NettyHttpComponent.class);

        NettyHttpConfiguration cfg = new NettyHttpConfiguration();

        comp.setConfiguration(cfg);
        assertSame(cfg, comp.getConfiguration());

        NettyHttpEndpoint e1 = (NettyHttpEndpoint) comp.createEndpoint("netty-http://http://localhost:4455");
        NettyHttpEndpoint e2
                = (NettyHttpEndpoint) comp.createEndpoint("netty-http://http://localhost:5566?sync=false&needClientAuth=true");

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

        assertEquals(8192, e1.getConfiguration().getMaxHeaderSize());
        assertEquals(4096, e1.getConfiguration().getMaxInitialLineLength());
        assertEquals(8192, e1.getConfiguration().getMaxChunkSize());

        assertEquals(e1.getConfiguration().getMaxHeaderSize(), e2.getConfiguration().getMaxHeaderSize());
        assertEquals(e1.getConfiguration().getMaxInitialLineLength(), e2.getConfiguration().getMaxInitialLineLength());
        assertEquals(e1.getConfiguration().getMaxChunkSize(), e2.getConfiguration().getMaxChunkSize());
    }

    @Test
    public void testNettyComponentWithExplicitConfiguration() throws Exception {
        NettyHttpComponent comp = context.getComponent("netty-http", NettyHttpComponent.class);

        NettyHttpEndpoint e1 = (NettyHttpEndpoint) comp.createEndpoint(
                "netty-http://tcp://localhost:8899?maxHeaderSize=1024&maxInitialLineLength=2048&maxChunkSize=4096");

        e1.getConfiguration().setPort(8899);

        assertEquals(1024, e1.getConfiguration().getMaxHeaderSize());
        assertEquals(2048, e1.getConfiguration().getMaxInitialLineLength());
        assertEquals(4096, e1.getConfiguration().getMaxChunkSize());
    }

}
