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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.dataformat.bindy.format.FormatException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A java.util.Date field is formatted with its pattern, and the formatted text can be longer than the pattern (M, d, h
 * with two digits, MMMM, a). Such a value must be read back.
 */
public class BindyCsvDatePatternValueLongerThanPatternTest extends CamelTestSupport {

    private static Date date(String text) throws Exception {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH);
        return df.parse(text);
    }

    @Test
    public void testUnmarshalValuesLongerThanPattern() throws Exception {
        String body = "12/25/2026;25.12.2026;December 25 2026;11:45 PM\r\n";
        Row row = template.requestBody("direct:unmarshal", body, Row.class);
        assertEquals(date("2026-12-25 00:00"), row.getUs());
        assertEquals(date("2026-12-25 00:00"), row.getDe());
        assertEquals(date("2026-12-25 00:00"), row.getLongMonth());
        assertEquals(date("1970-01-01 23:45"), row.getTime());
    }

    @Test
    public void testMarshalUnmarshalRoundTrip() throws Exception {
        Row row = new Row();
        row.setUs(date("2026-11-30 00:00"));
        row.setDe(date("2026-10-15 00:00"));
        row.setLongMonth(date("2026-09-30 00:00"));
        row.setTime(date("1970-01-01 10:05"));

        String csv = template.requestBody("direct:marshal", row, String.class);
        assertEquals("11/30/2026;15.10.2026;September 30 2026;10:05 AM\r\n", csv);

        Row back = template.requestBody("direct:unmarshal", csv, Row.class);
        assertEquals(row.getUs(), back.getUs());
        assertEquals(row.getDe(), back.getDe());
        assertEquals(row.getLongMonth(), back.getLongMonth());
        assertEquals(row.getTime(), back.getTime());
    }

    @Test
    public void testUnmarshalLongerValueThatIsNotTheDateIsRejected() {
        // a date followed by other characters, and a value that is not a valid date
        for (String value : new String[] { "12/25/2026-01", "13/45/2026" }) {
            String body = value + ";25.12.2026;December 25 2026;11:45 PM\r\n";
            CamelExecutionException e = assertThrows(CamelExecutionException.class,
                    () -> template.requestBody("direct:unmarshal", body, Row.class));
            assertInstanceOf(FormatException.class, e.getCause().getCause(), value);
            assertEquals("Date provided does not fit the pattern defined, position: 1, line: 1", e.getCause().getMessage(),
                    value);
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                BindyCsvDataFormat format = new BindyCsvDataFormat(Row.class);
                format.setLocale("en");
                from("direct:marshal").marshal(format);
                from("direct:unmarshal").unmarshal(format);
            }
        };
    }

    @CsvRecord(separator = ";")
    public static class Row {

        @DataField(pos = 1, pattern = "M/d/yyyy")
        private Date us;

        @DataField(pos = 2, pattern = "d.M.yyyy")
        private Date de;

        @DataField(pos = 3, pattern = "MMMM d yyyy")
        private Date longMonth;

        @DataField(pos = 4, pattern = "h:mm a")
        private Date time;

        public Date getUs() {
            return us;
        }

        public void setUs(Date us) {
            this.us = us;
        }

        public Date getDe() {
            return de;
        }

        public void setDe(Date de) {
            this.de = de;
        }

        public Date getLongMonth() {
            return longMonth;
        }

        public void setLongMonth(Date longMonth) {
            this.longMonth = longMonth;
        }

        public Date getTime() {
            return time;
        }

        public void setTime(Date time) {
            this.time = time;
        }
    }
}
