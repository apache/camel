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
package org.apache.camel.dataformat.bindy.csv2;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.util.ConverterUtils;
import org.apache.camel.model.dataformat.BindyType;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 *
 */
public class BindyMarshalWithQuoteTest extends CamelTestSupport {

    @Test
    public void testBindyMarshalWithQuote() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("\"123\",\"Wednesday, November 9, 2011\",\"Central California\"" + ConverterUtils.getStringCarriageReturn("WINDOWS"));

        WeatherModel model = new WeatherModel();
        model.setId(123);
        model.setDate("Wednesday, November 9, 2011");
        model.setPlace("Central California");

        template.sendBody("direct:start", model);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .marshal().bindy(BindyType.Csv, org.apache.camel.dataformat.bindy.csv2.WeatherModel.class)
                    .to("mock:result");
            }
        };
    }
}
