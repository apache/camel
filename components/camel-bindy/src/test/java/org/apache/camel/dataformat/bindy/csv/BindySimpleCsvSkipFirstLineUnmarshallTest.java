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

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration
public class BindySimpleCsvSkipFirstLineUnmarshallTest extends AbstractJUnit4SpringContextTests {

    @Produce(uri = "direct:start")
    protected ProducerTemplate template;

    private String record = "order nr,client ref,first name, last name,instrument code,instrument name,order type, instrument type, quantity,currency,date\r\n"
                            + "1,,Albert,Cartier,ISIN,BE12345678,SELL,,1500,EUR,08-01-2009\r\n" + "2,A1,,Preud'Homme,ISIN,XD12345678,BUY,,2500,USD,08-01-2009\r\n"
                            + "03,A2,Jacques,,,BE12345678,SELL,,1500,EUR,08-01-2009\r\n" + "04,A3,Michel,Dupond,,,BUY,,2500,USD,08-01-2009\r\n"
                            + "05,A4,Annie,Dutronc,ISIN,BE12345678,,,1500,EUR,08-01-2009\r\n" + "06,A5,Andr" + "\uc3a9" + ",Rieux,ISIN,XD12345678,SELL,Share,,USD,08-01-2009\r\n"
                            + "07,A6,Myl" + "\uc3a8" + "ne,Farmer,ISIN,BE12345678,BUY,1500,,,08-01-2009\r\n" + "08,A7,Eva,Longoria,ISIN,XD12345678,SELL,Share,2500,USD,\r\n"
                            + ",,,D,,BE12345678,SELL,,,,08-01-2009\r\n" + ",,,D,ISIN,BE12345678,,,,,08-01-2009\r\n" + ",,,D,ISIN,LU123456789,,,,,\r\n"
                            + "10,A8,Pauline,M,ISIN,XD12345678,SELL,Share,2500,USD,08-01-2009\r\n" + "10,A9,Pauline,M,ISIN,XD12345678,BUY,Share,2500.45,USD,08-01-2009";

    @EndpointInject(uri = "mock:result")
    private MockEndpoint resultEndpoint;

    @Test
    public void testUnMarshallMessage() throws Exception {
        template.sendBody(record);
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.assertIsSatisfied();
    }

    public static class ContextConfig extends RouteBuilder {
        BindyCsvDataFormat camelDataFormat = new BindyCsvDataFormat(org.apache.camel.dataformat.bindy.model.simple.oneclassandskipfirstline.Order.class);

        public void configure() {
            // from("file://src/test/data2")
            from("direct:start").unmarshal(camelDataFormat).to("mock:result");
        }

    }

}
