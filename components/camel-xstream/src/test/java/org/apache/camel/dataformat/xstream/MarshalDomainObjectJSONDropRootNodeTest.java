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
package org.apache.camel.dataformat.xstream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.dataformat.JsonDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;

public class MarshalDomainObjectJSONDropRootNodeTest extends CamelTestSupport {
    
    @Test
    public void testMarshalAndUnmarshalWithPrettyPrint() throws Exception {
        PurchaseOrder order = new PurchaseOrder();
        order.setName("pretty printed Camel");
        order.setAmount(1);
        order.setPrice(7.91);

        MockEndpoint mock = getMockEndpoint("mock:reverse");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(PurchaseOrder.class);
        mock.message(0).body().isEqualTo(order);

        Object marshalled = template.requestBody("direct:inPretty", order);
        String marshalledAsString = context.getTypeConverter().convertTo(String.class, marshalled);
        // the line-separator used by JsonWriter is "\n", even on windows
        String expected = "{\n"
                          + "  \"name\": \"pretty printed Camel\",\n" 
                          + "  \"price\": 7.91,\n"
                          + "  \"amount\": 1.0\n" 
                          + "}";
        assertEquals(expected, marshalledAsString);

        // must include class type when reversing
        String back = "{\"org.apache.camel.dataformat.xstream.PurchaseOrder\": {\n"
                + "  \"name\": \"pretty printed Camel\",\n"
                + "  \"price\": 7.91,\n"
                + "  \"amount\": 1.0\n"
                + "}}";

        template.sendBody("direct:backPretty", back);

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:reverse").unmarshal().json(JsonLibrary.XStream, PurchaseOrder.class).to("mock:reverse");

                JsonDataFormat df = new JsonDataFormat().library(JsonLibrary.XStream).dropRootNode(true).prettyPrint(true);
                from("direct:inPretty").marshal(df);

                from("direct:backPretty").unmarshal().json(JsonLibrary.XStream, PurchaseOrder.class, true).to("mock:reverse");
            }
        };
    }

}
