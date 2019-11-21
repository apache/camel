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
package org.apache.camel.dataformat.protobuf;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.protobuf.generated.AddressBookProtos.Person;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class ProtobufMarshalAndUnmarshalMapTest extends CamelTestSupport {

    @Test
    public void testMarshalAndUnmarshal() throws Exception {
        marshalAndUnmarshal("direct:in", "direct:back");
    }

    @Test
    public void testMarshalAndUnmarshalWithDSL() throws Exception {
        marshalAndUnmarshal("direct:marshal", "direct:unmarshalA");
    }

    private void marshalAndUnmarshal(String inURI, String outURI) throws Exception {
        final Map<String, Object> input = new HashMap<>();
        final Map<String, Object> phoneNumber = new HashMap<>();
        phoneNumber.put("number", "011122233");
        phoneNumber.put("type", "MOBILE");

        input.put("name", "Martin");
        input.put("id", 1234);
        input.put("phone", Collections.singletonList(phoneNumber));

        MockEndpoint mock = getMockEndpoint("mock:reverse");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(Person.class);

        Object marshalled = template.requestBody(inURI, input);

        template.sendBody(outURI, marshalled);

        mock.assertIsSatisfied();

        Person output = mock.getReceivedExchanges().get(0).getIn().getBody(Person.class);
        assertEquals("Martin", output.getName());
        assertEquals(1234, output.getId());
        assertEquals("011122233", output.getPhone(0).getNumber());
        assertEquals(0, output.getPhone(0).getType().getNumber());

        final Map resultedMap = mock.getReceivedExchanges().get(0).getMessage().getBody(Map.class);
        assertEquals(input, resultedMap);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                ProtobufDataFormat format = new ProtobufDataFormat(Person.getDefaultInstance());

                from("direct:in").marshal(format);
                from("direct:back").unmarshal(format).to("mock:reverse");

                from("direct:marshal").marshal().protobuf("org.apache.camel.dataformat.protobuf.generated.AddressBookProtos$Person");
                from("direct:unmarshalA").unmarshal().protobuf("org.apache.camel.dataformat.protobuf.generated.AddressBookProtos$Person").to("mock:reverse");
            }
        };
    }

}
