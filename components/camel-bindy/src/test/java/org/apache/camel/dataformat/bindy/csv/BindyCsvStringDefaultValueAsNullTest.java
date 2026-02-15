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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.model.csv.MyCsvRecord;
import org.apache.camel.model.dataformat.BindyType;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BindyCsvStringDefaultValueAsNullTest extends CamelTestSupport {

    @Test
    public void testAsStringDefaultValueAsNullTrue() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                var df = dataFormat().bindy().defaultValueStringAsNull(true).type(BindyType.Csv).classType(MyCsvRecord.class)
                        .end();
                from("direct:fromCsv").unmarshal(df).to("mock:result");
            }
        });

        MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);

        String addressLine1 = "8506 SIX FORKS ROAD,";
        String csvLine = "\"PROBLEM SOLVER\",\"" + addressLine1
                         + "\",\"SUITE 104\",\"RALEIGH\",\"\",\"27615\",\"\"";

        template.sendBody("direct:fromCsv", csvLine.trim());

        mock.assertIsSatisfied();

        MyCsvRecord rec = mock.getReceivedExchanges().get(0).getMessage().getBody(MyCsvRecord.class);
        Assertions.assertNotNull(rec);
        Assertions.assertEquals(addressLine1, rec.getAddressLine1());
        Assertions.assertNull(rec.getCountry());
        Assertions.assertNull(rec.getState());
        Assertions.assertEquals("27615", rec.getZip());
    }

    @Test
    public void testAsStringDefaultValueAsNullFalse() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                var df = dataFormat().bindy().defaultValueStringAsNull(false).type(BindyType.Csv).classType(MyCsvRecord.class)
                        .end();
                from("direct:fromCsv").unmarshal(df).to("mock:result");
            }
        });

        MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);

        String addressLine1 = "8506 SIX FORKS ROAD,";
        String csvLine = "\"PROBLEM SOLVER\",\"" + addressLine1
                         + "\",\"SUITE 104\",\"RALEIGH\",\"\",\"27615\",\"\"";

        template.sendBody("direct:fromCsv", csvLine.trim());

        mock.assertIsSatisfied();

        MyCsvRecord rec = mock.getReceivedExchanges().get(0).getMessage().getBody(MyCsvRecord.class);
        Assertions.assertNotNull(rec);
        Assertions.assertEquals(addressLine1, rec.getAddressLine1());
        Assertions.assertEquals("", rec.getCountry());
        Assertions.assertEquals("", rec.getState());
        Assertions.assertEquals("27615", rec.getZip());
    }

}
