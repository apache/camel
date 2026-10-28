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
package org.apache.camel.component.flatpack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.flatpack.FlatpackDataFormat;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Marshalling a list of maps writes every map as a data row, with the default ignoreFirstRecord=true too; the header
 * line comes from the column names.
 */
class FlatpackMarshalKeepsFirstRowTest extends CamelTestSupport {

    @Test
    void marshalWritesEveryRowWithDefaultOptions() {
        String csv = template.requestBody("direct:marshal", rows(), String.class);

        assertEquals(List.of("ID,NAME", "1,alice", "2,bob"), csv.lines().toList());
    }

    @Test
    void marshalWithDefinitionWritesEveryRow() {
        List<Map<String, Object>> data = new ArrayList<>();
        data.add(item("SOME VALVE", "2", "5.00", "20050101"));
        data.add(item("AN ENGINE", "100", "1000.00", "20040601"));

        String csv = template.requestBody("direct:marshalDefinition", data, String.class);

        List<String> lines = csv.lines().toList();
        assertEquals(3, lines.size(), csv);
        assertEquals("SOME VALVE,2,5.00,20050101", lines.get(1));
        assertEquals("AN ENGINE,100,1000.00,20040601", lines.get(2));
    }

    @Test
    void unmarshalThenMarshalKeepsEveryRecord() {
        String csv = "ID,NAME\r\n1,alice\r\n2,bob\r\n3,carol\r\n";

        Object list = template.requestBody("direct:unmarshal", csv);
        String back = template.requestBody("direct:marshal", list, String.class);

        List<String> lines = back.lines().toList();
        assertEquals(List.of("ID,NAME", "1,alice", "2,bob", "3,carol"), lines);
    }

    private static List<Map<String, Object>> rows() {
        List<Map<String, Object>> data = new ArrayList<>();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ID", "1");
        row.put("NAME", "alice");
        data.add(row);
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("ID", "2");
        row2.put("NAME", "bob");
        data.add(row2);
        return data;
    }

    private static Map<String, Object> item(String desc, String stock, String price, String date) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ITEM_DESC", desc);
        row.put("IN_STOCK", stock);
        row.put("PRICE", price);
        row.put("LAST_RECV_DT", date);
        return row;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                FlatpackDataFormat df = new FlatpackDataFormat();
                from("direct:marshal").marshal(df).convertBodyTo(String.class);
                from("direct:unmarshal").unmarshal(df);

                FlatpackDataFormat withDefinition = new FlatpackDataFormat();
                withDefinition.setDefinition("INVENTORY-Delimited.pzmap.xml");
                from("direct:marshalDefinition").marshal(withDefinition).convertBodyTo(String.class);
            }
        };
    }
}
