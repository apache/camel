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
package org.apache.camel.component.spring.ws;
import javax.activation.DataHandler;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;

import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertNotNull;

@ContextConfiguration
public class SoapResponseAttachmentTest extends AbstractJUnit4SpringContextTests {

    private final String xmlRequestForGoogleStockQuote = "<GetQuote xmlns=\"http://www.webserviceX.NET/\"><symbol>GOOG</symbol></GetQuote>";
    private final String soapHeader = "<h:Header xmlns:h=\"http://www.webserviceX.NET/\"><h:MessageID>1234567890</h:MessageID><h:Nested><h:NestedID>1111</h:NestedID></h:Nested></h:Header>";

    @Produce
    private ProducerTemplate template;

    /**
     * This tests if attachments, returned by a spring-ws request, are populated into the exchange. 
     * The SOAP attachments are populated by the SoapAttachmentResponseProcessor. 
     * Which adds 2 response attachments.
     * Note: 'allowResponseAttachmentOverride=true' must be set!
     *
     * @throws Exception
     */
    @Test()
    public void consumeStockQuoteWebserviceWithSoapResponseAttachment() throws Exception {
        Exchange result = template.request("direct:stockQuoteWebservice", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(xmlRequestForGoogleStockQuote);
                exchange.getIn().setHeader(SpringWebserviceConstants.SPRING_WS_SOAP_HEADER, soapHeader);
                exchange.getIn().addAttachment("requestAttachment1.txt", new DataHandler("hello attachment!", "text/plain"));
            }
        });
        assertNotNull(result);
        assertNotNull(result.getOut().getAttachment("requestAttachment1.txt"));
        assertNotNull(result.getOut().getAttachment("responseAttachment1.txt"));
        assertNotNull(result.getOut().getAttachment("responseAttachment2.xml"));
    }
}
