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
package org.apache.camel.dataformat.bindy.csv2;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.annotation.ContinueOnFailure;
import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.dataformat.bindy.csv.BindyCsvDataFormat;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the continueParseOnFailure / continueParseOnFailure override matrix: record-level boolean on @CsvRecord
 * interacts with field-level tri-state on @DataField so that the field opinion overrides the record default, and
 * INHERIT falls back to the record.
 *
 * Each model has three fields (id, orderDate, customerName); the bad row supplies a malformed date string at position
 * 2. Tolerant outcome: row is delivered, orderDate is null, and customerName is populated (proving the parser continued
 * past the bad field). Strict outcome: an exception propagates.
 */
public class BindyCsvContinueOnParseFailureTest extends CamelTestSupport {

    private static final String BAD_ROW = "1,not-a-date,Alice";
    private static final String GOOD_ROW = "1,2026-01-15,Alice";

    @Test
    public void recordUnset_fieldInherit_isStrict() {
        assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:strict-default", BAD_ROW));
    }

    @Test
    public void recordTolerant_fieldInherit_keepsRowWithNullField() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:record-tolerant");
        mock.expectedMessageCount(1);
        template.sendBody("direct:record-tolerant", BAD_ROW);
        MockEndpoint.assertIsSatisfied(context);

