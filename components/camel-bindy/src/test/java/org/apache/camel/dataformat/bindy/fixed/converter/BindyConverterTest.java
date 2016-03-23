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
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration
public class BindyConverterTest extends CamelTestSupport {

    public static final String URI_DIRECT_MARSHALL         = "direct:marshall";
    public static final String URI_DIRECT_UNMARSHALL       = "direct:unmarshall";
    public static final String URI_MOCK_MARSHALL_RESULT    = "mock:marshall-result";
    public static final String URI_MOCK_UNMARSHALL_RESULT  = "mock:unmarshall-result";

    // *************************************************************************
    //
    // *************************************************************************

    @Produce(uri = URI_DIRECT_MARSHALL)
    private ProducerTemplate mtemplate;

    @EndpointInject(uri = URI_MOCK_MARSHALL_RESULT)
    private MockEndpoint mresult;

    @Produce(uri = URI_DIRECT_UNMARSHALL)
    private ProducerTemplate utemplate;

    @EndpointInject(uri = URI_MOCK_UNMARSHALL_RESULT)
    private MockEndpoint uresult;

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
        Exchange  exc  = uresult.getReceivedExchanges().get(0);
        DataModel data = exc.getIn().getBody(DataModel.class);

        Assert.assertEquals("0123456789", data.field1);
    }

    // *************************************************************************
    // ROUTES
    // *************************************************************************

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        RouteBuilder routeBuilder = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                BindyDataFormat bindy = new BindyDataFormat();
                bindy.setClassType(DataModel.class);
                bindy.setType(BindyType.Fixed);

                from(URI_DIRECT_MARSHALL)
                    .marshal(bindy)
                    .to(URI_MOCK_MARSHALL_RESULT);
                from(URI_DIRECT_UNMARSHALL)
                    .unmarshal().bindy(BindyType.Fixed, DataModel.class)
                    .to(URI_MOCK_UNMARSHALL_RESULT);
            }
        };

        return routeBuilder;
    }

    // *************************************************************************
    // DATA MODEL
    // *************************************************************************

    @FixedLengthRecord(length = 10, paddingChar = ' ')
    public static class DataModel {
        @DataField(pos =  1, length = 10, trim = true)
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
}
