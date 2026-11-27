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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Marshal accepts other bodies than a List: a single record, an array, another Collection, an Iterator and a Stream.
 * Without unmarshalType, it needs Avro GenericRecords to know the schema.
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

    @Test
    void stream() {
        assertEquals(List.of(AIRPORT, PENGUIN), roundTrip(Stream.of(AIRPORT, PENGUIN)));
    }

    @Test
    void emptyBodyWithoutUnmarshalTypeFails() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:noUnmarshalType", List.of()));
        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertTrue(cause.getMessage().contains("no records"), cause.getMessage());
    }

    @Test
    void pojoWithoutUnmarshalTypeFails() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:noUnmarshalType", List.of(AIRPORT)));
        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertTrue(cause.getMessage().contains(Pojo.class.getName()), cause.getMessage());
    }

    @Test
    void streamIsClosed() {
        AtomicBoolean closed = new AtomicBoolean();
        roundTrip(Stream.of(AIRPORT, PENGUIN).onClose(() -> closed.set(true)));
        assertTrue(closed.get());
    }

    @Test
    void nullBodyWithoutUnmarshalTypeFails() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:noUnmarshalType", (Object) null));
        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertTrue(cause.getMessage().contains("no records"), cause.getMessage());
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
                from("direct:noUnmarshalType").marshal(new ParquetAvroDataFormat());
            }
        };
    }
}
