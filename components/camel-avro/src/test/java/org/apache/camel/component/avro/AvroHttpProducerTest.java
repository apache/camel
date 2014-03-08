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

import org.apache.avro.ipc.HttpServer;
import org.apache.avro.ipc.reflect.ReflectResponder;
import org.apache.avro.ipc.specific.SpecificResponder;
import org.apache.camel.avro.generated.KeyValueProtocol;
import org.apache.camel.avro.test.TestReflection;
import org.apache.camel.builder.RouteBuilder;

public class AvroHttpProducerTest extends AvroProducerTestSupport {

    @Override
    protected void initializeServer() throws IOException {
        if (server == null) {
            server = new HttpServer(new SpecificResponder(KeyValueProtocol.PROTOCOL, keyValue), avroPort);
            server.start();
        }
        
        if (serverReflection == null) {
            serverReflection = new HttpServer(new ReflectResponder(TestReflection.class, testReflection),
                                              avroPortReflection);
            serverReflection.start();
        }
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                //In Only
                from("direct:in")
                    .to("avro:http:localhost:" + avroPort + "?protocolClassName=org.apache.camel.avro.generated.KeyValueProtocol");
                
                //In Only with message in route
                from("direct:in-message-name")
                    .errorHandler(deadLetterChannel("mock:in-message-name-error"))
                    .to("avro:http:localhost:" + avroPort + "/put?protocolClassName=org.apache.camel.avro.generated.KeyValueProtocol")
                    .to("mock:result-in-message-name");
                
                //In Only with existing interface
                from("direct:in-reflection")
                    .to("avro:http:localhost:" + avroPortReflection + "/setName?protocolClassName=org.apache.camel.avro.test.TestReflection&singleParameter=true");

                //InOut
                from("direct:inout")
                    .to("avro:http:localhost:" + avroPort + "?protocolClassName=org.apache.camel.avro.generated.KeyValueProtocol")
                    .to("mock:result-inout");
                
                //InOut with message in route
                from("direct:inout-message-name")
                    .to("avro:http:localhost:" + avroPort + "/get?protocolClassName=org.apache.camel.avro.generated.KeyValueProtocol")
                    .to("mock:result-inout-message-name");
                
                //InOut with existing interface
                from("direct:inout-reflection")
                    .to("avro:http:localhost:" + avroPortReflection + "/increaseAge?protocolClassName=org.apache.camel.avro.test.TestReflection&singleParameter=true")
                    .to("mock:result-inout-reflection");
            }
        };
    }
}
