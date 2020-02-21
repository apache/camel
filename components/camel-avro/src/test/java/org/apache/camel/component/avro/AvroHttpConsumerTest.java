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
import java.net.URL;

import org.apache.avro.ipc.HttpTransceiver;
import org.apache.avro.ipc.reflect.ReflectRequestor;
import org.apache.avro.ipc.specific.SpecificRequestor;
import org.apache.camel.avro.generated.KeyValueProtocol;
import org.apache.camel.avro.test.TestReflection;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.avro.processors.GetProcessor;
import org.apache.camel.component.avro.processors.PutProcessor;
import org.apache.camel.component.avro.processors.ReflectionInOnlyProcessor;
import org.apache.camel.component.avro.processors.ReflectionInOutProcessor;

public class AvroHttpConsumerTest extends AvroConsumerTestSupport {

    @Override
    protected void initializeTranceiver() throws IOException {
        transceiver = new HttpTransceiver(new URL("http://localhost:" + avroPort));
        requestor = new SpecificRequestor(KeyValueProtocol.class, transceiver);

        transceiverMessageInRoute = new HttpTransceiver(new URL("http://localhost:" + avroPortMessageInRoute));
        requestorMessageInRoute = new SpecificRequestor(KeyValueProtocol.class, transceiverMessageInRoute);

        transceiverForWrongMessages = new HttpTransceiver(new URL("http://localhost:" + avroPortForWrongMessages));
        requestorForWrongMessages = new SpecificRequestor(KeyValueProtocol.class, transceiverForWrongMessages);

        reflectTransceiver = new HttpTransceiver(new URL("http://localhost:" + avroPortReflection));
        reflectRequestor = new ReflectRequestor(TestReflection.class, reflectTransceiver);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:exception-handler"));

                //In Only
                from("avro:http:localhost:" + avroPort + "?protocolClassName=org.apache.camel.avro.generated.KeyValueProtocol").choice()
                        .when().simple("${in.headers." + AvroConstants.AVRO_MESSAGE_NAME + "} == 'put'").process(new PutProcessor(keyValue))
                        .when().simple("${in.headers." + AvroConstants.AVRO_MESSAGE_NAME + "} == 'get'").process(new GetProcessor(keyValue));

                from("avro:http:localhost:" + avroPortMessageInRoute + "/put?protocolClassName=org.apache.camel.avro.generated.KeyValueProtocol")
                        .process(new PutProcessor(keyValue));

                from("avro:http:localhost:" + avroPortMessageInRoute + "/get?protocolClassName=org.apache.camel.avro.generated.KeyValueProtocol")
                        .process(new GetProcessor(keyValue));

                from("avro:http:localhost:" + avroPortForWrongMessages + "/put?protocolClassName=org.apache.camel.avro.generated.KeyValueProtocol")
                        .process(new PutProcessor(keyValue));

                from("avro:http:localhost:" + avroPortReflection + "/setName?protocolClassName=org.apache.camel.avro.test.TestReflection&singleParameter=true")
                        .process(new ReflectionInOnlyProcessor(testReflection));

                from("avro:http:localhost:" + avroPortReflection + "/setAge?protocolClassName=org.apache.camel.avro.test.TestReflection")
                        .process(new ReflectionInOnlyProcessor(testReflection));

                from("avro:http:localhost:" + avroPortReflection + "/setTestPojo?protocolClassName=org.apache.camel.avro.test.TestReflection&singleParameter=true")
                        .process(new ReflectionInOnlyProcessor(testReflection));

                from("avro:http:localhost:" + avroPortReflection + "/increaseAge?protocolClassName=org.apache.camel.avro.test.TestReflection&singleParameter=true")
                        .process(new ReflectionInOutProcessor(testReflection));

                from("avro:http:localhost:" + avroPortReflection + "/getTestPojo?protocolClassName=org.apache.camel.avro.test.TestReflection")
                        .process(new ReflectionInOutProcessor(testReflection));
            }
        };
    }
}
