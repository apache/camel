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
package org.apache.camel.component.cbor;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class CBORDataFormatTest extends CamelTestSupport {
    
    @Test
    public void testMarshalAndUnmarshalMap() throws Exception {
        Map<String, Object> in = new HashMap<>();
        in.put("name", "Camel");

        MockEndpoint mock = getMockEndpoint("mock:reverse");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(Map.class);
        mock.message(0).body().isEqualTo(in);

        Object marshalled = template.requestBody("direct:in", in);

        template.sendBody("direct:back", marshalled);

        mock.assertIsSatisfied();
    }
    
    @Test
    public void testMarshalAndUnmarshalAuthor() throws Exception {
        Author auth = new Author();
        auth.setName("Don");
        auth.setSurname("Winslow");

        MockEndpoint mock = getMockEndpoint("mock:reverse-auth");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(Author.class);

        Object marshalled = template.requestBody("direct:in-auth", auth);

        template.sendBody("direct:back-auth", marshalled);
        
        Author authReturned = mock.getExchanges().get(0).getIn().getBody(Author.class);
        assertEquals("Don", authReturned.getName());
        assertEquals("Winslow", authReturned.getSurname());

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                CBORDataFormat format = new CBORDataFormat();
                format.useMap();

                from("direct:in").marshal(format);
                from("direct:back").unmarshal(format).to("mock:reverse");
                
                CBORDataFormat spec = new CBORDataFormat();
                spec.setUnmarshalType(Author.class);
                
                from("direct:in-auth").marshal(spec);
                from("direct:back-auth").unmarshal(spec).to("mock:reverse-auth");
            }
        };
    }

}
