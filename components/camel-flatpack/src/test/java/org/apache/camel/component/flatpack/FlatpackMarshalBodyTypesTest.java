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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.flatpack.FlatpackDataFormat;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Marshal takes a List of Maps, or a single Map as one row, and fails with an InvalidPayloadException for any other
 * body.
 */
class FlatpackMarshalBodyTypesTest extends CamelTestSupport {

    @Test
    void marshalSingleMapAsOneRow() {
        String csv = template.requestBody("direct:marshal", row("1", "alice"), String.class);

        assertEquals(List.of("ID,NAME", "1,alice"), csv.lines().toList());
    }

    @Test
    void marshalStringFails() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:marshal", "1,alice", String.class));

        InvalidPayloadException cause = assertInstanceOf(InvalidPayloadException.class, e.getCause());
        assertEquals(List.class, cause.getType());
    }

    @Test
    void marshalListWithElementThatIsNotMapFails() {
        List<Object> data = List.of(row("1", "alice"), "2,bob");

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:marshal", data, String.class));

        InvalidPayloadException cause = assertInstanceOf(InvalidPayloadException.class, e.getCause());
        assertTrue(cause.getMessage().contains("element 1 is java.lang.String"), cause.getMessage());
    }

    @Test
    void dataFormatName() {
        assertEquals("flatpack", new FlatpackDataFormat().getDataFormatName());
    }

    private static Map<String, Object> row(String id, String name) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ID", id);
        row.put("NAME", name);
        return row;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:marshal").marshal(new FlatpackDataFormat()).convertBodyTo(String.class);
            }
        };
    }
}
