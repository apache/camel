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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.apache.cxf.binding.soap.SoapFault;
import org.apache.cxf.interceptor.Fault;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CxfSpringCustomizedExceptionTest extends CamelTestSupport  {
    private static final String EXCEPTION_MESSAGE = "This is an exception test message";
    private static final String DETAIL_TEXT = "This is a detail text node";
    private static final SoapFault SOAP_FAULT;
    private AbstractXmlApplicationContext applicationContext;
   
    static {
        // START SNIPPET: FaultDefine
        SOAP_FAULT = new SoapFault(EXCEPTION_MESSAGE, Fault.FAULT_CODE_CLIENT);
        Element detail = SOAP_FAULT.getOrCreateDetail();
        Document doc = detail.getOwnerDocument();
        Text tn = doc.createTextNode(DETAIL_TEXT);
        detail.appendChild(tn);
        // END SNIPPET: FaultDefine
    }    
   
    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    @Override
    @Before
    public void setUp() throws Exception {
        CXFTestSupport.getPort1();
        applicationContext = createApplicationContext();
        super.setUp();
        assertNotNull("Should have created a valid spring context", applicationContext);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        IOHelper.close(applicationContext);
        super.tearDown();
    }
    
    @Test
    public void testInvokingServiceFromCamel() throws Exception {
        try {
            template.sendBodyAndHeader("direct:start", ExchangePattern.InOut, "hello world", CxfConstants.OPERATION_NAME, "echo");
            fail("Should have thrown an exception");
        } catch (Exception ex) {
            Throwable result = ex.getCause();
            assertTrue("Exception is not instance of SoapFault", result instanceof SoapFault);
            assertEquals("Expect to get right detail message", DETAIL_TEXT, ((SoapFault)result).getDetail().getTextContent());
            assertEquals("Expect to get right fault-code", "{http://schemas.xmlsoap.org/soap/envelope/}Client", ((SoapFault)result).getFaultCode().toString());
        }
        
    }
    
    @Override
    protected CamelContext createCamelContext() throws Exception {
        return SpringCamelContext.springCamelContext(applicationContext, true);
    }


    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/CxfCustomizedExceptionContext.xml");
    }
    
    public static class SOAPFaultProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            exchange.getMessage().setBody(SOAP_FAULT);
        }
    }


}
