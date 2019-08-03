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

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration
public class BindyDoNotRemoveQuotesCsvUnmarshallTest extends AbstractJUnit4SpringContextTests {

    private static final String URI_MOCK_RESULT = "mock:result";
    private static final String URI_DIRECT_START = "direct:start";

    @Produce(URI_DIRECT_START)
    private ProducerTemplate template;

    @EndpointInject(URI_MOCK_RESULT)
    private MockEndpoint result;

    private String expected;

    // Without removesQuotes=false annotation on product this will fail to
    // unmarshall properly
    @Test
    @DirtiesContext
    public void testUnMarshallMessage() throws Exception {

        expected = "apple,\"bright red\" apple,a fruit";

        template.sendBody(expected);

        result.expectedMessageCount(1);
        result.assertIsSatisfied();
        BindyDoNotRemoveQuotesCsvUnmarshallTest.Product product = result.getReceivedExchanges().get(0).getIn().getBody(BindyDoNotRemoveQuotesCsvUnmarshallTest.Product.class);
        Assert.assertEquals("apple", product.getName());
        Assert.assertEquals("\"bright red\" apple", product.getDescription1());
        Assert.assertEquals("a fruit", product.getDescription2());
    }

    public static class ContextConfig extends RouteBuilder {
        BindyCsvDataFormat camelDataFormat = new BindyCsvDataFormat(BindyDoNotRemoveQuotesCsvUnmarshallTest.Product.class);

        @Override
        public void configure() {
            from(URI_DIRECT_START).unmarshal(camelDataFormat).to(URI_MOCK_RESULT);
        }
    }

    @CsvRecord(separator = ",", removeQuotes = false)
    public static class Product {

        @DataField(pos = 1)
        private String name;

        @DataField(pos = 2)
        private String description1;

        @DataField(pos = 3)
        private String description2;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription1() {
            return description1;
        }

        public void setDescription1(String description1) {
            this.description1 = description1;
        }

        public String getDescription2() {
            return description2;
        }

        public void setDescription2(String description2) {
            this.description2 = description2;
        }

        @Override
        public String toString() {
            return "Product{" + "name='" + name + '\'' + ", description1='" + description1 + '\'' + ", description2='" + description2 + '\'' + '}';
        }
    }

}
