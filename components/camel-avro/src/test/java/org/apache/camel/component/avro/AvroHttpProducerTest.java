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

import java.io.IOException;
import junit.framework.Assert;
import org.apache.avro.Protocol;
import org.apache.avro.ipc.HttpServer;
import org.apache.avro.ipc.Server;
import org.apache.avro.ipc.specific.SpecificResponder;
import org.apache.camel.CamelContext;
import org.apache.camel.avro.generated.Key;
import org.apache.camel.avro.generated.KeyValueProtocol;
import org.apache.camel.avro.generated.Value;
import org.apache.camel.avro.impl.KeyValueProtocolImpl;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AvroHttpProducerTest extends AvroProducerTestSupport {

    static int avroPort = setupFreePort("avroport");

    @Override
    protected void initializeServer() throws IOException {
        if (server == null) {
            server = new HttpServer(new SpecificResponder(KeyValueProtocol.PROTOCOL, keyValue), avroPort);
            server.start();
        }
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                //In Only
                from("direct:in").to("avro:http:localhost:" + avroPort);

                //InOut
                from("direct:inout").to("avro:http:localhost:" + avroPort).to("mock:result-inout");
            }
        };
    }

}
