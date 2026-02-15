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

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.test.spring.junit6.CamelSpringTestSupport;
import org.apache.camel.util.IOHelper;
import org.apache.cxf.binding.soap.SoapFault;
import org.apache.cxf.interceptor.Fault;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CxfSpringCustomizedExceptionTest extends CamelSpringTestSupport {
    private static final String EXCEPTION_MESSAGE = "This is an exception test message";
    private static final String DETAIL_TEXT = "This is a detail text node";
    private static final SoapFault SOAP_FAULT;

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
    public void setupResources() {
        CXFTestSupport.getPort1();
    }

    @Override
    public void cleanupResources() {
        IOHelper.close(applicationContext);
    }

    @Test
    public void testInvokingServiceFromCamel() throws Exception {
        Exception ex = assertThrows(Exception.class,
                () -> template.sendBodyAndHeader("direct:start", ExchangePattern.InOut, "hello world",
                        CxfConstants.OPERATION_NAME,
                        "echo"));
        Throwable result = ex.getCause();
        assertTrue(result instanceof SoapFault, "Exception is not instance of SoapFault");
        assertEquals(DETAIL_TEXT, ((SoapFault) result).getDetail().getTextContent(), "Expect to get right detail message");
        assertEquals("{http://schemas.xmlsoap.org/soap/envelope/}Client", ((SoapFault) result).getFaultCode().toString(),
                "Expect to get right fault-code");
    }

    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/CxfCustomizedExceptionContext.xml");
    }

    public static class SOAPFaultProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            exchange.getMessage().setBody(SOAP_FAULT);
        }
    }

}
