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
package org.apache.camel.component.jacksonxml;

import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JacksonMarshalUnmarshalListTest extends CamelTestSupport {

    @Test
    public void testUnmarshalListPojo() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:reversePojo");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(List.class);

        String json = "<list><pojo name=\"Camel\"/><pojo name=\"World\"/></list>";
        template.sendBody("direct:backPojo", json);

        assertMockEndpointsSatisfied();

        List list = mock.getReceivedExchanges().get(0).getIn().getBody(List.class);
        assertNotNull(list);
        assertEquals(2, list.size());

        TestPojo pojo = (TestPojo) list.get(0);
        assertEquals("Camel", pojo.getName());
        pojo = (TestPojo) list.get(1);
        assertEquals("World", pojo.getName());
    }

    @Test
    public void testUnmarshalListPojoOneElement() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:reversePojo");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(List.class);

        String json = "<list><pojo name=\"Camel\"/></list>";
        template.sendBody("direct:backPojo", json);

        assertMockEndpointsSatisfied();

        List list = mock.getReceivedExchanges().get(0).getIn().getBody(List.class);
        assertNotNull(list);
        assertEquals(1, list.size());

        TestPojo pojo = (TestPojo) list.get(0);
        assertEquals("Camel", pojo.getName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                JacksonXMLDataFormat format = new JacksonXMLDataFormat(TestPojo.class);
                format.useList();

                from("direct:backPojo").unmarshal(format).to("mock:reversePojo");

            }
        };
    }

}
