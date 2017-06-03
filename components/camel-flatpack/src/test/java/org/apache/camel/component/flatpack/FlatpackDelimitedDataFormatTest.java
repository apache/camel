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
package org.apache.camel.component.flatpack;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.dataformat.flatpack.FlatpackDataFormat;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Unit test for delimited DataFormat.
 */
public class FlatpackDelimitedDataFormatTest extends CamelTestSupport {

    @Test
    public void testUnmarshal() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:unmarshal");
        // by default we get on big message
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(DataSetList.class);

        String data = IOConverter.toString(new File("src/test/data/delim/INVENTORY-CommaDelimitedWithQualifier.txt"), null);

        template.sendBody("direct:unmarshal", data);
        assertMockEndpointsSatisfied();

        DataSetList list = mock.getExchanges().get(0).getIn().getBody(DataSetList.class);
        assertEquals(4, list.size());
        Map<?, ?> row = list.get(0);
        assertEquals("SOME VALVE", row.get("ITEM_DESC"));
    }

    @Test
    public void testMarshalWithDefinition() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:marshal");
        // by default we get on big message
        mock.expectedMessageCount(1);

        List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("ITEM_DESC", "SOME VALVE");
        row.put("IN_STOCK", "2");
        row.put("PRICE", "5.00");
        row.put("LAST_RECV_DT", "20050101");
        data.add(row);

        Map<String, Object> row2 = new LinkedHashMap<String, Object>();
        row2.put("ITEM_DESC", "AN ENGINE");
        row2.put("IN_STOCK", "100");
        row2.put("PRICE", "1000.00");
        row2.put("LAST_RECV_DT", "20040601");
        data.add(row2);

        template.sendBody("direct:marshal", data);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMarshalNoDefinition() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:marshal2");
        // by default we get on big message
        mock.expectedMessageCount(1);

        List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("ITEM_DESC", "SOME VALVE");
        row.put("IN_STOCK", "2");
        row.put("PRICE", "5.00");
        row.put("LAST_RECV_DT", "20050101");
        data.add(row);

        Map<String, Object> row2 = new LinkedHashMap<String, Object>();
        row2.put("ITEM_DESC", "AN ENGINE");
        row2.put("IN_STOCK", "100");
        row2.put("PRICE", "1000.00");
        row2.put("LAST_RECV_DT", "20040601");
        data.add(row2);

        template.sendBody("direct:marshal2", data);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                FlatpackDataFormat df = new FlatpackDataFormat();
                df.setDefinition("INVENTORY-Delimited.pzmap.xml");

                from("direct:unmarshal").unmarshal(df).to("mock:unmarshal");

                // with the definition
                from("direct:marshal").marshal(df).convertBodyTo(String.class).to("mock:marshal");

                // without the definition (will auto add column names from the recieved data)
                FlatpackDataFormat df2 = new FlatpackDataFormat();
                from("direct:marshal2").marshal(df2).convertBodyTo(String.class).to("mock:marshal2");
            }
        };
    }
}
