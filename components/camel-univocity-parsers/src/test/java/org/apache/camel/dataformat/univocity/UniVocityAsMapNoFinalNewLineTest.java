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
package org.apache.camel.dataformat.univocity;

import java.util.Iterator;
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.dataformat.univocity.UniVocityTestHelper.asMap;
import static org.apache.camel.test.junit6.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that asMap maps the last row by the header names when the input does not end with a new line, also when there
 * is only one data row (the parser reaches the end of the input while it reads that row).
 */
public final class UniVocityAsMapNoFinalNewLineTest extends CamelTestSupport {

    @Test
    public void shouldUnmarshalCsvOneRowAsMap() {
        List<?> body = assertIsInstanceOf(List.class, template.requestBody("direct:csv", "a,b\n1,2"));
        assertEquals(List.of(asMap("a", "1", "b", "2")), body);
    }

    @Test
    public void shouldUnmarshalCsvTwoRowsAsMap() {
        List<?> body = assertIsInstanceOf(List.class, template.requestBody("direct:csv", "a,b\n1,2\n3,4"));
        assertEquals(List.of(asMap("a", "1", "b", "2"), asMap("a", "3", "b", "4")), body);
    }

    @Test
    public void shouldUnmarshalCsvOneRowAsMapLazily() {
        Iterator<?> body = assertIsInstanceOf(Iterator.class, template.requestBody("direct:csvLazy", "a,b\n1,2"));
        assertTrue(body.hasNext());
        assertEquals(asMap("a", "1", "b", "2"), body.next());
        assertFalse(body.hasNext());
    }

    @Test
    public void shouldUnmarshalCsvOneRowAsMapWithGivenHeaders() {
        List<?> body = assertIsInstanceOf(List.class, template.requestBody("direct:csvHeaders", "1,2"));
        assertEquals(List.of(asMap("a", "1", "b", "2")), body);
    }

    @Test
    public void shouldUnmarshalTsvOneRowAsMap() {
        List<?> body = assertIsInstanceOf(List.class, template.requestBody("direct:tsv", "a\tb\n1\t2"));
        assertEquals(List.of(asMap("a", "1", "b", "2")), body);
    }

    @Test
    public void shouldUnmarshalFixedOneRowAsMap() {
        // the padding of the last field is trimmed too, so the end of the input is reached while that row is read
        List<?> body = assertIsInstanceOf(List.class, template.requestBody("direct:fixed", "a  b  \n1  2"));
        assertEquals(List.of(asMap("a", "1", "b", "2")), body);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                UniVocityCsvDataFormat csv = new UniVocityCsvDataFormat();
                csv.setAsMap(true);
                csv.setHeaderExtractionEnabled(true);
                from("direct:csv").unmarshal(csv);

                UniVocityCsvDataFormat csvLazy = new UniVocityCsvDataFormat();
                csvLazy.setAsMap(true);
                csvLazy.setHeaderExtractionEnabled(true);
                csvLazy.setLazyLoad(true);
                from("direct:csvLazy").unmarshal(csvLazy);

                UniVocityCsvDataFormat csvHeaders = new UniVocityCsvDataFormat();
                csvHeaders.setAsMap(true);
                csvHeaders.setHeaders("a,b");
                from("direct:csvHeaders").unmarshal(csvHeaders);

                UniVocityTsvDataFormat tsv = new UniVocityTsvDataFormat();
                tsv.setAsMap(true);
                tsv.setHeaderExtractionEnabled(true);
                from("direct:tsv").unmarshal(tsv);

                UniVocityFixedDataFormat fixed = new UniVocityFixedDataFormat();
                fixed.setFieldLengths("3,3");
                fixed.setAsMap(true);
                fixed.setHeaderExtractionEnabled(true);
                from("direct:fixed").unmarshal(fixed);
            }
        };
    }
}
