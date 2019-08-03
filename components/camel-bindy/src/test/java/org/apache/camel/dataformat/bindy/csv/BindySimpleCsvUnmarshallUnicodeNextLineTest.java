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
import org.apache.camel.dataformat.bindy.model.unicode.LocationRecord;
import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@ContextConfiguration
public class BindySimpleCsvUnmarshallUnicodeNextLineTest extends AbstractJUnit4SpringContextTests {
    private static final String URI_MOCK_RESULT = "mock:result";
    private static final String URI_DIRECT_START = "direct:start";

    @Produce(URI_DIRECT_START)
    protected ProducerTemplate template;

    @EndpointInject(URI_MOCK_RESULT)
    private MockEndpoint result;

    private String record;

    @Test
    @DirtiesContext
    public void testUnicodeNextLineCharacterParsing() throws Exception {
        record = "123\u0085 Anywhere Lane,United States";

        template.sendBody(record);

        result.expectedMessageCount(1);
        result.assertIsSatisfied();
        LocationRecord data = result.getExchanges().get(0).getIn().getBody(LocationRecord.class);
        assertNotNull(data);
        assertEquals("Parsing error with unicode next line", "123\u0085 Anywhere Lane", data.getAddress());
        assertEquals("Parsing error with unicode next line", "United States", data.getNation());
    }

    public static class ContextConfig extends RouteBuilder {
        BindyCsvDataFormat locationRecordBindyDataFormat = new BindyCsvDataFormat(LocationRecord.class);

        @Override
        public void configure() {
            from(URI_DIRECT_START)
                    .unmarshal(locationRecordBindyDataFormat)
                    .to(URI_MOCK_RESULT);
        }
    }

}
