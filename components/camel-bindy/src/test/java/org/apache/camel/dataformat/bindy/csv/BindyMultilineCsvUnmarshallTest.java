/**
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
import java.util.List;

import junit.framework.TestCase;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration
public class BindyMultilineCsvUnmarshallTest extends AbstractJUnit4SpringContextTests {

    private static final String URI_MOCK_RESULT = "mock:result";
    private static final String URI_DIRECT_START = "direct:start";

    @Produce(uri = URI_DIRECT_START)
    private ProducerTemplate template;

    @EndpointInject(uri = URI_MOCK_RESULT)
    private MockEndpoint result;

    private String expected;

    @Test
    @DirtiesContext
    public void testMultilineCsvUnmarshall() throws Exception {

        expected = "ACDT,\"Australian Central Daylight Savings Time\r\nUTC+10:30\",,A,\"1000\",1000,1.000,\"1.000\",\r\n" 
                 + "\"BST\",\"British Summer Time\r\nUTC+01\",\"a.k.a British Standard Time \"\"from Feb, 1968 to Oct, 1971\"\"\",B,2000,\"2000\",\"2.000\",2.000,\"20.02\"\r\n" 
                 + "\"CDT\",\"Central Daylight Time\r\nUTC-05\",\"North America\",\"C\",,3000,,\"3.000\",\"30.03\"\r\n" 
                 + "\"AEST\",\"\"\"Australia\"\" Eastern Summer Time\r\nUTC+10\",E.g. Brisbane,,\"4000\",4000,4.000,\"4.000\",\"40.04\"\r\n" 
                 + "\"GMT\",\"Greenwich Mean Time\r\nGMT\",\"See also \"\"AZOST\"\", \"\"EGST\"\", \"\"UTC\"\" & \"\"WET\"\"\",E,\"5000\",,5.000,\"\",\"50.05\"\r\n";

        template.sendBody(expected);

        result.expectedMessageCount(1);
        result.assertIsSatisfied();

        final List<?> models = result.getReceivedExchanges().get(0).getIn().getBody(List.class);
        TestCase.assertEquals(5, models.size());

        final MultiLineModel acdt = (MultiLineModel) models.get(0);
        TestCase.assertEquals("ACDT", acdt.getName());
        TestCase.assertEquals("Australian Central Daylight Savings Time\r\nUTC+10:30", acdt.getDescription());
        TestCase.assertEquals(null, acdt.getComment());
        TestCase.assertEquals('A', acdt.getCharacter());
        TestCase.assertEquals(1000, acdt.getNumber1());
        TestCase.assertEquals(1000L, acdt.getNumber2());
        TestCase.assertEquals(1.000f, acdt.getNumber3());
        TestCase.assertEquals(1.000, acdt.getNumber4());
        TestCase.assertEquals(null, acdt.getNumber5());

        final MultiLineModel bst = (MultiLineModel) models.get(1);
        TestCase.assertEquals("BST", bst.getName());
        TestCase.assertEquals("British Summer Time\r\nUTC+01", bst.getDescription());
        TestCase.assertEquals("a.k.a British Standard Time \"from Feb, 1968 to Oct, 1971\"", bst.getComment());
        TestCase.assertEquals('B', bst.getCharacter());
        TestCase.assertEquals(2000, bst.getNumber1());
        TestCase.assertEquals(2000L, bst.getNumber2());
        TestCase.assertEquals(2.000f, bst.getNumber3());
        TestCase.assertEquals(2.000, bst.getNumber4());
        TestCase.assertEquals(new BigDecimal("20.02"), bst.getNumber5());

        final MultiLineModel cdt = (MultiLineModel) models.get(2);
        TestCase.assertEquals("CDT", cdt.getName());
        TestCase.assertEquals("Central Daylight Time\r\nUTC-05", cdt.getDescription());
        TestCase.assertEquals("North America", cdt.getComment());
        TestCase.assertEquals('C', cdt.getCharacter());
        TestCase.assertEquals(Integer.MIN_VALUE, cdt.getNumber1());
        TestCase.assertEquals(3000L, cdt.getNumber2());
        TestCase.assertEquals(Float.MIN_VALUE, cdt.getNumber3());
        TestCase.assertEquals(3.000, cdt.getNumber4());
        TestCase.assertEquals(new BigDecimal("30.03"), cdt.getNumber5());

        final MultiLineModel aest = (MultiLineModel) models.get(3);
        TestCase.assertEquals("AEST", aest.getName());
        TestCase.assertEquals("\"Australia\" Eastern Summer Time\r\nUTC+10", aest.getDescription());
        TestCase.assertEquals("E.g. Brisbane", aest.getComment());
        TestCase.assertEquals('\0', aest.getCharacter());
        TestCase.assertEquals(4000, aest.getNumber1());
        TestCase.assertEquals(4000L, aest.getNumber2());
        TestCase.assertEquals(4.000f, aest.getNumber3());
        TestCase.assertEquals(4.000, aest.getNumber4());
        TestCase.assertEquals(new BigDecimal("40.04"), aest.getNumber5());

        final MultiLineModel gmt = (MultiLineModel) models.get(4);
        TestCase.assertEquals("GMT", gmt.getName());
        TestCase.assertEquals("Greenwich Mean Time\r\nGMT", gmt.getDescription());
        TestCase.assertEquals("See also \"AZOST\", \"EGST\", \"UTC\" & \"WET\"", gmt.getComment());
        TestCase.assertEquals('E', gmt.getCharacter());
        TestCase.assertEquals(5000, gmt.getNumber1());
        TestCase.assertEquals(Long.MIN_VALUE, gmt.getNumber2());
        TestCase.assertEquals(5.000f, gmt.getNumber3());
        TestCase.assertEquals(Double.MIN_VALUE, gmt.getNumber4());
        TestCase.assertEquals(new BigDecimal("50.05"), gmt.getNumber5());
    }

    public static class ContextConfig extends RouteBuilder {
        BindyCsvDataFormat camelDataFormat = new BindyCsvDataFormat(MultiLineModel.class);

        public void configure() {
            from(URI_DIRECT_START).unmarshal(camelDataFormat).to(URI_MOCK_RESULT);
        }

    }

    @CsvRecord(separator = ",", multiLine = true)
    public static class MultiLineModel {

        @DataField(pos = 1)
        private String name;

        @DataField(pos = 2)
        private String description;

        @DataField(pos = 3)
        private String comment;

        @DataField(pos = 4)
        private char character;

        @DataField(pos = 5)
        private int number1;

        @DataField(pos = 6)
        private long number2;

        @DataField(pos = 7)
        private float number3;

        @DataField(pos = 8)
        private double number4;

        @DataField(pos = 9, precision = 2)
        private BigDecimal number5;

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getComment() {
            return comment;
        }

        public char getCharacter() {
            return character;
        }

        public int getNumber1() {
            return number1;
        }

        public long getNumber2() {
            return number2;
        }

        public float getNumber3() {
            return number3;
        }

        public double getNumber4() {
            return number4;
        }

        public BigDecimal getNumber5() {
            return number5;
        }

        @Override
        public String toString() {
            return "name=" + this.name + ",description=" + this.description + ",comment=" + this.comment
                    + ",character=" + character + ",number1=" + number1 + ",number2=" + number2
                    + ",number3=" + number3 + ",number4=" + number4 + ",number5=" + number5;
        }
    }

}
