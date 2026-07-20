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

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.model.dataformat.BindyDataFormat;
import org.apache.camel.model.dataformat.BindyType;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests that BigDecimal fields without an explicit precision attribute round-trip correctly (CAMEL-24168).
 */
public class BindyBigDecimalNoPrecisionTest extends CamelTestSupport {

    @Produce("direct:unmarshal")
    private ProducerTemplate unmarshalTemplate;

    @EndpointInject("mock:unmarshal-result")
    private MockEndpoint unmarshalResult;

    @Produce("direct:marshal")
    private ProducerTemplate marshalTemplate;

    @EndpointInject("mock:marshal-result")
    private MockEndpoint marshalResult;

    @Test
    public void testUnmarshalBigDecimalWithoutPrecision() throws Exception {
        unmarshalResult.expectedMessageCount(1);

        unmarshalTemplate.sendBody("10.35,foo");

        unmarshalResult.assertIsSatisfied();

        Exchange exchange = unmarshalResult.getReceivedExchanges().get(0);
        NoPrecisionModel model = exchange.getIn().getBody(NoPrecisionModel.class);
        assertNotNull(model);
        assertEquals(new BigDecimal("10.35"), model.amount);
        assertEquals("foo", model.label);
    }

    @Test
    public void testMarshalBigDecimalWithoutPrecision() throws Exception {
        marshalResult.expectedMessageCount(1);

        NoPrecisionModel model = new NoPrecisionModel();
        model.amount = new BigDecimal("10.35");
        model.label = "foo";

        marshalTemplate.sendBody(model);

        marshalResult.assertIsSatisfied();

        String body = marshalResult.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertEquals("10.35,foo\r\n", body);
    }

    @Test
    public void testUnmarshalBigDecimalWithExplicitPrecision() throws Exception {
        unmarshalResult.expectedMessageCount(1);

        unmarshalTemplate.sendBody("10.355,bar");

        unmarshalResult.assertIsSatisfied();

        Exchange exchange = unmarshalResult.getReceivedExchanges().get(0);
        NoPrecisionModel model = exchange.getIn().getBody(NoPrecisionModel.class);
        assertNotNull(model);
        // amount has no precision — original scale preserved
        assertEquals(new BigDecimal("10.355"), model.amount);
    }

    @Test
    public void testUnmarshalIntegerValueBigDecimal() throws Exception {
        unmarshalResult.expectedMessageCount(1);

        unmarshalTemplate.sendBody("10,bar");

        unmarshalResult.assertIsSatisfied();

        Exchange exchange = unmarshalResult.getReceivedExchanges().get(0);
        NoPrecisionModel model = exchange.getIn().getBody(NoPrecisionModel.class);
        assertNotNull(model);
        assertEquals(new BigDecimal("10"), model.amount);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                BindyDataFormat unmarshalBindy = new BindyDataFormat()
                        .type(BindyType.Csv)
                        .classType(NoPrecisionModel.class)
                        .locale("en");

                BindyDataFormat marshalBindy = new BindyDataFormat()
                        .type(BindyType.Csv)
                        .classType(NoPrecisionModel.class)
                        .locale("en");

                from("direct:unmarshal")
                        .unmarshal(unmarshalBindy)
                        .to("mock:unmarshal-result");

                from("direct:marshal")
                        .marshal(marshalBindy)
                        .to("mock:marshal-result");
            }
        };
    }

    @CsvRecord(separator = ",")
    public static class NoPrecisionModel {
        @DataField(pos = 1)
        public BigDecimal amount;

        @DataField(pos = 2)
        public String label;
    }
}
