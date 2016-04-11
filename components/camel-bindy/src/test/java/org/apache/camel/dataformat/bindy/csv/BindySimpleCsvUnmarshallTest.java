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
import org.apache.camel.LoggingLevel;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.format.FormatException;
import org.apache.camel.dataformat.bindy.model.simple.oneclass.Order;
import org.apache.camel.processor.interceptor.Tracer;
import org.apache.camel.test.junit4.TestSupport;
import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

@ContextConfiguration
public class BindySimpleCsvUnmarshallTest extends AbstractJUnit4SpringContextTests {

    private static final String URI_MOCK_RESULT = "mock:result";
    private static final String URI_MOCK_ERROR = "mock:error";
    private static final String URI_DIRECT_START = "direct:start";

    @Produce(uri = URI_DIRECT_START)
    private ProducerTemplate template;

    @EndpointInject(uri = URI_MOCK_RESULT)
    private MockEndpoint result;

    @EndpointInject(uri = URI_MOCK_ERROR)
    private MockEndpoint error;

    private String expected;

    @Test
    @DirtiesContext
    public void testUnMarshallMessage() throws Exception {

        expected = "01,,Albert,Cartier,ISIN,BE12345678,SELL,,1500,EUR,08-01-2009\r\n" + "02,A1,,Preud'Homme,ISIN,XD12345678,BUY,,2500,USD,08-01-2009\r\n"
                   + "03,A2,Jacques,,,BE12345678,SELL,,1500,EUR,08-01-2009\r\n" + "04,A3,Michel,Dupond,,,BUY,,2500,USD,08-01-2009\r\n"
                   + "05,A4,Annie,Dutronc,ISIN,BE12345678,,,1500,EUR,08-01-2009\r\n" + "06,A5,Andr" + "\uc3a9" + ",Rieux,ISIN,XD12345678,SELL,Share,,USD,08-01-2009\r\n"
                   + "07,A6,Myl" + "\uc3a8" + "ne,Farmer,ISIN,BE12345678,BUY,1500,,,08-01-2009\r\n" + "08,A7,Eva,Longoria,ISIN,XD12345678,SELL,Share,2500,USD,\r\n"
                   + ",,,D,,BE12345678,SELL,,,,08-01-2009\r\n" + ",,,D,ISIN,BE12345678,,,,,08-01-2009\r\n" + ",,,D,ISIN,LU123456789,,,,,\r\n"
                   + "10,A8,Pauline,M,ISIN,XD12345678,SELL,Share,2500,USD,08-01-2009\r\n" + "10,A9,Pauline,M,ISIN,XD12345678,BUY,Share,2500.45,USD,08-01-2009";

        template.sendBody(expected);

        result.expectedMessageCount(1);
        result.assertIsSatisfied();

        /*
         * List<Exchange> exchanges = resultEndpoint.getExchanges();
         * for(Exchange exchange : exchanges) { Object body =
         * exchange.getOut().getBody(); LOG.debug("Body received : " +
         * body.toString()); }
         */

    }

    @Test
    @DirtiesContext
    public void testMessageWithErroneousDate() throws Exception {

        // Erroneous date
        expected = "1,B2,Keira,Knightley,ISIN,XX23456789,BUY,Share,400.25,EUR,14-01-2009-01\r\n";
        template.sendBody(expected);

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
    
    @SuppressWarnings("unchecked")
    @Test
    @DirtiesContext
    public void testUnMarshallMessageWithMissingFields() throws Exception {
       
        // We suppress the firstName field of the first record
        expected = "01,,,Cartier,ISIN,BE12345678,SELL,,1500,EUR\r\n" + "02,A1,,Preud'Homme,ISIN,XD12345678,BUY,,2500,USD,08-01-2009\r\n"
            + "03,A2,Jacques,,,BE12345678,SELL,,1500,EUR,08-01-2009\r\n" + "04,A3,Michel,Dupond,,,BUY,,2500,USD,08-01-2009\r\n"
            + "05,A4,Annie,Dutronc,ISIN,BE12345678,,,1500,EUR,08-01-2009\r\n" + "06,A5,Andr" + "\uc3a9" + ",Rieux,ISIN,XD12345678,SELL,Share,,USD,08-01-2009\r\n"
            + "07,A6,Myl" + "\uc3a8" + "ne,Farmer,ISIN,BE12345678,BUY,1500,,,08-01-2009\r\n" + "08,A7,Eva,Longoria,ISIN,XD12345678,SELL,Share,2500,USD,\r\n"
            + ",,,D,,BE12345678,SELL,,,,08-01-2009\r\n" + ",,,D,ISIN,BE12345678,,,,,08-01-2009\r\n" + ",,,D,ISIN,LU123456789,,,,,\r\n"
            + "10,A8,Pauline,M,ISIN,XD12345678,SELL,Share,2500,USD,08-01-2009\r\n" + "10,A9,Pauline,M,ISIN,XD12345678,BUY,Share,2500.45";

        template.sendBody(expected);

        List<Order> orders = (List<Order>) result.getExchanges().get(0).getIn().getBody();
        
        result.expectedMessageCount(1);
        result.assertIsSatisfied();

        assertNotNull(orders);
        // As the @DataField defines a default value for the firstName, the
        // value might not be empty
        assertFalse(orders.get(0).getFirstName().isEmpty());
    }
    
    public static class ContextConfig extends RouteBuilder {
        BindyCsvDataFormat camelDataFormat = new BindyCsvDataFormat(org.apache.camel.dataformat.bindy.model.simple.oneclass.Order.class);

        public void configure() {
            // from("file://src/test/data?move=./target/done").unmarshal(camelDataFormat).to("mock:result");

            Tracer tracer = new Tracer();
            tracer.setLogLevel(LoggingLevel.ERROR);
            tracer.setLogName("org.apache.camel.bindy");

            getContext().addInterceptStrategy(tracer);

            // default should errors go to mock:error
            errorHandler(deadLetterChannel(URI_MOCK_ERROR).redeliveryDelay(0));

            onException(Exception.class).maximumRedeliveries(0).handled(true);

            from(URI_DIRECT_START).unmarshal(camelDataFormat).to(URI_MOCK_RESULT);
        }

    }
}
