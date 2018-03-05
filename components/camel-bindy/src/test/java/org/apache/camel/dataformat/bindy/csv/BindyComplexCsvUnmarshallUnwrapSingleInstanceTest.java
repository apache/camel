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
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.dataformat.BindyType;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration
public class BindyComplexCsvUnmarshallUnwrapSingleInstanceTest extends AbstractJUnit4SpringContextTests {

    private static final Class<?> TYPE = org.apache.camel.dataformat.bindy.model.complex.twoclassesandonelink.Order.class;

    @Produce(uri = "direct:start")
    protected ProducerTemplate template;

    private String singleRecord = "01,,Albert,Cartier,ISIN,BE12345678,SELL,,1500,EUR,08-01-2009\r\n";

    @EndpointInject(uri = "mock:result")
    private MockEndpoint resultEndpoint;

    @Test
    public void testUnMarshallSingleMessage() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        // should still be a list as we turned off unwrap
        resultEndpoint.message(0).body().isInstanceOf(List.class);

        template.sendBody(singleRecord);

        resultEndpoint.assertIsSatisfied();

        // there should be 1 element in the list
        List list = resultEndpoint.getReceivedExchanges().get(0).getIn().getBody(List.class);
        Assert.assertEquals(1, list.size());
    }

    public static class ContextConfig extends RouteBuilder {
        public void configure() {
            from("direct:start")
                .unmarshal().bindy(BindyType.Csv, TYPE, false)
                .to("mock:result");
        }
    }
}
