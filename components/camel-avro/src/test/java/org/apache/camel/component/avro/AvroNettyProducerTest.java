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

package org.apache.camel.component.avro;

import java.net.InetSocketAddress;

import org.apache.avro.ipc.NettyServer;
import org.apache.avro.ipc.specific.SpecificResponder;

import org.apache.camel.avro.generated.KeyValueProtocol;
import org.apache.camel.builder.RouteBuilder;

import org.junit.After;

public class AvroNettyProducerTest extends AvroProducerTestSupport {

    static int avroPort = setupFreePort("avroport");

    @After
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                //In Only
                from("direct:in").to("avro:netty:localhost:" + avroPort);

                //InOut
                from("direct:inout").to("avro:netty:localhost:" + avroPort).to("mock:result-inout");
            }
        };
    }

    @Override
    protected void initializeServer() {
        if (server == null) {
            server = new NettyServer(new SpecificResponder(KeyValueProtocol.PROTOCOL, keyValue), new InetSocketAddress("localhost", avroPort));
            server.start();
        }
    }
}
