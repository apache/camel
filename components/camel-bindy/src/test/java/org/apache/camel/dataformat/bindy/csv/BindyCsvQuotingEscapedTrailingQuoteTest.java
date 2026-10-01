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

import java.math.BigDecimal;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With quotingEscaped=true a quote inside a field is written as a backslash and the quote. A field whose value ends
 * with a quote must still be read back as one field.
 */
public class BindyCsvQuotingEscapedTrailingQuoteTest extends CamelTestSupport {

    @Test
    public void testUnmarshalFieldEndingWithEscapedQuote() {
        String body = "\"123\",\"He said \\\"hi\\\"\",\"10\"\r\n";
        Row row = template.requestBody("direct:unmarshal", body, Row.class);
        assertEquals("123", row.getFirstField());
        assertEquals("He said \"hi\"", row.getSecondField());
        assertEquals(new BigDecimal("10"), row.getNumber());
    }

    @Test
    public void testMarshalUnmarshalFieldEndingWithQuote() {
        for (String value : new String[] { "He said \"hi\"", "12\"", "\"", "a \"b\" \"c\"" }) {
            Row row = new Row();
            row.setFirstField("123");
            row.setSecondField(value);
            row.setNumber(new BigDecimal("10"));

            String csv = template.requestBody("direct:marshal", row, String.class);
            Row back = template.requestBody("direct:unmarshal", csv, Row.class);
            assertEquals("123", back.getFirstField(), csv);
            assertEquals(value, back.getSecondField(), csv);
            assertEquals(new BigDecimal("10"), back.getNumber(), csv);
        }
    }

    @Test
    public void testMarshalUnmarshalFieldWithoutTrailingQuote() {
        // these values are read back correctly today and must stay so
        for (String value : new String[] { "\"\"foo\"\"", "C:\\temp\\", "a\\", "say \"hi\" now" }) {
            Row row = new Row();
            row.setFirstField("123");
            row.setSecondField(value);
            row.setNumber(new BigDecimal("10"));

            String csv = template.requestBody("direct:marshal", row, String.class);
            Row back = template.requestBody("direct:unmarshal", csv, Row.class);
            assertEquals(value, back.getSecondField(), csv);
            assertEquals(new BigDecimal("10"), back.getNumber(), csv);
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                BindyCsvDataFormat format = new BindyCsvDataFormat(Row.class);
                from("direct:marshal").marshal(format);
                from("direct:unmarshal").unmarshal(format);
            }
        };
    }

    @CsvRecord(separator = ",", quote = "\"", quoting = true, quotingEscaped = true)
    public static class Row {

        @DataField(pos = 1)
        private String firstField;

        @DataField(pos = 2)
        private String secondField;

        @DataField(pos = 3, pattern = "########.##")
        private BigDecimal number;

        public String getFirstField() {
            return firstField;
        }

        public void setFirstField(String firstField) {
            this.firstField = firstField;
        }

        public String getSecondField() {
            return secondField;
        }

        public void setSecondField(String secondField) {
            this.secondField = secondField;
        }

        public BigDecimal getNumber() {
            return number;
        }

        public void setNumber(BigDecimal number) {
            this.number = number;
        }
    }
}
