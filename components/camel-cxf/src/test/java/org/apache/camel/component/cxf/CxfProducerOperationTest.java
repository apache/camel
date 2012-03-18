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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.junit.Test;

/**
 * @version 
 */
public class CxfProducerOperationTest extends CxfProducerTest {
    private static final String NAMESPACE = "http://apache.org/hello_world_soap_http";

   
    protected String getSimpleEndpointUri() {
        return "cxf://" + getSimpleServerAddress()
            + "?serviceClass=org.apache.camel.component.cxf.HelloService" 
            + "&defaultOperationName=" + ECHO_OPERATION;
    }

    protected String getJaxwsEndpointUri() {
        return "cxf://" + getJaxWsServerAddress()
            + "?serviceClass=org.apache.hello_world_soap_http.Greeter"
            + "&defaultOperationName=" + GREET_ME_OPERATION
            + "&defaultOperationNamespace=" + NAMESPACE;
    }

    protected Exchange sendSimpleMessage() {
        return sendSimpleMessage(getSimpleEndpointUri());
    }

    private Exchange sendSimpleMessage(String endpointUri) {
        Exchange exchange = template.send(endpointUri, new Processor() {
            public void process(final Exchange exchange) {
                final List<String> params = new ArrayList<String>();
                params.add(TEST_MESSAGE);
                exchange.getIn().setBody(params);
                exchange.getIn().setHeader(Exchange.FILE_NAME, "testFile");
                exchange.getIn().setHeader("requestObject", new DefaultCxfBinding());
            }
        });
        return exchange;

    }
    
    protected Exchange sendJaxWsMessage() {
        Exchange exchange = template.send(getJaxwsEndpointUri(), new Processor() {
            public void process(final Exchange exchange) {
                final List<String> params = new ArrayList<String>();
                params.add(TEST_MESSAGE);
                exchange.getIn().setBody(params);
                exchange.getIn().setHeader(Exchange.FILE_NAME, "testFile");
            }
        });
        return exchange;
    }
    
    @Test
    public void testSendingComplexParameter() throws Exception {
        Exchange exchange = template.send(getSimpleEndpointUri(), new Processor() {
            public void process(final Exchange exchange) {
                // we need to override the operation name first                
                final List<String> para1 = new ArrayList<String>();
                para1.add("para1");
                final List<String> para2 = new ArrayList<String>();
                para2.add("para2");                
                List<List<String>> parameters = new ArrayList<List<String>>();
                parameters.add(para1);
                parameters.add(para2);
                // The object array version is working too
                // Object[] parameters = new Object[] {para1, para2};
                exchange.getIn().setBody(parameters);
                exchange.getIn().setHeader(CxfConstants.OPERATION_NAME, "complexParameters");
            }
        });
        
        if (exchange.getException() != null) {
            throw exchange.getException();
        }
        
        assertEquals("Get a wrong response.", "param:para1para2", exchange.getOut().getBody(String.class));
        
    }
}
