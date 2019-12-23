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
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.format.FormatException;
import org.apache.camel.test.junit4.TestSupport;
import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertEquals;

@ContextConfiguration
public class BindySimpleCsvUnmarshallPositionModifiedTest extends AbstractJUnit4SpringContextTests {

    private static final String URI_MOCK_RESULT = "mock:result";
    private static final String URI_MOCK_ERROR = "mock:error";
    private static final String URI_DIRECT_START = "direct:start";

    @Produce(URI_DIRECT_START)
    protected ProducerTemplate template;

    private String record;

    @EndpointInject(URI_MOCK_RESULT)
    private MockEndpoint result;

    @EndpointInject(URI_MOCK_ERROR)
    private MockEndpoint error;

    @Test
    @DirtiesContext
    public void testUnMarshallMessage() throws Exception {

        record = "1,25,Albert,Cartier,ISIN,BE12345678,SELL,Share,1500,EUR,08-01-2009\r\n";

        template.sendBody(record);

        result.expectedMessageCount(1);
        result.assertIsSatisfied();

    }

    @Test
    @DirtiesContext
    public void testUnmarshallErrorMessage() throws Exception {
        record = "1,25,Albert,Cartier,ISIN,BE12345678,SELL,Share,1500,EUR,08-01-2009-01\r\n";

        template.sendBody(record);

        // We don't expect to have a message as an error will be raised
        result.expectedMessageCount(0);

        // Message has been delivered to the mock error
        error.expectedMessageCount(1);

        result.assertIsSatisfied();
        error.assertIsSatisfied();

        // and check that we have the caused exception stored
        Exception cause = error.getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        TestSupport.assertIsInstanceOf(FormatException.class, cause.getCause());
        assertEquals("Date provided does not fit the pattern defined, position: 11, line: 1", cause.getMessage());

    }

    public static class ContextConfig extends RouteBuilder {

        BindyCsvDataFormat orderBindyDataFormat = new BindyCsvDataFormat(org.apache.camel.dataformat.bindy.model.simple.oneclassdifferentposition.Order.class);

        @Override
        public void configure() {

            // default should errors go to mock:error
            errorHandler(deadLetterChannel(URI_MOCK_ERROR).redeliveryDelay(0));

            onException(Exception.class).maximumRedeliveries(0).handled(true);

            from(URI_DIRECT_START).unmarshal(orderBindyDataFormat).to(URI_MOCK_RESULT);

        }
          
    }
}
