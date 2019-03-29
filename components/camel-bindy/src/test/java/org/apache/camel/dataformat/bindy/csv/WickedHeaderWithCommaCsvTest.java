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
package org.apache.camel.dataformat.bindy.csv;

import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class WickedHeaderWithCommaCsvTest extends CamelTestSupport {

    @Test
    public void testHeadersWithComma() throws Exception {

        final MockEndpoint unmarshalMock = getMockEndpoint("mock:receiveUnmarshal");
        unmarshalMock.expectedMessageCount(1);

        String csv = ("\"Foo (one, or more, foos)\",\"Bar (one, or more, bars)\"" + "\r\n")
            + "\"1,000.00\",\"1,500.00\"" + "\r\n"
            + "\"2,000.00\",\"2,700.00\"" + "\r\n";

        template.sendBody("direct:startUnmarshal", csv);

        assertMockEndpointsSatisfied();

        final List<WickedHeaderWithCommaCsv> wickedHeaderWithCommaCsvs = unmarshalMock.getReceivedExchanges().get(0).getIn().getBody(List.class);

        final WickedHeaderWithCommaCsv row1000 = wickedHeaderWithCommaCsvs.get(0);
        assertEquals("1,000.00", row1000.getFoo());
        assertEquals("1,500.00", row1000.getBar());

        final WickedHeaderWithCommaCsv row2000 = wickedHeaderWithCommaCsvs.get(1);
        assertEquals("2,000.00", row2000.getFoo());
        assertEquals("2,700.00", row2000.getBar());

        final MockEndpoint marshalMock = getMockEndpoint("mock:receiveMarshal");
        template.sendBody("direct:startMarshal", wickedHeaderWithCommaCsvs);

        marshalMock.expectedMessageCount(1);
        assertMockEndpointsSatisfied();

        final String result = marshalMock.getReceivedExchanges().get(0).getIn().getBody(String.class);

        assertEquals(csv.trim(), result.trim());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:startUnmarshal")
                    .unmarshal(new BindyCsvDataFormat(WickedHeaderWithCommaCsv.class))
                    .to("mock:receiveUnmarshal");

                from("direct:startMarshal")
                    .marshal(new BindyCsvDataFormat(WickedHeaderWithCommaCsv.class))
                    .to("mock:receiveMarshal");

            }
        };
    }

}
