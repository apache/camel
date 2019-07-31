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
import java.math.BigDecimal;

import org.apache.camel.EndpointInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class BindyRecordFieldStartingWithSeperatorCharTest extends CamelTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint mockEndPoint;

    @Test
    public void testUnmarshallCsvRecordFieldStartingWithSeparatorChar() throws Exception {

        mockEndPoint.expectedMessageCount(4);

        template.sendBody("direct:start", "'val1',',val2',1");
        template.sendBody("direct:start", "',',',val2',2");
        template.sendBody("direct:start", "',','val2,',3");
        template.sendBody("direct:start", "'',',val2,',4");

        mockEndPoint.assertIsSatisfied();

        BindyCsvRowFormat row = mockEndPoint.getExchanges().get(0).getIn().getBody(BindyCsvRowFormat.class);
        assertEquals("val1", row.getFirstField());
        assertEquals(",val2", row.getSecondField());
        assertEquals(BigDecimal.valueOf(1), row.getNumber());

        row = mockEndPoint.getExchanges().get(1).getIn().getBody(BindyCsvRowFormat.class);
        assertEquals(",", row.getFirstField());
        assertEquals(",val2", row.getSecondField());
        assertEquals(BigDecimal.valueOf(2), row.getNumber());

        row = mockEndPoint.getExchanges().get(2).getIn().getBody(BindyCsvRowFormat.class);
        assertEquals(",", row.getFirstField());
        assertEquals("val2,", row.getSecondField());
        assertEquals(BigDecimal.valueOf(3), row.getNumber());

        row = mockEndPoint.getExchanges().get(3).getIn().getBody(BindyCsvRowFormat.class);
        assertEquals("", row.getFirstField());
        assertEquals(",val2,", row.getSecondField());
        assertEquals(BigDecimal.valueOf(4), row.getNumber());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                BindyCsvDataFormat camelDataFormat =
                    new BindyCsvDataFormat(BindyCsvRowFormat.class);
                from("direct:start").unmarshal(camelDataFormat).to("mock:result");
            }
        };
    }

    //from https://issues.apache.org/jira/browse/CAMEL-11065
    @SuppressWarnings("serial")
    @CsvRecord(separator = ",", quote = "'")
    public static class BindyCsvRowFormat implements Serializable {

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
