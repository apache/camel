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

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.component.spring.ws.util.FileUtil;
import org.apache.camel.test.junit4.CamelSpringTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.core.SoapActionCallback;

public class ConsumerEndpointMappingResponseHandlingRouteTest extends CamelSpringTestSupport {

    private final String xmlRequestForGoogleStockQuote = "<GetQuote xmlns=\"http://www.webserviceX.NET/\"><symbol>GOOG</symbol></GetQuote>";
    private final String xmlRequestForGoogleStockQuoteNoNamespace = "<GetQuote><symbol>GOOG</symbol></GetQuote>";
    private final String xmlRequestForGoogleStockQuoteNoNamespaceDifferentBody = "<GetQuote><symbol>GRABME</symbol></GetQuote>";

    private String expectedResponse;
    private WebServiceTemplate webServiceTemplate;

    public ConsumerEndpointMappingResponseHandlingRouteTest() throws IOException {
        expectedResponse = toUnixLineEndings(FileUtil.readFileAsString("/stockquote-response.xml"));
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        webServiceTemplate = (WebServiceTemplate) applicationContext.getBean("webServiceTemplate");
    }
    
    public String toUnixLineEndings(String inSt) {
    	return inSt.replace("\r\n", "\n");
    }

    @Test
    public void testRootQName() throws Exception {
        StreamSource source = new StreamSource(new StringReader(xmlRequestForGoogleStockQuote));
        StringWriter sw = new StringWriter();
        StreamResult result = new StreamResult(sw);
        webServiceTemplate.sendSourceAndReceiveToResult(source, result);
        assertNotNull(result);
        assertEquals(expectedResponse, toUnixLineEndings(sw.toString()));
    }

    @Test
    public void testSoapAction() throws Exception {
        StreamSource source = new StreamSource(new StringReader(xmlRequestForGoogleStockQuoteNoNamespace));
        StringWriter sw = new StringWriter();
        StreamResult result = new StreamResult(sw);
        webServiceTemplate.sendSourceAndReceiveToResult(source, new SoapActionCallback("http://www.webserviceX.NET/GetQuote"), result);
        assertNotNull(result);
        assertEquals(expectedResponse, toUnixLineEndings(sw.toString()));
    }

    @Test
    public void testUri() throws Exception {
        StreamSource source = new StreamSource(new StringReader(xmlRequestForGoogleStockQuoteNoNamespace));
        StringWriter sw = new StringWriter();
        StreamResult result = new StreamResult(sw);
        webServiceTemplate.sendSourceAndReceiveToResult("http://localhost/stockquote2", source, result);
        assertNotNull(result);
        assertEquals(expectedResponse, toUnixLineEndings(sw.toString()));
    }

    @Test
    public void testXPath() throws Exception {
        StreamSource source = new StreamSource(new StringReader(xmlRequestForGoogleStockQuoteNoNamespaceDifferentBody));
        StringWriter sw = new StringWriter();
        StreamResult result = new StreamResult(sw);
        webServiceTemplate.sendSourceAndReceiveToResult(source, result);
        assertNotNull(result);
        assertEquals(expectedResponse, toUnixLineEndings(sw.toString()));
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "org/apache/camel/component/spring/ws/ConsumerEndpointMappingResponseHandlingRouteTest-context.xml");
    }
}
