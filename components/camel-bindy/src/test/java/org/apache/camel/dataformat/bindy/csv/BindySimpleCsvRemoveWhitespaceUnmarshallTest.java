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


import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertEquals;

@ContextConfiguration
public class BindySimpleCsvRemoveWhitespaceUnmarshallTest extends AbstractJUnit4SpringContextTests {

    @Produce(uri = "direct:start")
    protected ProducerTemplate template;

    private String record = "1 , 2,Albert,Cartier,ISIN,BE12345678,SELL,,1500,EUR,08-01-2009\r\n"
                            + ",,Jacques,,,BE12345678,SELL,,1500,EUR,08-01-2009";

    @EndpointInject(uri = "mock:result")
    private MockEndpoint resultEndpoint;

    @Test
    public void testUnMarshallMessage() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        
        template.sendBody(record);
        
        resultEndpoint.assertIsSatisfied();
        Exchange exchange = resultEndpoint.assertExchangeReceived(0);
        assertEquals(2, exchange.getIn().getBody(List.class).size());
    }

    public static class ContextConfig extends RouteBuilder {
        BindyCsvDataFormat camelDataFormat = new BindyCsvDataFormat(org.apache.camel.dataformat.bindy.model.simple.oneclassandremovewhitespace.Order.class);

        public void configure() {
            // from("file://src/test/data2")
            from("direct:start").unmarshal(camelDataFormat).to("mock:result");
        }
    }
}
