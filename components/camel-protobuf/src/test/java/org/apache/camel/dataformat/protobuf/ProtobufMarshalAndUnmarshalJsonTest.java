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
package org.apache.camel.dataformat.protobuf;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.protobuf.generated.AddressBookProtos;
import org.apache.camel.dataformat.protobuf.generated.AddressBookProtos.Person;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class ProtobufMarshalAndUnmarshalJsonTest extends CamelTestSupport {
    
    private static final String PERSON_TEST_NAME = "Martin";
    private static final String PERSON_TEST_JSON = "{\"name\": \"Martin\",\"id\": 1234}";
    private static final int PERSON_TEST_ID = 1234;
    
    @Test
    public void testMarshalAndUnmarshal() throws Exception {
        marshalAndUnmarshal("direct:in", "direct:back");
    }
    
    @Test
    public void testMarshalAndUnmarshalWithDSL() throws Exception {
        marshalAndUnmarshal("direct:marshal", "direct:unmarshalA");
    }
    
    private void marshalAndUnmarshal(String inURI, String outURI) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:reverse");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(Person.class);
        
        Object marshalled = template.requestBody(inURI, PERSON_TEST_JSON);

        template.sendBody(outURI, marshalled);

        mock.assertIsSatisfied();

        Person output = mock.getReceivedExchanges().get(0).getIn().getBody(Person.class);
        assertEquals(PERSON_TEST_NAME, output.getName());
        assertEquals(PERSON_TEST_ID, output.getId());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                ProtobufDataFormat format = new ProtobufDataFormat(Person.getDefaultInstance(), ProtobufDataFormat.CONTENT_TYPE_FORMAT_JSON);

                from("direct:in").unmarshal(format).to("mock:reverse");
                from("direct:back").marshal(format);

                from("direct:marshal").unmarshal().protobuf("org.apache.camel.dataformat.protobuf.generated.AddressBookProtos$Person", "json").to("mock:reverse");
                from("direct:unmarshalA").marshal().protobuf();
            }
        };
    }
}
