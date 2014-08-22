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
package org.apache.camel.itest.osgi.protobuf;

import org.apache.camel.CamelException;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.protobuf.ProtobufDataFormat;
import org.apache.camel.dataformat.protobuf.generated.AddressBookProtos;
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;

import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
public class ProtobufRouteTest extends OSGiIntegrationTestSupport {

    @Test
    public void testMarshalAndUnmarshalWithDataFormat() throws Exception {
        marshalAndUnmarshal("direct:in", "direct:back");
    }
    
    @Test
    public void testMarshalAndUnmarshalWithDSL1() throws Exception {
        marshalAndUnmarshal("direct:marshal", "direct:unmarshalA");
    }
    
    @Test
    public void testMarshalAndUnmarshalWithDSL2() throws Exception {
        marshalAndUnmarshal("direct:marshal", "direct:unmarshalB");
    }
    
    @Test
    public void testMarshalAndUnmashalWithDSL3() throws Exception {
        try {
            context.addRoutes(new RouteBuilder() {
                public void configure() throws Exception {
                    from("direct:unmarshalC").unmarshal().protobuf(new CamelException("wrong instance"))
                        .to("mock:reverse");
                }
            });
            fail("Expect the exception here");
        } catch (Exception ex) {
            assertTrue("Expect FailedToCreateRouteException", ex instanceof FailedToCreateRouteException);
            assertTrue("Get a wrong reason", ex.getCause() instanceof IllegalArgumentException);
        }
    }
    
    private void marshalAndUnmarshal(String inURI, String outURI) throws Exception {
        AddressBookProtos.Person input = AddressBookProtos.Person
            .newBuilder().setName("Martin").setId(1234).build();

        MockEndpoint mock = getMockEndpoint("mock:reverse");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(org.apache.camel.dataformat.protobuf.generated.AddressBookProtos.Person.class);
        mock.message(0).body().equals(input);

        Object marshalled = template.requestBody(inURI, input);

        template.sendBody(outURI, marshalled);

        mock.assertIsSatisfied();

        AddressBookProtos.Person output = mock.getReceivedExchanges().get(0).getIn().getBody(AddressBookProtos.Person.class);
        assertEquals("Martin", output.getName());
    }


    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                ProtobufDataFormat format = new ProtobufDataFormat(AddressBookProtos.Person.getDefaultInstance());

                from("direct:in").marshal(format);
                from("direct:back").unmarshal(format).to("mock:reverse");
                
                from("direct:marshal").marshal().protobuf();
                from("direct:unmarshalA").unmarshal().protobuf("org.apache.camel.dataformat.protobuf.generated.AddressBookProtos$Person").to("mock:reverse");
                
                from("direct:unmarshalB").unmarshal().protobuf(AddressBookProtos.Person.getDefaultInstance()).to("mock:reverse");
            }
        };
    }
    
    @Configuration
    public static Option[] configure() {
        Option[] options = combine(
            getDefaultCamelKarafOptions(),
            // using the features to install the other camel components             
            loadCamelFeatures("camel-protobuf"));
        
        return options;
    }
  
}
