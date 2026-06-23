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
package org.apache.camel.dataformat.bindy.fix;

import java.util.Date;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.annotation.ContinueOnFailure;
import org.apache.camel.dataformat.bindy.annotation.KeyValuePairField;
import org.apache.camel.dataformat.bindy.annotation.Message;
import org.apache.camel.dataformat.bindy.kvp.BindyKeyValuePairDataFormat;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * KeyValuePair parallel of BindyCsvContinueOnParseFailureTest. @Message controls the record-level
 * default; @KeyValuePairField provides the field-level tri-state. Bad value on tag 2 (orderDate).
 */
public class BindyKvpContinueOnParseFailureTest extends CamelTestSupport {

    private static final String BAD_ROW = "1=1 2=not-a-date 3=Alice";
    private static final String GOOD_ROW = "1=1 2=2026-01-15 3=Alice";

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

    @Test
    public void tolerant_primitiveIntField_getsMinValue() throws Exception {
        // @KeyValuePairField has no defaultValue element today, so the only fallback for a bad
        // primitive int is getDefaultValueForPrimitive (which returns MIN_VALUE in Bindy).
        MockEndpoint mock = getMockEndpoint("mock:tolerant-primitive-int");
        mock.expectedMessageCount(1);
        // tag 1 (int) is bad; tags 2 and 3 are valid
        template.sendBody("direct:tolerant-primitive-int", "1=abc 2=2026-01-15 3=Alice");
        MockEndpoint.assertIsSatisfied(context);

        TolerantPrimitiveIntOrder row
                = mock.getReceivedExchanges().get(0).getIn().getBody(TolerantPrimitiveIntOrder.class);
        assertNotNull(row);
        assertEquals(Integer.MIN_VALUE, row.id);
        assertNotNull(row.orderDate);
        assertEquals("Alice", row.customerName);
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
                        .unmarshal(new BindyKeyValuePairDataFormat(StrictDefaultOrder.class))
                        .to("mock:strict-default");
                from("direct:record-tolerant")
                        .unmarshal(new BindyKeyValuePairDataFormat(RecordTolerantOrder.class))
                        .to("mock:record-tolerant");
                from("direct:record-strict")
                        .unmarshal(new BindyKeyValuePairDataFormat(RecordStrictOrder.class))
                        .to("mock:record-strict");
                from("direct:field-strict-override")
                        .unmarshal(new BindyKeyValuePairDataFormat(FieldStrictOverrideOrder.class))
                        .to("mock:field-strict-override");
                from("direct:field-tolerant-override")
                        .unmarshal(new BindyKeyValuePairDataFormat(FieldTolerantOverrideOrder.class))
                        .to("mock:field-tolerant-override");
                from("direct:field-tolerant")
                        .unmarshal(new BindyKeyValuePairDataFormat(FieldTolerantOrder.class))
                        .to("mock:field-tolerant");
                from("direct:tolerant-primitive-int")
                        .unmarshal(new BindyKeyValuePairDataFormat(TolerantPrimitiveIntOrder.class))
                        .to("mock:tolerant-primitive-int");
            }
        };
    }

    @Message(pairSeparator = " ", keyValuePairSeparator = "=")
    public static class StrictDefaultOrder {
        @KeyValuePairField(tag = 1)
        public int id;
        @KeyValuePairField(tag = 2, pattern = "yyyy-MM-dd")
        public Date orderDate;
        @KeyValuePairField(tag = 3)
        public String customerName;
    }

    @Message(pairSeparator = " ", keyValuePairSeparator = "=", continueParseOnFailure = true)
    public static class RecordTolerantOrder {
        @KeyValuePairField(tag = 1)
        public int id;
        @KeyValuePairField(tag = 2, pattern = "yyyy-MM-dd")
        public Date orderDate;
        @KeyValuePairField(tag = 3)
        public String customerName;
    }

    @Message(pairSeparator = " ", keyValuePairSeparator = "=", continueParseOnFailure = false)
    public static class RecordStrictOrder {
        @KeyValuePairField(tag = 1)
        public int id;
        @KeyValuePairField(tag = 2, pattern = "yyyy-MM-dd")
        public Date orderDate;
        @KeyValuePairField(tag = 3)
        public String customerName;
    }

    @Message(pairSeparator = " ", keyValuePairSeparator = "=", continueParseOnFailure = true)
    public static class FieldStrictOverrideOrder {
        @KeyValuePairField(tag = 1)
        public int id;
        @KeyValuePairField(tag = 2, pattern = "yyyy-MM-dd", continueParseOnFailure = ContinueOnFailure.FALSE)
        public Date orderDate;
        @KeyValuePairField(tag = 3)
        public String customerName;
    }

    @Message(pairSeparator = " ", keyValuePairSeparator = "=", continueParseOnFailure = false)
    public static class FieldTolerantOverrideOrder {
        @KeyValuePairField(tag = 1)
        public int id;
        @KeyValuePairField(tag = 2, pattern = "yyyy-MM-dd", continueParseOnFailure = ContinueOnFailure.TRUE)
        public Date orderDate;
        @KeyValuePairField(tag = 3)
        public String customerName;
    }

    @Message(pairSeparator = " ", keyValuePairSeparator = "=")
    public static class FieldTolerantOrder {
        @KeyValuePairField(tag = 1)
        public int id;
        @KeyValuePairField(tag = 2, pattern = "yyyy-MM-dd", continueParseOnFailure = ContinueOnFailure.TRUE)
        public Date orderDate;
        @KeyValuePairField(tag = 3)
        public String customerName;
    }

    @Message(pairSeparator = " ", keyValuePairSeparator = "=", continueParseOnFailure = true)
    public static class TolerantPrimitiveIntOrder {
        @KeyValuePairField(tag = 1)
        public int id;
        @KeyValuePairField(tag = 2, pattern = "yyyy-MM-dd")
        public Date orderDate;
        @KeyValuePairField(tag = 3)
        public String customerName;
    }
}
