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
package org.apache.camel.component.cxf;

import org.w3c.dom.Node;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class CxfConsumerProviderTest extends CamelTestSupport {
    
    
    protected static final String REQUEST_MESSAGE = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:ser=\"test/service\">"
        + "<soapenv:Header/><soapenv:Body><ser:ping/></soapenv:Body></soapenv:Envelope>";
    
    protected static final String RESPONSE_MESSAGE_BEGINE = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
        + "<soap:Body><pong xmlns=\"test/service\"";
    protected static final String RESPONSE_MESSAGE_END = "/></soap:Body></soap:Envelope>";
    
    protected static final String RESPONSE = "<pong xmlns=\"test/service\"/>";
  

    
    protected final String simpleEndpointAddress = "http://localhost:"
        + CXFTestSupport.getPort1() + "/" + getClass().getSimpleName() + "/test";
    protected final String simpleEndpointURI = "cxf://" + simpleEndpointAddress
        + "?serviceClass=org.apache.camel.component.cxf.ServiceProvider";
    
    
    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                errorHandler(noErrorHandler());
                from(getFromEndpointUri()).process(new Processor() {
                    public void process(final Exchange exchange) {
                        Message in = exchange.getIn();
                        Node node = in.getBody(Node.class);
                        assertNotNull(node);
                        XmlConverter xmlConverter = new XmlConverter();
                        // Put the result back
                        exchange.getOut().setBody(xmlConverter.toSource(RESPONSE));
                    }
                });
            }
        };
    }
    

    @Test
    public void testInvokingServiceFromHttpCompnent() throws Exception {
        // call the service with right post message
        
        String response = template.requestBody(simpleEndpointAddress, REQUEST_MESSAGE, String.class);
        assertTrue("Get a wrong response ", response.startsWith(RESPONSE_MESSAGE_BEGINE));
        assertTrue("Get a wrong response ", response.endsWith(RESPONSE_MESSAGE_END));
        try {
            template.requestBody(simpleEndpointAddress, null, String.class);
            fail("Excpetion to get exception here");
        } catch (Exception ex) {
            // do nothing here
        }
       
        response = template.requestBody(simpleEndpointAddress, REQUEST_MESSAGE, String.class);
        assertTrue("Get a wrong response ", response.startsWith(RESPONSE_MESSAGE_BEGINE));
        assertTrue("Get a wrong response ", response.endsWith(RESPONSE_MESSAGE_END));
    }

    
    protected String getFromEndpointUri() {
        return simpleEndpointURI;
    }

}
