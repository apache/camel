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
import java.net.URL;

import org.apache.avro.ipc.HttpTransceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;

import org.apache.camel.avro.generated.KeyValueProtocol;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.avro.processors.GetProcessor;
import org.apache.camel.component.avro.processors.PutProcessor;

public class AvroHttpConsumerTest extends AvroConsumerTestSupport {

    static int avroPort = setupFreePort("avroport");

    @Override
    protected void initializeTranceiver() throws IOException {
        transceiver = new HttpTransceiver(new URL("http://localhost:" + avroPort));
        requestor = new SpecificRequestor(KeyValueProtocol.class, transceiver);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                //In Only
                from("avro:http:localhost:" + avroPort).choice()
                        .when().el("${in.headers." + AvroConstants.AVRO_MESSAGE_NAME + " == 'put'}").process(new PutProcessor(keyValue))
                        .when().el("${in.headers." + AvroConstants.AVRO_MESSAGE_NAME + " == 'get'}").process(new GetProcessor(keyValue));
            }
        };
    }
}
