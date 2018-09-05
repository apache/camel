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
package org.apache.camel.component.netty4.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests https://issues.apache.org/jira/browse/CAMEL-10409
 */
public class NettyRequestManagementTest extends BaseNettyTest {

    @Test
    public void testBufferManagement() {
        Exchange exchange = template.send("direct:start", e -> e.getIn().setBody("World"));
        Assert.assertEquals("Bye World", exchange.getIn().getBody(String.class));
        exchange.getProperty("buffer", ByteBuf.class).release();
    }

    private static void requestBuffer(Exchange exchange) {
        exchange.setProperty("buffer", PooledByteBufAllocator.DEFAULT.directBuffer());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty4-http:http://0.0.0.0:{{port}}/foo")
                        .transform(body().prepend("Bye "));

                from("direct:start")
                        .to("netty4-http:http://localhost:{{port}}/foo?synchronous=true")
                        .process(NettyRequestManagementTest::requestBuffer);
            }
        };
    }
}
