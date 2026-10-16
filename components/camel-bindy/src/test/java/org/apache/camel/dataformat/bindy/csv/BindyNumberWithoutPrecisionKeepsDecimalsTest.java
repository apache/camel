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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.dataformat.bindy.annotation.FixedLengthRecord;
import org.apache.camel.model.dataformat.BindyDataFormat;
import org.apache.camel.model.dataformat.BindyType;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A BigDecimal, Double or Float field without a precision keeps all its decimals when it is marshalled, so that a
 * record unmarshalled and marshalled again is the same text. The default formatter of the JDK keeps at most three
 * decimals.
 */
public class BindyNumberWithoutPrecisionKeepsDecimalsTest extends CamelTestSupport {

    @Test
    public void csvMarshalKeepsAllDecimals() {
        CsvModel model = new CsvModel();
        model.rate = new BigDecimal("1.23456");
        model.ratio = 0.000123d;
        model.weight = 1.1f;

        String csv = template.requestBody("direct:csv-marshal", model, String.class);

        assertEquals("1.23456,0.000123,1.1\r\n", csv);
    }

    @Test
    public void csvRecordIsTheSameAfterUnmarshalAndMarshal() {
        String record = "0.98765,12.3456789,2.5\r\n";

        CsvModel model = template.requestBody("direct:csv-unmarshal", record, CsvModel.class);
        assertEquals(new BigDecimal("0.98765"), model.rate);
        assertEquals(12.3456789d, model.ratio);
        assertEquals(2.5f, model.weight);

        assertEquals(record, template.requestBody("direct:csv-marshal", model, String.class));
    }

    @Test
    public void wholeAndShortNumbersAreUnchanged() {
        CsvModel model = new CsvModel();
        model.rate = new BigDecimal("10");
        model.ratio = 10d;
        model.weight = 0.5f;

        assertEquals("10,10,0.5\r\n", template.requestBody("direct:csv-marshal", model, String.class));
    }

    @Test
    public void wholeFloatKeepsItsDigits() {
        CsvModel model = new CsvModel();
        model.rate = BigDecimal.ONE;
        model.ratio = 1d;
        // a float that is a whole number above 2^24: Float.toString gives the shorter 1.2345679E8
        model.weight = 123456792f;

        assertEquals("1,1,123456792\r\n", template.requestBody("direct:csv-marshal", model, String.class));
    }

    @Test
    public void fixedLengthRecordIsTheSameAfterUnmarshalAndMarshal() {
        String record = "   1.23456  0.000123";

        FixedModel model = template.requestBody("direct:fixed-unmarshal", record, FixedModel.class);
        assertEquals(new BigDecimal("1.23456"), model.amount);
        assertEquals(0.000123d, model.factor);

        assertEquals(record + "\r\n", template.requestBody("direct:fixed-marshal", model, String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                BindyDataFormat csv = new BindyDataFormat().type(BindyType.Csv).classType(CsvModel.class).locale("en");
                BindyDataFormat fixed
                        = new BindyDataFormat().type(BindyType.Fixed).classType(FixedModel.class).locale("en");

                from("direct:csv-marshal").marshal(csv);
                from("direct:csv-unmarshal").unmarshal(csv);
                from("direct:fixed-marshal").marshal(fixed);
                from("direct:fixed-unmarshal").unmarshal(fixed);
            }
        };
    }

    @CsvRecord(separator = ",")
    public static class CsvModel {
        @DataField(pos = 1)
        public BigDecimal rate;

        @DataField(pos = 2)
        public Double ratio;

        @DataField(pos = 3)
        public Float weight;
    }

    @FixedLengthRecord(length = 20)
    public static class FixedModel {
        @DataField(pos = 1, length = 10)
        public BigDecimal amount;

        @DataField(pos = 11, length = 10)
        public Double factor;
    }
}
