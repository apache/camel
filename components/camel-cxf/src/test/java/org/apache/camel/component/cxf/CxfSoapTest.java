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
package org.apache.camel.component.cxf;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;

import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.helpers.XMLUtils;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.staxutils.StaxUtils;

public class CxfSoapTest extends ContextTestSupport {
    private static final String SOAP_STRING =
        "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
        + "<soap:Body><testMethod xmlns=\"http://camel.apache.org/testService\" />"
        + "</soap:Body></soap:Envelope>";

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                //from("soap:direct:consumer?soap.wsdl=classpath:hello/HelloWorld-DOC.wsdl").to("mock:consumer");
                from("soap:direct:consumer?soap.wsdl=classpath:hello/HelloWorld-DOC.wsdl").to("mock:consumer").process(new Processor() {
                    public void process(Exchange e) {
                        Object result = e.getIn().getBody();
                        e.getOut().setBody(result);
                    }
                });
                from("direct:producer").to("soap:mock:producer?soap.wsdl=classpath:hello/HelloWorld-DOC.wsdl");
            }
        };
    }

    public void testSoapConsumer() throws Exception {
        // send out the request message
        URL request = this.getClass().getResource("SoapRequest.xml");
        File requestFile = new File(request.toURI());
        FileInputStream inputStream = new FileInputStream(requestFile);
        Object result = template.sendBody("direct:consumer", inputStream);
        assertFalse("The result should not be changed", inputStream.equals(result));
        assertTrue("result should be a inputstream", result instanceof InputStream);
        CachedOutputStream bos = new CachedOutputStream();
        IOUtils.copy((InputStream) result, bos);
        bos.flush();
        ((InputStream)result).close();
        assertEquals("We should find the soap body string here",
                   SOAP_STRING, bos.getOut().toString());


    }

    public void testSoapProducer() throws Exception {
        // set out the source message
        URL request = this.getClass().getResource("RequestBody.xml");
        File requestFile = new File(request.toURI());
        FileInputStream inputStream = new FileInputStream(requestFile);
        XMLStreamReader xmlReader = StaxUtils.createXMLStreamReader(inputStream);
        DOMSource source = new DOMSource(StaxUtils.read(xmlReader));
        MockEndpoint endpoint = getMockEndpoint("mock:producer");
        endpoint.expectedMessageCount(1);
        Object result = template.sendBody("direct:producer", source);

        assertMockEndpointsSatisifed();
        assertFalse("The result should not be changed", source.equals(result));
        assertTrue("The result should be the instance of DOMSource", result instanceof DOMSource);
        assertEquals("The DOMSource should be equal", XMLUtils.toString(source), XMLUtils.toString((Source)result));

    }
}