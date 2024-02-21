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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CBORMarshalAllowJMSTypeTest extends CamelTestSupport {

    @Test
    void testUnmarshalPojo() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:reversePojo");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(Author.class);

        Author author = new Author();
        author.setName("David");
        author.setSurname("Foster Wallace");

        CBORFactory factory = new CBORFactory();
        ObjectMapper objectMapper = new ObjectMapper(factory);
        byte[] payload = objectMapper.writeValueAsBytes(author);
        template.sendBodyAndHeader("direct:backPojo", payload, "JMSType", Author.class.getName());

        MockEndpoint.assertIsSatisfied(context);

        Author pojo = mock.getReceivedExchanges().get(0).getIn().getBody(Author.class);
        assertNotNull(pojo);
        assertEquals("David", pojo.getName());
        assertEquals("Foster Wallace", pojo.getSurname());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                CBORDataFormat format = new CBORDataFormat();
                format.setAllowJmsType(true);

                from("direct:backPojo").unmarshal(format).to("mock:reversePojo");
            }
        };
    }

}
