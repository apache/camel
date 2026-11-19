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
package org.apache.camel.dataformat.bindy.fixed;

import java.math.BigDecimal;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.dataformat.bindy.annotation.FixedLengthRecord;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A number padded with '0' and unmarshalled with trim = true: trimming the padding must not remove the value 0.
 */
public class BindyFixedLengthTrimZeroPaddingTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:numbers").unmarshal(new BindyFixedLengthDataFormat(Numbers.class)).to("mock:result");
                from("direct:marshalNumbers").marshal(new BindyFixedLengthDataFormat(Numbers.class)).to("mock:result");
                from("direct:required").unmarshal(new BindyFixedLengthDataFormat(RequiredNumber.class)).to("mock:result");
                from("direct:recordPadding").unmarshal(new BindyFixedLengthDataFormat(RecordPadding.class))
                        .to("mock:result");
                from("direct:noTrim").unmarshal(new BindyFixedLengthDataFormat(NumbersNoTrim.class)).to("mock:result");
                from("direct:text").unmarshal(new BindyFixedLengthDataFormat(Text.class)).to("mock:result");
                from("direct:spaces").unmarshal(new BindyFixedLengthDataFormat(SpacePadded.class)).to("mock:result");
                from("direct:short").unmarshal(new BindyFixedLengthDataFormat(ShortLine.class)).to("mock:result");
            }
        };
    }

    private <T> T send(String uri, Object body, Class<T> type) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.reset();
        mock.expectedMessageCount(1);
        template.sendBody(uri, body);
        mock.assertIsSatisfied();
        return mock.getReceivedExchanges().get(0).getIn().getBody(type);
    }

    @Test
    public void allZeroNumbersUnmarshalToZero() throws Exception {
        Numbers numbers = send("direct:numbers", "000000000000000", Numbers.class);

        assertEquals(Integer.valueOf(0), numbers.boxed);
        assertEquals(0, numbers.prim);
        assertEquals(0L, numbers.big);
        assertEquals(new BigDecimal("0.00"), numbers.amount);
    }

    @Test
    public void zeroRoundTrips() throws Exception {
        Numbers zero = new Numbers();
        zero.boxed = 0;
        zero.amount = BigDecimal.ZERO;

        String line = send("direct:marshalNumbers", zero, String.class);
        assertEquals("000000000000000", line.trim());

        Numbers back = send("direct:numbers", line, Numbers.class);
        assertEquals(Integer.valueOf(0), back.boxed);
        assertEquals(0, back.prim);
        assertEquals(0L, back.big);
        assertEquals(new BigDecimal("0.00"), back.amount);
    }

    @Test
    public void requiredFieldAcceptsZero() throws Exception {
        RequiredNumber number = send("direct:required", "000", RequiredNumber.class);

        assertEquals(0, number.value);
    }

    @Test
    public void recordPaddingCharZeroUnmarshalsToZero() throws Exception {
        RecordPadding number = send("direct:recordPadding", "000", RecordPadding.class);

        assertEquals(0, number.value);
    }

    @Test
    public void paddedNumbersKeepTheirDigits() throws Exception {
        Numbers numbers = send("direct:numbers", "007012005000125", Numbers.class);

        assertEquals(Integer.valueOf(7), numbers.boxed);
        assertEquals(12, numbers.prim);
        assertEquals(50L, numbers.big);
        assertEquals(new BigDecimal("1.25"), numbers.amount);
    }

    @Test
    public void withoutTrimZeroIsZero() throws Exception {
        NumbersNoTrim numbers = send("direct:noTrim", "000000", NumbersNoTrim.class);

        assertEquals(Integer.valueOf(0), numbers.boxed);
        assertEquals(0, numbers.prim);
    }

    @Test
    public void textPaddedWithZeroStaysEmpty() throws Exception {
        Text text = send("direct:text", "000", Text.class);

        assertEquals("", text.value);
    }

    @Test
    public void numbersPaddedWithSpacesStayEmpty() throws Exception {
        SpacePadded numbers = send("direct:spaces", "x      ", SpacePadded.class);

        assertNull(numbers.boxed);
        assertEquals(Integer.MIN_VALUE, numbers.prim);
    }

    @Test
    public void missingNumbersStayEmpty() throws Exception {
        ShortLine numbers = send("direct:short", "007", ShortLine.class);

        assertEquals(Integer.valueOf(7), numbers.first);
        assertNull(numbers.second);
    }

    @FixedLengthRecord(length = 15)
    public static class Numbers {
        @DataField(pos = 1, length = 3, paddingChar = '0', trim = true)
        Integer boxed;

        @DataField(pos = 4, length = 3, paddingChar = '0', trim = true)
        int prim;

        @DataField(pos = 7, length = 4, paddingChar = '0', trim = true)
        long big;

        @DataField(pos = 11, length = 5, precision = 2, impliedDecimalSeparator = true, paddingChar = '0', trim = true)
        BigDecimal amount;
    }

    @FixedLengthRecord(length = 3)
    public static class RequiredNumber {
        @DataField(pos = 1, length = 3, paddingChar = '0', trim = true, required = true)
        int value;
    }

    @FixedLengthRecord(length = 3, paddingChar = '0')
    public static class RecordPadding {
        @DataField(pos = 1, length = 3, paddingChar = 0, trim = true)
        int value;
    }

    @FixedLengthRecord(length = 6)
    public static class NumbersNoTrim {
        @DataField(pos = 1, length = 3, paddingChar = '0')
        Integer boxed;

        @DataField(pos = 4, length = 3, paddingChar = '0')
        int prim;
    }

    @FixedLengthRecord(length = 3)
    public static class Text {
        @DataField(pos = 1, length = 3, paddingChar = '0', trim = true)
        String value;
    }

    @FixedLengthRecord(length = 7)
    public static class SpacePadded {
        @DataField(pos = 1, length = 1)
        String id;

        @DataField(pos = 2, length = 3, trim = true)
        Integer boxed;

        @DataField(pos = 5, length = 3, trim = true)
        int prim;
    }

    @FixedLengthRecord(length = 6, ignoreMissingChars = true)
    public static class ShortLine {
        @DataField(pos = 1, length = 3, paddingChar = '0', trim = true)
        Integer first;

        @DataField(pos = 4, length = 3, paddingChar = '0', trim = true)
        Integer second;
    }
}
