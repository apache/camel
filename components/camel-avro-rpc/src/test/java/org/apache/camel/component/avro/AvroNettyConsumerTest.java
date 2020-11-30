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
package org.apache.camel.component.avro;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.apache.avro.ipc.netty.NettyTransceiver;
import org.apache.avro.ipc.reflect.ReflectRequestor;
import org.apache.avro.ipc.specific.SpecificRequestor;
import org.apache.camel.avro.generated.KeyValueProtocol;
import org.apache.camel.avro.test.TestReflection;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.avro.processors.GetProcessor;
import org.apache.camel.component.avro.processors.PutProcessor;
import org.apache.camel.component.avro.processors.ReflectionInOnlyProcessor;
import org.apache.camel.component.avro.processors.ReflectionInOutProcessor;

public class AvroNettyConsumerTest extends AvroConsumerTestSupport {

    @Override
    protected void initializeTranceiver() throws IOException {
        ConsumerRouteType type = getRouteType();

        switch (type) {
            case reflect:
                reflectTransceiver = new NettyTransceiver(new InetSocketAddress("localhost", avroPortReflection));
                reflectRequestor = new ReflectRequestor(TestReflection.class, reflectTransceiver);
                return;
            case specific:
                transceiver = new NettyTransceiver(new InetSocketAddress("localhost", avroPort));
                requestor = new SpecificRequestor(KeyValueProtocol.class, transceiver);
                return;
            case specificProcessor:
                transceiverMessageInRoute = new NettyTransceiver(new InetSocketAddress("localhost", avroPortMessageInRoute));
                requestorMessageInRoute = new SpecificRequestor(KeyValueProtocol.class, transceiverMessageInRoute);
                return;
            case specificProcessorWrong:
                transceiverForWrongMessages
                        = new NettyTransceiver(new InetSocketAddress("localhost", avroPortForWrongMessages));
                requestorForWrongMessages = new SpecificRequestor(KeyValueProtocol.class, transceiverForWrongMessages);
                return;
            default:
                throw new IllegalStateException("Unsupported type of route.");
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                switch (getRouteType()) {
                    case specific:
                        from("avro:netty:localhost:" + avroPort
                             + "?protocolClassName=org.apache.camel.avro.generated.KeyValueProtocol")
                                     .choice()
                                     .when().simple("${in.headers." + AvroConstants.AVRO_MESSAGE_NAME + "} == 'put'")
                                     .process(new PutProcessor(keyValue))
                                     .when().simple("${in.headers." + AvroConstants.AVRO_MESSAGE_NAME + "} == 'get'")
                                     .process(new GetProcessor(keyValue));
                        break;
                    case specificProcessor:
                        from("avro:netty:localhost:" + avroPortMessageInRoute
                             + "/get?protocolClassName=org.apache.camel.avro.generated.KeyValueProtocol")
                                     .process(new GetProcessor(keyValue));

                        from("avro:netty:localhost:" + avroPortMessageInRoute
                             + "/put?protocolClassName=org.apache.camel.avro.generated.KeyValueProtocol")
                                     .process(new PutProcessor(keyValue));

                        break;
                    case specificProcessorWrong:
                        from("avro:netty:localhost:" + avroPortForWrongMessages
                             + "/put?protocolClassName=org.apache.camel.avro.generated.KeyValueProtocol")
                                     .process(new PutProcessor(keyValue));
                        break;
                    case reflect:
                        from("avro:netty:localhost:" + avroPortReflection
                             + "/getTestPojo?protocolClassName=org.apache.camel.avro.test.TestReflection")
                                     .process(new ReflectionInOutProcessor(testReflection));

                        from("avro:netty:localhost:" + avroPortReflection
                             + "/setAge?protocolClassName=org.apache.camel.avro.test.TestReflection")
                                     .process(new ReflectionInOnlyProcessor(testReflection));

                        from("avro:http:localhost:" + avroPortReflection
                             + "/setTestPojo?protocolClassName=org.apache.camel.avro.test.TestReflection&singleParameter=true")
                                     .process(new ReflectionInOnlyProcessor(testReflection));

                        from("avro:http:localhost:" + avroPortReflection
                             + "/increaseAge?protocolClassName=org.apache.camel.avro.test.TestReflection&singleParameter=true")
                                     .process(new ReflectionInOutProcessor(testReflection));

                        from("avro:netty:localhost:" + avroPortReflection
                             + "/setName?protocolClassName=org.apache.camel.avro.test.TestReflection&singleParameter=true")
                                     .process(new ReflectionInOnlyProcessor(testReflection));

                        break;
                    default:
                        throw new IllegalStateException("Unsupported type of route.");
                }

            }
        };
    }
}
