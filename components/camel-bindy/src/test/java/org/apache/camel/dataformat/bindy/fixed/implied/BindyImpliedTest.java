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
package org.apache.camel.dataformat.bindy.fixed.implied;

import java.math.BigDecimal;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.dataformat.bindy.annotation.FixedLengthRecord;
import org.apache.camel.model.dataformat.BindyDataFormat;
import org.apache.camel.model.dataformat.BindyType;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration
public class BindyImpliedTest extends AbstractJUnit4SpringContextTests {

    public static final String URI_DIRECT_MARSHALL         = "direct:marshall";
    public static final String URI_DIRECT_UNMARSHALL       = "direct:unmarshall";
    public static final String URI_MOCK_MARSHALL_RESULT    = "mock:marshall-result";
    public static final String URI_MOCK_UNMARSHALL_RESULT  = "mock:unmarshall-result";

    // *************************************************************************
    //
    // *************************************************************************

    @Produce(URI_DIRECT_MARSHALL)
    private ProducerTemplate mtemplate;

    @EndpointInject(URI_MOCK_MARSHALL_RESULT)
    private MockEndpoint mresult;

    @Produce(URI_DIRECT_UNMARSHALL)
    private ProducerTemplate utemplate;

    @EndpointInject(URI_MOCK_UNMARSHALL_RESULT)
    private MockEndpoint uresult;

    // *************************************************************************
    // TEST
    // *************************************************************************

    @Test
    @DirtiesContext
    public void testMarshall() throws Exception {

        Record rec = new Record();
        rec.setField1(123.45);
        rec.setField2(67.89);
        rec.setField3(11.24F);
        rec.setField4(33.45F);
        rec.setField5(new BigDecimal(60.52));
        rec.setField6(new BigDecimal(70.62));

        mresult.expectedBodiesReceived("1234567.89 112433.45 605270.62\r\n");

        mtemplate.sendBody(rec);
        mresult.assertIsSatisfied();
    }

    @Test
    @DirtiesContext
    public void testUnMarshall() throws Exception {

        utemplate.sendBody("1234567.89 112433.45 605270.62");

        uresult.expectedMessageCount(1);
        uresult.assertIsSatisfied();

        // check the model
        Exchange exc  = uresult.getReceivedExchanges().get(0);
        Record   data = exc.getIn().getBody(Record.class);

        Assert.assertEquals(123.45D, data.getField1(), 0D);
        Assert.assertEquals(67.89D, data.getField2(), 0D);
        Assert.assertEquals(11.24F, data.getField3(), 0.001);
        Assert.assertEquals(33.45F, data.getField4(), 0.001);
        Assert.assertEquals(60.52D, data.getField5().doubleValue(), 0.001);
        Assert.assertEquals(70.62D, data.getField6().doubleValue(), 0.001);
    }

    // *************************************************************************
    // ROUTES
    // *************************************************************************

    public static class ContextConfig extends RouteBuilder {
        @Override
        public void configure() {
            BindyDataFormat bindy = new BindyDataFormat();
            bindy.setClassType(Record.class);
            bindy.setLocale("en");
            bindy.type(BindyType.Fixed);

            from(URI_DIRECT_MARSHALL)
                .marshal(bindy)
                .to(URI_MOCK_MARSHALL_RESULT);
            from(URI_DIRECT_UNMARSHALL)
                .unmarshal().bindy(BindyType.Fixed, Record.class)
                .to(URI_MOCK_UNMARSHALL_RESULT);
        }
    }

    // *************************************************************************
    // DATA MODEL
    // *************************************************************************

    @FixedLengthRecord(length = 30, paddingChar = ' ')
    public static class Record {

        @DataField(pos =  1, length = 5, precision = 2, impliedDecimalSeparator = true)
        private Double field1;

        @DataField(pos =  6, length = 5, precision = 2)
        private Double field2;

        @DataField(pos = 11, length = 5, precision = 2, impliedDecimalSeparator = true)
        private Float field3;

        @DataField(pos = 16, length = 5, precision = 2)
        private Float field4;

        @DataField(pos = 21, length = 5, precision = 2, impliedDecimalSeparator = true)
        private BigDecimal field5;

        @DataField(pos = 26, length = 5, precision = 2)
        private BigDecimal field6;


        // *********************************************************************
        // GETTER/SETTERS
        // *********************************************************************

        public Double getField1() {
            return field1;
        }

        public void setField1(Double value) {
            this.field1 = value;
        }

        public Double getField2() {
            return field2;
        }

        public void setField2(Double value) {
            this.field2 = value;
        }

        public Float getField3() {
            return field3;
        }

        public void setField3(Float value) {
            this.field3 = value;
        }

        public Float getField4() {
            return field4;
        }

        public void setField4(Float value) {
            this.field4 = value;
        }

        public BigDecimal getField5() {
            return field5;
        }

        public void setField5(BigDecimal value) {
            this.field5 = value;
        }

        public BigDecimal getField6() {
            return field6;
        }

        public void setField6(BigDecimal value) {
            this.field6 = value;
        }

        // *********************************************************************
        // HELPERS
        // *********************************************************************

        @Override
        public String toString() {
            return "Record{"
                    +   "field1=<" + field1 + ">"
                    + ", field2=<" + field2 + ">"
                    + ", field3=<" + field3 + ">"
                    + ", field4=<" + field4 + ">"
                    + ", field5=<" + field6 + ">"
                    + ", field6=<" + field6 + ">"
                    + "}";
        }
    }
}
