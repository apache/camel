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

import java.util.ArrayList;
import java.util.List;

import javax.xml.ws.Endpoint;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.hello_world_soap_http.BadRecordLitFault;
import org.apache.hello_world_soap_http.GreeterImpl;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class CxfProducerSoapFaultTest extends Assert {
    private static final String JAXWS_SERVER_ADDRESS = "http://localhost:" + CXFTestSupport.getPort1() + "/CxfProducerSoapFaultTest/test";
    private static final String JAXWS_ENDPOINT_URI = "cxf://" + JAXWS_SERVER_ADDRESS + "?serviceClass=org.apache.hello_world_soap_http.Greeter";
    
    protected CamelContext camelContext;
    protected ProducerTemplate template;

    @BeforeClass
    public static void startService() throws Exception {
        GreeterImpl greeterImpl = new GreeterImpl();
        Endpoint.publish(JAXWS_SERVER_ADDRESS, greeterImpl);
    }

    @Before
    public void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.start();
        template = camelContext.createProducerTemplate();
    }

    @After
    public void tearDown() throws Exception {
        template.stop();
        camelContext.stop();
    }
    
    @Test
    public void testAsyncSoapFault() throws Exception {
        invokeSoapFault(false);
    }
    
    @Test
    public void testSyncSoapFault() throws Exception {
        invokeSoapFault(true);
    }
        
    private void invokeSoapFault(boolean sync) throws Exception {
        String cxfEndpointURI = JAXWS_ENDPOINT_URI;
        if (sync) {
            cxfEndpointURI = cxfEndpointURI + "&synchronous=true";
        }
        Exchange exchange = sendJaxWsMessage(cxfEndpointURI, "BadRecordLitFault", "testDocLitFault");
        Exception exception = exchange.getException();
        // assert we got the exception first
        assertNotNull("except to get the exception", exception);
        assertTrue("Get a wrong soap fault", exception instanceof BadRecordLitFault);
        // check out the message header which is copied from in message
        String fileName = exchange.getOut().getHeader(Exchange.FILE_NAME, String.class);
        assertEquals("Should get the file name from out message header", "testFile", fileName);
    }
    
    private Exchange sendJaxWsMessage(final String uri, final String message, final String operation) {
        Exchange exchange = template.request(uri, new Processor() {
            public void process(final Exchange exchange) {
                final List<String> params = new ArrayList<String>();
                params.add(message);
                exchange.getIn().setBody(params);
                exchange.getIn().setHeader(CxfConstants.OPERATION_NAME, operation);
                exchange.getIn().setHeader(Exchange.FILE_NAME, "testFile");
            }
        });
        return exchange;
    }

}
