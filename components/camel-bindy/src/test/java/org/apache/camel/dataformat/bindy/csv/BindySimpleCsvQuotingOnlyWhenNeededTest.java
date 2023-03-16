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

import java.io.Serializable;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BindySimpleCsvQuotingOnlyWhenNeededTest extends CamelTestSupport {
    @Test
    public void testMarshalFieldQuotedWhenContainingDoubleQuote() {
        BindyCsvRowFormat191431 body = new BindyCsvRowFormat191431();
        body.setFirstField("123");
        body.setSecondField("He said \"lets go to Hawaii!\"");
        body.setaNumber(10.5);
        body.setaBoolean(true);

        String marshalled = template.requestBody("direct:marshal1", body, String.class);
        assertEquals("123,\"He said \"\"lets go to Hawaii!\"\"\",10.5,true\r\n", marshalled);
    }

    @Test
    public void testUnmarshalFieldWhenContainingDoubleQuote() {
        String body = "123,\"He said \"\"lets go to Hawaii!\"\"\",10.5,true\r\n";
        BindyCsvRowFormat191431 unmarshalled = template.requestBody("direct:unmarshal1", body, BindyCsvRowFormat191431.class);
        assertEquals("123", unmarshalled.getFirstField());
        assertEquals("He said \"lets go to Hawaii!\"", unmarshalled.getSecondField());
        assertEquals(10.5, unmarshalled.getaNumber());
        assertEquals(true, unmarshalled.getaBoolean());
    }

    @Test
    public void testMarshalFieldQuotedWhenContainingOtherConfiguredQuoteCharacter() {
        BindyCsvRowFormat191432 body = new BindyCsvRowFormat191432();
        body.setFirstField("123");
        body.setSecondField("He said 'lets go to Hawaii!'");
        body.setaNumber(10.5);
        body.setaBoolean(true);

        String marshalled = template.requestBody("direct:marshal2", body, String.class);
        assertEquals("123,'He said \\'lets go to Hawaii!\\'',10.5,true\r\n", marshalled);
    }

    @Test
    public void testUnmarshalFieldWhenContainingOtherConfiguredQuoteCharacter() {
        String body = "123,'He said \\'lets go to Hawaii!\\'',10.5,true\r\n";
        BindyCsvRowFormat191432 unmarshalled = template.requestBody("direct:unmarshal2", body, BindyCsvRowFormat191432.class);
        assertEquals("123", unmarshalled.getFirstField());
        assertEquals("He said 'lets go to Hawaii!'", unmarshalled.getSecondField());
        assertEquals(10.5, unmarshalled.getaNumber());
        assertEquals(true, unmarshalled.getaBoolean());
    }

    @Test
    public void testMarshalFieldQuotedWhenContainingComma() {
        BindyCsvRowFormat191431 body = new BindyCsvRowFormat191431();
        body.setFirstField("123");
        body.setSecondField("Then, lets go to Hawaii!");
        body.setaNumber(10.5);
        body.setaBoolean(true);

        String marshalled = template.requestBody("direct:marshal1", body, String.class);
        assertEquals("123,\"Then, lets go to Hawaii!\",10.5,true\r\n", marshalled);
    }

    @Test
    public void testUnmarshalFieldWhenContainingComma() {
        String body = "123,\"Then, lets go to Hawaii!\",10.5,true\r\n";
        BindyCsvRowFormat191431 unmarshalled = template.requestBody("direct:unmarshal1", body, BindyCsvRowFormat191431.class);
        assertEquals("123", unmarshalled.getFirstField());
        assertEquals("Then, lets go to Hawaii!", unmarshalled.getSecondField());
        assertEquals(10.5, unmarshalled.getaNumber());
        assertEquals(true, unmarshalled.getaBoolean());
    }

    @Test
    public void testMarshalFieldQuotedWhenContainingOtherConfiguredSeparator() {
        BindyCsvRowFormat191433 body = new BindyCsvRowFormat191433();
        body.setFirstField("123");
        body.setSecondField("Then; lets go to Hawaii!");
        body.setaNumber(10.5);
        body.setaBoolean(true);

        String marshalled = template.requestBody("direct:marshal3", body, String.class);
        assertEquals("123;\"Then; lets go to Hawaii!\";10.5;true\r\n", marshalled);
    }

    @Test
    public void testUnmarshalFieldWhenContainingOtherConfiguredSeparator() {
        String body = "123;\"Then; lets go to Hawaii!\";10.5;true\r\n";
        BindyCsvRowFormat191433 unmarshalled = template.requestBody("direct:unmarshal3", body, BindyCsvRowFormat191433.class);
        assertEquals("123", unmarshalled.getFirstField());
        assertEquals("Then; lets go to Hawaii!", unmarshalled.getSecondField());
        assertEquals(10.5, unmarshalled.getaNumber());
        assertEquals(true, unmarshalled.getaBoolean());
    }

    @Test
    public void testMarshalFieldQuotedWhenContainingCrlf() {
        BindyCsvRowFormat191431 body = new BindyCsvRowFormat191431();
        body.setFirstField("123");
        body.setSecondField("Then\r\n lets go to Hawaii!");
        body.setaNumber(10.5);
        body.setaBoolean(true);

        String marshalled = template.requestBody("direct:marshal1", body, String.class);
        assertEquals("123,\"Then\r\n lets go to Hawaii!\",10.5,true\r\n", marshalled);
    }

    @Test
    public void testMarshalFieldQuotedWhenContainingOtherConfiguredEscapeCharacter() {
        BindyCsvRowFormat191434 body = new BindyCsvRowFormat191434();
        body.setFirstField("123");
        body.setSecondField("Then\n lets go to Hawaii!");
        body.setaNumber(10.5);
        body.setaBoolean(true);

        String marshalled = template.requestBody("direct:marshal4", body, String.class);
        assertEquals("123,\"Then\n lets go to Hawaii!\",10.5,true\n", marshalled);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                final BindyCsvDataFormat one = new BindyCsvDataFormat(BindyCsvRowFormat191431.class);
                final BindyCsvDataFormat two = new BindyCsvDataFormat(BindyCsvRowFormat191432.class);
                final BindyCsvDataFormat three = new BindyCsvDataFormat(BindyCsvRowFormat191433.class);
                final BindyCsvDataFormat four = new BindyCsvDataFormat(BindyCsvRowFormat191434.class);

                from("direct:marshal1").marshal(one);
                from("direct:marshal2").marshal(two);
                from("direct:marshal3").marshal(three);
                from("direct:marshal4").marshal(four);

                from("direct:unmarshal1").unmarshal(one);
                from("direct:unmarshal2").unmarshal(two);
                from("direct:unmarshal3").unmarshal(three);
                from("direct:unmarshal4").unmarshal(four);
            }
        };
    }

    @CsvRecord(separator = ",", quoting = true, quotingOnlyWhenNeeded = true)
    public static class BindyCsvRowFormat191431 implements Serializable {
        private static final long serialVersionUID = 1L;

        @DataField(pos = 1)
        private String firstField;
        @DataField(pos = 2)
        private String secondField;
        @DataField(pos = 3, pattern = "#.##")
        private Double aNumber;
        @DataField(pos = 4)
        private Boolean aBoolean;

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

        public Double getaNumber() {
            return aNumber;
        }

        public void setaNumber(Double aNumber) {
            this.aNumber = aNumber;
        }

        public Boolean getaBoolean() {
            return aBoolean;
        }

        public void setaBoolean(Boolean aBoolean) {
            this.aBoolean = aBoolean;
        }
    }

    @CsvRecord(separator = ",", quoting = true, quote = "'", quotingEscaped = true, quotingOnlyWhenNeeded = true)
    public static class BindyCsvRowFormat191432 implements Serializable {
        private static final long serialVersionUID = 1L;
        @DataField(pos = 1)
        private String firstField;
        @DataField(pos = 2)
        private String secondField;
        @DataField(pos = 3, pattern = "#.##")
        private Double aNumber;
        @DataField(pos = 4)
        private Boolean aBoolean;

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

        public Double getaNumber() {
            return aNumber;
        }

        public void setaNumber(Double aNumber) {
            this.aNumber = aNumber;
        }

        public Boolean getaBoolean() {
            return aBoolean;
        }

        public void setaBoolean(Boolean aBoolean) {
            this.aBoolean = aBoolean;
        }
    }

    @CsvRecord(separator = ";", quoting = true, quotingOnlyWhenNeeded = true)
    public static class BindyCsvRowFormat191433 implements Serializable {
        private static final long serialVersionUID = 1L;
        @DataField(pos = 1)
        private String firstField;
        @DataField(pos = 2)
        private String secondField;
        @DataField(pos = 3, pattern = "#.##")
        private Double aNumber;
        @DataField(pos = 4)
        private Boolean aBoolean;

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

        public Double getaNumber() {
            return aNumber;
        }

        public void setaNumber(Double aNumber) {
            this.aNumber = aNumber;
        }

        public Boolean getaBoolean() {
            return aBoolean;
        }

        public void setaBoolean(Boolean aBoolean) {
            this.aBoolean = aBoolean;
        }
    }

    @CsvRecord(separator = ",", quoting = true, quotingOnlyWhenNeeded = true, crlf = "UNIX")
    public static class BindyCsvRowFormat191434 implements Serializable {
        private static final long serialVersionUID = 1L;
        @DataField(pos = 1)
        private String firstField;
        @DataField(pos = 2)
        private String secondField;
        @DataField(pos = 3, pattern = "#.##")
        private Double aNumber;
        @DataField(pos = 4)
        private Boolean aBoolean;

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

        public Double getaNumber() {
            return aNumber;
        }

        public void setaNumber(Double aNumber) {
            this.aNumber = aNumber;
        }

        public Boolean getaBoolean() {
            return aBoolean;
        }

        public void setaBoolean(Boolean aBoolean) {
            this.aBoolean = aBoolean;
        }
    }
}
