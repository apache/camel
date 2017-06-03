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
package org.apache.camel.dataformat.bindy.number.rounding;

import java.math.BigDecimal;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.model.dataformat.BindyDataFormat;
import org.apache.camel.model.dataformat.BindyType;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Assert;
import org.junit.Test;


public class BindyBigDecimalRoundingUnmarshallTest extends CamelTestSupport {

    private static final String URI_MOCK_RESULT = "mock:result";
    private static final String URI_DIRECT_START = "direct:start";

    @Produce(uri = URI_DIRECT_START)
    private ProducerTemplate template;

    @EndpointInject(uri = URI_MOCK_RESULT)
    private MockEndpoint result;

    private String record;


    @Test
    public void testBigDecimalRoundingUp() throws Exception {

        record = "'12345.789'";
        String bigDecimal = "12345.79";

        template.sendBody(record);

        result.expectedMessageCount(1);
        result.assertIsSatisfied();

        NumberModel bd = (NumberModel)result.getExchanges().get(0).getIn().getBody();
        Assert.assertEquals(bigDecimal, bd.getRoundingUp().toString());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                BindyDataFormat bindy = new BindyDataFormat();
                bindy.setType(BindyType.Csv);
                bindy.setClassType(NumberModel.class);
                bindy.setLocale("en");

                from(URI_DIRECT_START)
                    .unmarshal(bindy)
                    .to(URI_MOCK_RESULT);
            }

        };
    }

    @CsvRecord(separator = ",", quote = "'")
    public static class NumberModel {

        @DataField(pos = 1, precision = 2, rounding = "UP", pattern = "#####,##")
        private BigDecimal roundingUp;

        public BigDecimal getRoundingUp() {
            return roundingUp;
        }

        public void setRoundingUp(BigDecimal roundingUp) {
            this.roundingUp = roundingUp;
        }

        @Override
        public String toString() {
            return "BigDecimal rounding : " + this.roundingUp;
        }
    }
}
