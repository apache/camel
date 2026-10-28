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

import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Unmarshal without unmarshalType, as in the first example of the docs.
 */
public class CBORDefaultUnmarshalTypeTest extends CamelTestSupport {

    @Test
    void testUnmarshalMap() {
        Map<String, Object> in = Map.of("name", "Camel");

        Object out = template.requestBody("direct:back", template.requestBody("direct:in", in));
        assertInstanceOf(Map.class, out);
        assertEquals(in, out);
    }

    @Test
    void testUnmarshalList() {
        List<Object> in = List.of(Map.of("id", 1), Map.of("id", 2));

        Object out = template.requestBody("direct:back", template.requestBody("direct:in", in));
        assertInstanceOf(List.class, out);
        assertEquals(in, out);
    }

    @Test
    void testUnmarshalString() {
        Object out = template.requestBody("direct:back", template.requestBody("direct:in", "Camel"));
        assertEquals("Camel", out);
    }

    @Test
    void testUnmarshalUseList() {
        List<Object> in = List.of(Map.of("id", 1), Map.of("id", 2));

        Object out = template.requestBody("direct:back-list", template.requestBody("direct:in", in));
        assertInstanceOf(List.class, out);
        assertEquals(in, out);
    }

    @Test
    void testUnmarshalTypeName() {
        Author auth = new Author();
        auth.setName("Don");
        auth.setSurname("Winslow");

        Object out = template.requestBody("direct:back-name", template.requestBody("direct:in", auth));
        Author returned = assertInstanceOf(Author.class, out);
        assertEquals("Don", returned.getName());
        assertEquals("Winslow", returned.getSurname());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:in").marshal().cbor();
                from("direct:back").unmarshal().cbor();
                from("direct:back-list").unmarshal(dataFormat().cbor().useList(true).end());

                CBORDataFormat byName = new CBORDataFormat();
                byName.setUnmarshalTypeName(Author.class.getName());
                from("direct:back-name").unmarshal(byName);
            }
        };
    }
}