        RecordTolerantOrder row = mock.getReceivedExchanges().get(0).getIn().getBody(RecordTolerantOrder.class);
        assertNotNull(row);
        assertEquals(1, row.id);
        assertNull(row.orderDate);
        assertEquals("Alice", row.customerName);
    }

    @Test
    public void recordStrict_fieldInherit_isStrict() {
        assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:record-strict", BAD_ROW));
    }

    @Test
    public void recordTolerant_fieldFalse_fieldOverridesToStrict() {
        assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:field-strict-override", BAD_ROW));
    }

    @Test
    public void recordStrict_fieldTrue_fieldOverridesToTolerant() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:field-tolerant-override");
        mock.expectedMessageCount(1);
        template.sendBody("direct:field-tolerant-override", BAD_ROW);
        MockEndpoint.assertIsSatisfied(context);

        FieldTolerantOverrideOrder row
                = mock.getReceivedExchanges().get(0).getIn().getBody(FieldTolerantOverrideOrder.class);
        assertNotNull(row);
        assertEquals(1, row.id);
        assertNull(row.orderDate);
        assertEquals("Alice", row.customerName);
    }

    @Test
    public void recordUnset_fieldTrue_keepsRowWithNullField() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:field-tolerant");
        mock.expectedMessageCount(1);
        template.sendBody("direct:field-tolerant", BAD_ROW);
        MockEndpoint.assertIsSatisfied(context);

        FieldTolerantOrder row = mock.getReceivedExchanges().get(0).getIn().getBody(FieldTolerantOrder.class);
        assertNotNull(row);
        assertEquals(1, row.id);
        assertNull(row.orderDate);
        assertEquals("Alice", row.customerName);
    }

    // ---- defaultValue substitution ----

    @Test
    public void tolerant_withDefaultValue_substitutesParsedDefault() throws Exception {
        // Bad date input + defaultValue set -> field is populated with the parsed defaultValue (not null)
        MockEndpoint mock = getMockEndpoint("mock:tolerant-with-default");
        mock.expectedMessageCount(1);
        template.sendBody("direct:tolerant-with-default", BAD_ROW);
        MockEndpoint.assertIsSatisfied(context);

        TolerantWithDefaultValueOrder row
                = mock.getReceivedExchanges().get(0).getIn().getBody(TolerantWithDefaultValueOrder.class);
        assertNotNull(row);
        assertEquals(1, row.id);
        // Substituted from defaultValue="1970-01-01"
        assertNotNull(row.orderDate);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        assertEquals(fmt.parse("1970-01-01"), row.orderDate);
        assertEquals("Alice", row.customerName);
    }

    @Test
    public void strict_withDefaultValue_stillThrows() {
        // Even if defaultValue is set, strict mode propagates the parse exception
        assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:strict-with-default", BAD_ROW));
    }

    @Test
    public void tolerant_malformedDefaultValue_throws() {
        // defaultValue itself cannot be parsed by the field's format -> exception propagates
        assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:tolerant-malformed-default", BAD_ROW));
    }

    // ---- primitive / null fallback when no defaultValue is set ----

    @Test
    public void tolerant_primitiveIntField_getsZero() throws Exception {
        // Bad int input + tolerant + no defaultValue -> primitive default (0)
        MockEndpoint mock = getMockEndpoint("mock:tolerant-primitive-int");
        mock.expectedMessageCount(1);
        // id at pos 1 is the bad field here; the date is valid
        template.sendBody("direct:tolerant-primitive-int", "abc,2026-01-15,Alice");
        MockEndpoint.assertIsSatisfied(context);

        TolerantPrimitiveIntOrder row
                = mock.getReceivedExchanges().get(0).getIn().getBody(TolerantPrimitiveIntOrder.class);
        assertNotNull(row);
        assertEquals(Integer.MIN_VALUE, row.id);   // Bindy's primitive default convention                  // primitive int default
        assertNotNull(row.orderDate);             // good input still parses
        assertEquals("Alice", row.customerName);
    }

    @Test
    public void tolerant_multipleBadFields_eachGetsItsOwnFallback() throws Exception {
        // Two bad fields on one row:
        //   pos 1 (int) - no defaultValue -> 0
        //   pos 2 (Date) - defaultValue="1970-01-01" -> parsed default
        // pos 3 (String) is good -> "Alice"
        MockEndpoint mock = getMockEndpoint("mock:tolerant-multi-bad");
        mock.expectedMessageCount(1);
        template.sendBody("direct:tolerant-multi-bad", "abc,not-a-date,Alice");
        MockEndpoint.assertIsSatisfied(context);

        TolerantMultiBadOrder row = mock.getReceivedExchanges().get(0).getIn().getBody(TolerantMultiBadOrder.class);
        assertNotNull(row);
        assertEquals(Integer.MIN_VALUE, row.id);   // Bindy's primitive default convention
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        assertEquals(fmt.parse("1970-01-01"), row.orderDate);
        assertEquals("Alice", row.customerName);
    }

    @Test
    public void tolerant_emptyDefaultValueExplicit_fallsThroughToPrimitive() throws Exception {
        // defaultValue="" (explicitly empty) is treated the same as unset -> primitive default
        MockEndpoint mock = getMockEndpoint("mock:tolerant-empty-default");
        mock.expectedMessageCount(1);
        template.sendBody("direct:tolerant-empty-default", "abc,2026-01-15,Alice");
        MockEndpoint.assertIsSatisfied(context);

        TolerantEmptyDefaultOrder row
                = mock.getReceivedExchanges().get(0).getIn().getBody(TolerantEmptyDefaultOrder.class);
        assertEquals(Integer.MIN_VALUE, row.id);   // Bindy's primitive default convention
    }

    @Test
    public void recordTolerant_goodInput_parsesNormally() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:record-tolerant");
        mock.expectedMessageCount(1);
        template.sendBody("direct:record-tolerant", GOOD_ROW);
        MockEndpoint.assertIsSatisfied(context);

        RecordTolerantOrder row = mock.getReceivedExchanges().get(0).getIn().getBody(RecordTolerantOrder.class);
        assertNotNull(row);
        assertEquals(1, row.id);
        assertNotNull(row.orderDate);
        assertEquals("Alice", row.customerName);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:strict-default")
                        .unmarshal(new BindyCsvDataFormat(StrictDefaultOrder.class))
                        .to("mock:strict-default");
                from("direct:record-tolerant")
                        .unmarshal(new BindyCsvDataFormat(RecordTolerantOrder.class))
                        .to("mock:record-tolerant");
                from("direct:record-strict")
                        .unmarshal(new BindyCsvDataFormat(RecordStrictOrder.class))
                        .to("mock:record-strict");
                from("direct:field-strict-override")
                        .unmarshal(new BindyCsvDataFormat(FieldStrictOverrideOrder.class))
                        .to("mock:field-strict-override");
                from("direct:field-tolerant-override")
                        .unmarshal(new BindyCsvDataFormat(FieldTolerantOverrideOrder.class))
                        .to("mock:field-tolerant-override");
                from("direct:field-tolerant")
                        .unmarshal(new BindyCsvDataFormat(FieldTolerantOrder.class))
                        .to("mock:field-tolerant");
                from("direct:tolerant-with-default")
                        .unmarshal(new BindyCsvDataFormat(TolerantWithDefaultValueOrder.class))
                        .to("mock:tolerant-with-default");
                from("direct:strict-with-default")
                        .unmarshal(new BindyCsvDataFormat(StrictWithDefaultValueOrder.class))
                        .to("mock:strict-with-default");
                from("direct:tolerant-malformed-default")
                        .unmarshal(new BindyCsvDataFormat(TolerantMalformedDefaultOrder.class))
                        .to("mock:tolerant-malformed-default");
                from("direct:tolerant-primitive-int")
                        .unmarshal(new BindyCsvDataFormat(TolerantPrimitiveIntOrder.class))
                        .to("mock:tolerant-primitive-int");
                from("direct:tolerant-multi-bad")
                        .unmarshal(new BindyCsvDataFormat(TolerantMultiBadOrder.class))
                        .to("mock:tolerant-multi-bad");
                from("direct:tolerant-empty-default")
                        .unmarshal(new BindyCsvDataFormat(TolerantEmptyDefaultOrder.class))
                        .to("mock:tolerant-empty-default");
            }
        };
    }

    @CsvRecord(separator = ",")
    public static class StrictDefaultOrder {
        @DataField(pos = 1)
        public int id;
        @DataField(pos = 2, pattern = "yyyy-MM-dd")
        public Date orderDate;
        @DataField(pos = 3)
        public String customerName;
    }

    @CsvRecord(separator = ",", continueParseOnFailure = true)
    public static class RecordTolerantOrder {
        @DataField(pos = 1)
        public int id;
        @DataField(pos = 2, pattern = "yyyy-MM-dd")
        public Date orderDate;
        @DataField(pos = 3)
        public String customerName;
    }

    @CsvRecord(separator = ",", continueParseOnFailure = false)
    public static class RecordStrictOrder {
        @DataField(pos = 1)
        public int id;
        @DataField(pos = 2, pattern = "yyyy-MM-dd")
        public Date orderDate;
        @DataField(pos = 3)
        public String customerName;
    }

    @CsvRecord(separator = ",", continueParseOnFailure = true)
    public static class FieldStrictOverrideOrder {
        @DataField(pos = 1)
        public int id;
        @DataField(pos = 2, pattern = "yyyy-MM-dd", continueParseOnFailure = ContinueOnFailure.FALSE)
        public Date orderDate;
        @DataField(pos = 3)
        public String customerName;
    }

    @CsvRecord(separator = ",", continueParseOnFailure = false)
    public static class FieldTolerantOverrideOrder {
        @DataField(pos = 1)
        public int id;
        @DataField(pos = 2, pattern = "yyyy-MM-dd", continueParseOnFailure = ContinueOnFailure.TRUE)
        public Date orderDate;
        @DataField(pos = 3)
        public String customerName;
    }

    @CsvRecord(separator = ",")
    public static class FieldTolerantOrder {
        @DataField(pos = 1)
        public int id;
        @DataField(pos = 2, pattern = "yyyy-MM-dd", continueParseOnFailure = ContinueOnFailure.TRUE)
        public Date orderDate;
        @DataField(pos = 3)
        public String customerName;
    }

    @CsvRecord(separator = ",", continueParseOnFailure = true)
    public static class TolerantWithDefaultValueOrder {
        @DataField(pos = 1)
        public int id;
        @DataField(pos = 2, pattern = "yyyy-MM-dd", defaultValue = "1970-01-01")
        public Date orderDate;
        @DataField(pos = 3)
        public String customerName;
    }

    @CsvRecord(separator = ",")
    public static class StrictWithDefaultValueOrder {
        @DataField(pos = 1)
        public int id;
        @DataField(pos = 2, pattern = "yyyy-MM-dd", defaultValue = "1970-01-01")
        public Date orderDate;
        @DataField(pos = 3)
        public String customerName;
    }

    @CsvRecord(separator = ",", continueParseOnFailure = true)
    public static class TolerantMalformedDefaultOrder {
        @DataField(pos = 1)
        public int id;
        @DataField(pos = 2, pattern = "yyyy-MM-dd", defaultValue = "this-is-not-a-date")
        public Date orderDate;
        @DataField(pos = 3)
        public String customerName;
    }

    @CsvRecord(separator = ",", continueParseOnFailure = true)
    public static class TolerantPrimitiveIntOrder {
        @DataField(pos = 1)
        public int id;
        @DataField(pos = 2, pattern = "yyyy-MM-dd")
        public Date orderDate;
        @DataField(pos = 3)
        public String customerName;
    }

    @CsvRecord(separator = ",", continueParseOnFailure = true)
    public static class TolerantMultiBadOrder {
        @DataField(pos = 1)
        public int id;
        @DataField(pos = 2, pattern = "yyyy-MM-dd", defaultValue = "1970-01-01")
        public Date orderDate;
        @DataField(pos = 3)
        public String customerName;
    }

    @CsvRecord(separator = ",", continueParseOnFailure = true)
    public static class TolerantEmptyDefaultOrder {
        @DataField(pos = 1, defaultValue = "")
        public int id;
        @DataField(pos = 2, pattern = "yyyy-MM-dd")
        public Date orderDate;
        @DataField(pos = 3)
        public String customerName;
    }
}
