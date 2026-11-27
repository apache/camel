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
package org.apache.camel.dataformat.parquet.avro;

import java.util.LinkedHashSet;
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Marshal accepts other bodies than a List: a single record, an array, another Collection and an Iterator.
 */
class ParquetAvroMarshalBodyTypesTest extends CamelTestSupport {

    private static final Pojo AIRPORT = new Pojo(1, "airport");
    private static final Pojo PENGUIN = new Pojo(2, "penguin");

    @Test
    void singleRecord() {
        assertEquals(List.of(AIRPORT), roundTrip(AIRPORT));
    }

    @Test
    void array() {
        assertEquals(List.of(AIRPORT, PENGUIN), roundTrip(new Pojo[] { AIRPORT, PENGUIN }));
    }

    @Test
    void collectionThatIsNotAList() {
        assertEquals(List.of(AIRPORT, PENGUIN), roundTrip(new LinkedHashSet<>(List.of(AIRPORT, PENGUIN))));
    }

    @Test
    void iterator() {
        assertEquals(List.of(AIRPORT, PENGUIN), roundTrip(List.of(AIRPORT, PENGUIN).iterator()));
    }

    private List<?> roundTrip(Object body) {
        Object marshalled = template.requestBody("direct:in", body);
        return template.requestBody("direct:back", marshalled, List.class);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                ParquetAvroDataFormat format = new ParquetAvroDataFormat();
                format.setUnmarshalType(Pojo.class);
                from("direct:in").marshal(format);
                from("direct:back").unmarshal(format);
            }
        };
    }
}
