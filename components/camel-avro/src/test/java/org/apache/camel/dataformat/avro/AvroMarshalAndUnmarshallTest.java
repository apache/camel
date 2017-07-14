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
package org.apache.camel.dataformat.avro;

import org.apache.camel.CamelException;
import org.apache.camel.avro.generated.Value;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;

import org.junit.Test;

public class AvroMarshalAndUnmarshallTest extends CamelTestSupport {

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
    public void testMarshalAndUnmarshalWithDSL3() throws Exception {
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:unmarshalC").unmarshal().avro(new CamelException("wrong schema"))
                            .to("mock:reverse");
                }
            });
            fail("Expect the exception here");
        } catch (Exception ex) {
            // expected
        }
    }


    private void marshalAndUnmarshal(String inURI, String outURI) throws Exception {
        Value input = Value.newBuilder().setValue("test body").build();

        MockEndpoint mock = getMockEndpoint("mock:reverse");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(Value.class);
        mock.message(0).body().isEqualTo(input);

        Object marshalled = template.requestBody(inURI, input);

        template.sendBody(outURI, marshalled);

        mock.assertIsSatisfied();

        Value output = mock.getReceivedExchanges().get(0).getIn().getBody(Value.class);
        assertEquals(input, output);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                AvroDataFormat format = new AvroDataFormat(Value.SCHEMA$);

                from("direct:in").marshal(format);
                from("direct:back").unmarshal(format).to("mock:reverse");

                from("direct:marshal").marshal().avro();
                from("direct:unmarshalA").unmarshal().avro(Value.class.getName()).to("mock:reverse");

                from("direct:unmarshalB").unmarshal().avro(Value.SCHEMA$).to("mock:reverse");
            }
        };
    }

}
