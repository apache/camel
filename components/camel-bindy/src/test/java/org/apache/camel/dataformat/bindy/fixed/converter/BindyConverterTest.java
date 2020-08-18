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
package org.apache.camel.dataformat.bindy.fixed.converter;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.Format;
import org.apache.camel.dataformat.bindy.annotation.BindyConverter;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.dataformat.bindy.annotation.FixedLengthRecord;
import org.apache.camel.model.dataformat.BindyDataFormat;
import org.apache.camel.model.dataformat.BindyType;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ContextConfiguration
public class BindyConverterTest extends CamelTestSupport {

    public static final String URI_DIRECT_MARSHALL = "direct:marshall";
    public static final String URI_DIRECT_UNMARSHALL = "direct:unmarshall";
    public static final String URI_DIRECT_THROUGH = "direct:through";

    public static final String URI_MOCK_MARSHALL_RESULT = "mock:marshall-result";
    public static final String URI_MOCK_UNMARSHALL_RESULT = "mock:unmarshall-result";
    public static final String URI_MOCK_THROUGH = "mock:through-result";

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

    @Produce(URI_DIRECT_THROUGH)
    private ProducerTemplate ttemplate;

    @EndpointInject(URI_MOCK_THROUGH)
    private MockEndpoint tresult;

    // *************************************************************************
    // TEST
    // *************************************************************************

    @Test
    @DirtiesContext
    public void testMarshall() throws Exception {
        DataModel rec = new DataModel();
        rec.field1 = "0123456789";
        mresult.expectedBodiesReceived("9876543210\r\n");

        mtemplate.sendBody(rec);
        mresult.assertIsSatisfied();
    }

    @Test
    @DirtiesContext
    public void testUnMarshall() throws Exception {
        utemplate.sendBody("9876543210\r\n");

        uresult.expectedMessageCount(1);
        uresult.assertIsSatisfied();

        // check the model
        Exchange exc = uresult.getReceivedExchanges().get(0);
        DataModel data = exc.getIn().getBody(DataModel.class);

        assertEquals("0123456789", data.field1);
    }

    @Test
    @DirtiesContext
    public void testRightAlignedNotTrimmed() throws Exception {
        AllCombinations data = sendAndReceiveAllCombinations();

        assertThat("Right aligned, padding not trimmed", data.field1, is("!!!f1"));
    }

    @Test
    @DirtiesContext
    public void testLeftAlignedNotTrimmed() throws Exception {
        AllCombinations data = sendAndReceiveAllCombinations();

        assertThat("Left aligned, padding not trimmed", data.field2, is("f2!!!"));
    }

    @Test
    @DirtiesContext
    public void testRightAlignedTrimmed() throws Exception {
        AllCombinations data = sendAndReceiveAllCombinations();

        assertThat("Right aligned, padding trimmed", data.field3, is("f3"));
    }

    @Test
    @DirtiesContext
    public void testLeftAlignedTrimmed() throws Exception {
        AllCombinations data = sendAndReceiveAllCombinations();

        assertThat("Left aligned, padding trimmed", data.field4, is("f4"));
    }

    @Test
    @DirtiesContext
    public void testRightAlignedRecordPaddingNotTrimmed() throws Exception {
        AllCombinations data = sendAndReceiveAllCombinations();

        assertThat("Right aligned, padding not trimmed", data.field5, is("###f5"));
    }

    @Test
    @DirtiesContext
    public void testLeftAlignedRecordPaddingNotTrimmed() throws Exception {
        AllCombinations data = sendAndReceiveAllCombinations();

        assertThat("Left aligned, padding not trimmed", data.field6, is("f6###"));
    }

    @Test
    @DirtiesContext
    public void testRightAlignedRecordPaddingTrimmed() throws Exception {
        AllCombinations data = sendAndReceiveAllCombinations();

        assertThat("Right aligned, padding trimmed", data.field7, is("f7"));
    }

    @Test
    @DirtiesContext
    public void testLeftAlignedRecordPaddingTrimmed() throws Exception {
        AllCombinations data = sendAndReceiveAllCombinations();

        assertThat("Left aligned, padding trimmed", data.field8, is("f8"));
    }

    private AllCombinations sendAndReceiveAllCombinations() throws InterruptedException {
        AllCombinations all = new AllCombinations();
        all.field1 = "f1";
        all.field2 = "f2";
        all.field3 = "f3";
        all.field4 = "f4";
        all.field5 = "f5";
        all.field6 = "f6";
        all.field7 = "f7";
        all.field8 = "f8";
        ttemplate.sendBody(all);

        tresult.expectedMessageCount(1);
        tresult.assertIsSatisfied();

        Exchange exc = tresult.getReceivedExchanges().get(0);
        return exc.getIn().getBody(AllCombinations.class);
    }

    // *************************************************************************
    // ROUTES
    // *************************************************************************

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        RouteBuilder routeBuilder = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                BindyDataFormat bindy = new BindyDataFormat()
                        .classType(DataModel.class)
                        .type(BindyType.Fixed);

                from(URI_DIRECT_MARSHALL)
                        .marshal(bindy)
                        .to(URI_MOCK_MARSHALL_RESULT);
                from(URI_DIRECT_UNMARSHALL)
                        .unmarshal().bindy(BindyType.Fixed, DataModel.class)
                        .to(URI_MOCK_UNMARSHALL_RESULT);

                BindyDataFormat bindy2 = new BindyDataFormat()
                        .classType(AllCombinations.class)
                        .type(BindyType.Fixed);
                from(URI_DIRECT_THROUGH)
                        .marshal(bindy2)
                        .unmarshal().bindy(BindyType.Fixed, AllCombinations.class)
                        .to(URI_MOCK_THROUGH);
            }
        };

        return routeBuilder;
    }

    // *************************************************************************
    // DATA MODEL
    // *************************************************************************

    @FixedLengthRecord(length = 10, paddingChar = ' ')
    public static class DataModel {
        @DataField(pos = 1, length = 10, trim = true)
        @BindyConverter(CustomConverter.class)
        public String field1;
    }

    public static class CustomConverter implements Format<String> {
        @Override
        public String format(String object) throws Exception {
            return (new StringBuilder(object)).reverse().toString();
        }

        @Override
        public String parse(String string) throws Exception {
            return (new StringBuilder(string)).reverse().toString();
        }
    }

    @FixedLengthRecord(length = 60, paddingChar = '#', ignoreMissingChars = true)
    public static class AllCombinations {
        @DataField(pos = 1, length = 5, paddingChar = '!')
        public String field1;

        @DataField(pos = 2, length = 5, paddingChar = '!', align = "L")
        public String field2;

        @DataField(pos = 3, length = 5, paddingChar = '#', trim = true)
        public String field3;

        @DataField(pos = 4, length = 5, paddingChar = '#', align = "L", trim = true)
        public String field4;

        @DataField(pos = 5, length = 5, paddingChar = 0)
        public String field5;

        @DataField(pos = 6, length = 5, paddingChar = 0, align = "L")
        public String field6;

        @DataField(pos = 7, length = 5, paddingChar = 0, trim = true)
        public String field7;

        @DataField(pos = 8, length = 5, paddingChar = 0, align = "L", trim = true)
        public String field8;
    }
}
