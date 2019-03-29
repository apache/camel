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
package org.apache.camel.dataformat.soap;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPMessage;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.soap.name.TypeNameStrategy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class SoapToSoapDontIgnoreTest extends CamelTestSupport {
    private static SoapJaxbDataFormat soapjaxbModel;
    private static SoapJaxbDataFormat soapjaxbModelDontIgnoreUnmarshalled;
    private static Map<String, String> namespacePrefixMap;

    @BeforeClass
    public static void setup() {
        namespacePrefixMap = new HashMap<>();
        namespacePrefixMap.put("http://schemas.xmlsoap.org/soap/envelope/", "soap");
        namespacePrefixMap.put("http://www.w3.org/2001/XMLSchema", "xsd");
        namespacePrefixMap.put("http://www.w3.org/2001/XMLSchema-instance", "xsi");
        namespacePrefixMap.put("http://www.example.com/contact", "cont");
        namespacePrefixMap.put("http://www.example.com/soapheaders", "custom");
        soapjaxbModel = new SoapJaxbDataFormat("com.example.contact:com.example.soapheaders");
        soapjaxbModel.setNamespacePrefix(namespacePrefixMap);
        soapjaxbModel.setPrettyPrint(true);
        soapjaxbModel.setIgnoreUnmarshalledHeaders(false);
        soapjaxbModel.setIgnoreJAXBElement(false);
        soapjaxbModel.setElementNameStrategy(new TypeNameStrategy());
        soapjaxbModelDontIgnoreUnmarshalled = new SoapJaxbDataFormat(
                                                                     "com.example.contact:com.example.soapheaders");
        soapjaxbModelDontIgnoreUnmarshalled.setNamespacePrefix(namespacePrefixMap);
        soapjaxbModelDontIgnoreUnmarshalled.setPrettyPrint(true);
        soapjaxbModelDontIgnoreUnmarshalled.setIgnoreUnmarshalledHeaders(false);
        soapjaxbModelDontIgnoreUnmarshalled.setElementNameStrategy(new TypeNameStrategy());
    }

    @AfterClass
    public static void teardown() {
        soapjaxbModel = null;
        namespacePrefixMap = null;
    }

    @Test
    public void testSoapMarshal() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:end");
        endpoint.setExpectedMessageCount(1);

        template.sendBody("direct:start", createRequest());

        assertMockEndpointsSatisfied();
        Exchange result = endpoint.assertExchangeReceived(0);

        byte[] body = (byte[])result.getIn().getBody();
        InputStream stream = new ByteArrayInputStream(body);
        SOAPMessage request = MessageFactory.newInstance().createMessage(null, stream);
        assertTrue("Expected headers", null != request.getSOAPHeader()
                                       && request.getSOAPHeader().extractAllHeaderElements().hasNext());
    }

    private InputStream createRequest() throws Exception {
        InputStream stream = this.getClass().getResourceAsStream("SoapMarshalHeadersTest.xml");
        return stream;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        context.getGlobalOptions().put(Exchange.LOG_DEBUG_BODY_MAX_CHARS, "0");
        context.setTracing(true);

        return new RouteBuilder() {
            public void configure() {
                from("direct:start").unmarshal(soapjaxbModel).marshal(soapjaxbModelDontIgnoreUnmarshalled)
                    .to("mock:end");
            }
        };
    }
}
