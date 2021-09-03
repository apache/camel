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
package org.apache.camel.dataformat.bindy.fixed.multibytes;

import java.nio.charset.StandardCharsets;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.dataformat.bindy.annotation.FixedLengthRecord;
import org.apache.camel.dataformat.bindy.fixed.BindyFixedLengthDataFormat;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class BindyCountGraphemeTest extends CamelTestSupport {

    @Produce("direct:in")
    private ProducerTemplate producer;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    // *************************************************************************
    // TESTS
    // *************************************************************************

    /**
     * Let's test a situation where graphemes are not equals to code points.
     */
    @Test
    public void testCountGrapheme() throws Exception {

        // camel + man face palming => 2 graphemes
        String firstAndSecondGrapheme = "\uD83D\uDC2A\uD83E\uDD26\uD83C\uDFFC\u200D\u2642\uFE0F";
        // d with diaresis => 1 grapheme
        String thirdGrapheme = "d\u0308";

        result.expectedMessagesMatches(exchange -> {
            TestRecord record = exchange.getIn().getBody(TestRecord.class);
            boolean beginIsOk = firstAndSecondGrapheme.equals(record.getFirstAndSecondGrapheme());
            boolean endIsOk = thirdGrapheme.equals(record.getThirdGrapheme());
            return beginIsOk && endIsOk;
        });

        producer.sendBody(firstAndSecondGrapheme + thirdGrapheme);

        result.assertIsSatisfied();
    }

    // *************************************************************************
    // ROUTES
    // *************************************************************************

    @Override
    protected RouteBuilder createRouteBuilder() {
        RouteBuilder routeBuilder = new RouteBuilder() {

            @Override
            public void configure() {
                from("direct:in").setHeader(Exchange.CHARSET_NAME, constant(StandardCharsets.UTF_8.name()))
                        .unmarshal(new BindyFixedLengthDataFormat(TestRecord.class))
                        .to("mock:result");
            }
        };

        return routeBuilder;
    }

    @FixedLengthRecord(countGrapheme = true)
    public static class TestRecord {

        @DataField(pos = 1, length = 2)
        private String firstAndSecondGrapheme;

        @DataField(pos = 2, length = 1)
        private String thirdGrapheme;

        public String getFirstAndSecondGrapheme() {
            return firstAndSecondGrapheme;
        }

        public void setFirstAndSecondGrapheme(String firstAndSecondGrapheme) {
            this.firstAndSecondGrapheme = firstAndSecondGrapheme;
        }

        public String getThirdGrapheme() {
            return thirdGrapheme;
        }

        public void setThirdGrapheme(String thirdGrapheme) {
            this.thirdGrapheme = thirdGrapheme;
        }
    }
}
